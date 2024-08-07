/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.client.program;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

import akka.pattern.Patterns;
import akka.util.Timeout;
import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.JobSubmissionResult;
import org.apache.flink.configuration.IllegalConfigurationException;
import org.apache.flink.runtime.akka.AkkaUtils;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.Plan;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.ExecutionEnvironmentFactory;
import org.apache.flink.optimizer.CompilerException;
import org.apache.flink.optimizer.DataStatistics;
import org.apache.flink.optimizer.Optimizer;
import org.apache.flink.optimizer.costs.DefaultCostEstimator;
import org.apache.flink.optimizer.plan.FlinkPlan;
import org.apache.flink.optimizer.plan.OptimizedPlan;
import org.apache.flink.optimizer.plan.StreamingPlan;
import org.apache.flink.optimizer.plandump.PlanJSONDumpGenerator;
import org.apache.flink.optimizer.plantranslate.JobGraphGenerator;
import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.Path;
import org.apache.flink.runtime.client.JobClient;
import org.apache.flink.runtime.client.JobExecutionException;
import org.apache.flink.runtime.client.SerializedJobExecutionResult;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobmanager.JobManager;
import org.apache.flink.runtime.messages.JobManagerMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import com.google.common.base.Preconditions;

/**
 * Encapsulates the functionality necessary to submit a program to a remote cluster.
 */
public class Client {
	
	private static final Logger LOG = LoggerFactory.getLogger(Client.class);

	/** The configuration to use for the client (optimizer, timeouts, ...) */
	private final Configuration configuration;

	/** The address of the JobManager to send the program to */
	private final InetSocketAddress jobManagerAddress;

	/** The optimizer used in the optimization of batch programs */
	private final Optimizer compiler;

	/** The class loader to use for classes from the user program (e.g., functions and data types) */
	private final ClassLoader userCodeClassLoader;
	
	/** Flag indicating whether to sysout print execution updates */
	private boolean printStatusDuringExecution = true;

	/**
	 * If != -1, this field specifies the total number of available slots on the cluster
	 * connected to the client.
	 */
	private int maxSlots = -1;

	/** ID of the last job submitted with this client. */
	private JobID lastJobId = null;
	
	
	// ------------------------------------------------------------------------
	//                            Construction
	// ------------------------------------------------------------------------
	
	/**
	 * Creates a new instance of the class that submits the jobs to a job-manager.
	 * at the given address using the default port.
	 * 
	 * @param jobManagerAddress Address and port of the job-manager.
	 */
	public Client(InetSocketAddress jobManagerAddress, Configuration config, 
							ClassLoader userCodeClassLoader, int maxSlots) throws UnknownHostException
	{
		Preconditions.checkNotNull(jobManagerAddress, "JobManager address is null");
		Preconditions.checkNotNull(config, "Configuration is null");
		Preconditions.checkNotNull(userCodeClassLoader, "User code ClassLoader is null");
		
		this.configuration = config;
		
		if (jobManagerAddress.isUnresolved()) {
			// address is unresolved, resolve it
			String host = jobManagerAddress.getHostName();
			if (host == null) {
				throw new IllegalArgumentException("Host in jobManagerAddress is null");
			}
			
			try {
				InetAddress address = InetAddress.getByName(host);
				this.jobManagerAddress = new InetSocketAddress(address, jobManagerAddress.getPort());
			}
			catch (UnknownHostException e) {
				throw new UnknownHostException("Cannot resolve JobManager host name '" + host + "'.");
			}
		}
		else {
			// address is already resolved, use it as is
			this.jobManagerAddress = jobManagerAddress;
		}
		
		this.compiler = new Optimizer(new DataStatistics(), new DefaultCostEstimator(), configuration);
		this.userCodeClassLoader = userCodeClassLoader;
		this.maxSlots = maxSlots;
	}

	/**
	 * Creates a instance that submits the programs to the JobManager defined in the
	 * configuration. This method will try to resolve the JobManager hostname and throw an exception
	 * if that is not possible.
	 * 
	 * @param config The config used to obtain the job-manager's address.
	 * @param userCodeClassLoader The class loader to use for loading user code classes.   
	 */
	public Client(Configuration config, ClassLoader userCodeClassLoader) throws UnknownHostException {
		Preconditions.checkNotNull(config, "Configuration is null");
		Preconditions.checkNotNull(userCodeClassLoader, "User code ClassLoader is null");
		
		this.configuration = config;
		this.userCodeClassLoader = userCodeClassLoader;
		
		// instantiate the address to the job manager
		final String address = config.getString(ConfigConstants.JOB_MANAGER_IPC_ADDRESS_KEY, null);
		if (address == null) {
			throw new IllegalConfigurationException(
					"Cannot find address to job manager's RPC service in the global configuration.");
		}
		
		final int port = config.getInteger(ConfigConstants.JOB_MANAGER_IPC_PORT_KEY,
														ConfigConstants.DEFAULT_JOB_MANAGER_IPC_PORT);
		if (port < 0) {
			throw new IllegalConfigurationException("Cannot find port to job manager's RPC service in the global configuration.");
		}
		
		try {
			InetAddress inetAddress = InetAddress.getByName(address);
			this.jobManagerAddress = new InetSocketAddress(inetAddress, port);
		}
		catch (UnknownHostException e) {
			throw new UnknownHostException("Cannot resolve the JobManager hostname '" + address
					+ "' specified in the configuration");
		}

		this.compiler = new Optimizer(new DataStatistics(), new DefaultCostEstimator(), configuration);
	}

	/**
	 * Configures whether the client should print progress updates during the execution to {@code System.out}.
	 * All updates are logged via the SLF4J loggers regardless of this setting.
	 * 
	 * @param print True to print updates to standard out during execution, false to not print them.
	 */
	public void setPrintStatusDuringExecution(boolean print) {
		this.printStatusDuringExecution = print;
	}

	/**
	 * @return -1 if unknown. The maximum number of available processing slots at the Flink cluster
	 * connected to this client.
	 */
	public int getMaxSlots() {
		return this.maxSlots;
	}
	
	// ------------------------------------------------------------------------
	//                      Compilation and Submission
	// ------------------------------------------------------------------------
	
	public String getOptimizedPlanAsJson(PackagedProgram prog, int parallelism) throws CompilerException, ProgramInvocationException {
		PlanJSONDumpGenerator jsonGen = new PlanJSONDumpGenerator();
		return jsonGen.getOptimizerPlanAsJSON((OptimizedPlan) getOptimizedPlan(prog, parallelism));
	}
	
	public FlinkPlan getOptimizedPlan(PackagedProgram prog, int parallelism) throws CompilerException, ProgramInvocationException {
		Thread.currentThread().setContextClassLoader(prog.getUserCodeClassLoader());
		if (prog.isUsingProgramEntryPoint()) {
			return getOptimizedPlan(prog.getPlanWithJars(), parallelism);
		}
		else if (prog.isUsingInteractiveMode()) {
			// temporary hack to support the optimizer plan preview
			OptimizerPlanEnvironment env = new OptimizerPlanEnvironment(this.compiler);
			if (parallelism > 0) {
				env.setParallelism(parallelism);
			}
			env.setAsContext();
			
			// temporarily write syserr and sysout to a byte array.
			PrintStream originalOut = System.out;
			PrintStream originalErr = System.err;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			System.setOut(new PrintStream(baos));
			ByteArrayOutputStream baes = new ByteArrayOutputStream();
			System.setErr(new PrintStream(baes));
			try {
				ContextEnvironment.enableLocalExecution(false);
				prog.invokeInteractiveModeForExecution();
			}
			catch (ProgramInvocationException e) {
				throw e;
			}
			catch (Throwable t) {
				// the invocation gets aborted with the preview plan
				if (env.optimizerPlan != null) {
					return env.optimizerPlan;
				} else {
					throw new ProgramInvocationException("The program caused an error: ", t);
				}
			}
			finally {
				ContextEnvironment.enableLocalExecution(true);
				System.setOut(originalOut);
				System.setErr(originalErr);
				System.err.println(baes);
				System.out.println(baos);
			}
			
			throw new ProgramInvocationException(
					"The program plan could not be fetched - the program aborted pre-maturely.\n"
					+ "System.err: " + baes.toString() + '\n'
					+ "System.out: " + baos.toString() + '\n');
		}
		else {
			throw new RuntimeException();
		}
	}
	
	public FlinkPlan getOptimizedPlan(Plan p, int parallelism) throws CompilerException {
		if (parallelism > 0 && p.getDefaultParallelism() <= 0) {
			LOG.debug("Changing plan default parallelism from {} to {}",p.getDefaultParallelism(), parallelism);
			p.setDefaultParallelism(parallelism);
		}
		LOG.debug("Set parallelism {}, plan default parallelism {}", parallelism, p.getDefaultParallelism());

		return this.compiler.compile(p);
	}
	
	
	/**
	 * Creates the optimized plan for a given program, using this client's compiler.
	 *  
	 * @param prog The program to be compiled.
	 * @return The compiled and optimized plan, as returned by the compiler.
	 * @throws CompilerException Thrown, if the compiler encounters an illegal situation.
	 * @throws ProgramInvocationException Thrown, if the program could not be instantiated from its jar file.
	 */
	public FlinkPlan getOptimizedPlan(JobWithJars prog, int parallelism) throws CompilerException, ProgramInvocationException {
		return getOptimizedPlan(prog.getPlan(), parallelism);
	}
	
	public JobGraph getJobGraph(PackagedProgram prog, FlinkPlan optPlan) throws ProgramInvocationException {
		return getJobGraph(optPlan, prog.getAllLibraries());
	}
	
	private JobGraph getJobGraph(FlinkPlan optPlan, List<File> jarFiles) {
		JobGraph job;
		if (optPlan instanceof StreamingPlan) {
			job = ((StreamingPlan) optPlan).getJobGraph();
		} else {
			JobGraphGenerator gen = new JobGraphGenerator();
			job = gen.compileJobGraph((OptimizedPlan) optPlan);
		}

		for (File jar : jarFiles) {
			job.addJar(new Path(jar.getAbsolutePath()));
		}

		return job;
	}

	public JobSubmissionResult run(final PackagedProgram prog, int parallelism, boolean wait) throws ProgramInvocationException {
		Thread.currentThread().setContextClassLoader(prog.getUserCodeClassLoader());
		if (prog.isUsingProgramEntryPoint()) {
			return run(prog.getPlanWithJars(), parallelism, wait);
		}
		else if (prog.isUsingInteractiveMode()) {
			LOG.info("Starting program in interactive mode");
			ContextEnvironment.setAsContext(this, prog.getAllLibraries(), prog.getUserCodeClassLoader(), parallelism, wait);
			ContextEnvironment.enableLocalExecution(false);

			// invoke here
			try {
				prog.invokeInteractiveModeForExecution();
			}
			finally {
				ContextEnvironment.enableLocalExecution(true);
			}

			// Job id has been set in the Client passed to the ContextEnvironment
			return new JobSubmissionResult(lastJobId);
		}
		else {
			throw new RuntimeException();
		}
	}
	
	public JobSubmissionResult run(PackagedProgram prog, OptimizedPlan optimizedPlan, boolean wait) throws ProgramInvocationException {
		return run(optimizedPlan, prog.getAllLibraries(), wait);

	}
	
	/**
	 * Runs a program on Flink cluster whose job-manager is configured in this client's configuration.
	 * This method involves all steps, from compiling, job-graph generation to submission.
	 * 
	 * @param prog The program to be executed.
	 * @param parallelism The default parallelism to use when running the program. The default parallelism is used
	 *                    when the program does not set a parallelism by itself.
	 * @param wait A flag that indicates whether this function call should block until the program execution is done.
	 * @throws CompilerException Thrown, if the compiler encounters an illegal situation.
	 * @throws ProgramInvocationException Thrown, if the program could not be instantiated from its jar file,
	 *                                    or if the submission failed. That might be either due to an I/O problem,
	 *                                    i.e. the job-manager is unreachable, or due to the fact that the
	 *                                    parallel execution failed.
	 */
	public JobSubmissionResult run(JobWithJars prog, int parallelism, boolean wait) throws CompilerException, ProgramInvocationException {
		return run((OptimizedPlan) getOptimizedPlan(prog, parallelism), prog.getJarFiles(), wait);
	}
	

	public JobSubmissionResult run(OptimizedPlan compiledPlan, List<File> libraries, boolean wait) throws ProgramInvocationException {
		JobGraph job = getJobGraph(compiledPlan, libraries);
		this.lastJobId = job.getJobID();
		return run(job, wait);
	}

	public JobSubmissionResult run(JobGraph jobGraph, boolean wait) throws ProgramInvocationException {
		this.lastJobId = jobGraph.getJobID();
		
		LOG.info("JobManager actor system address is " + jobManagerAddress);
		
		LOG.info("Starting client actor system");
		final ActorSystem actorSystem;
		try {
			actorSystem = JobClient.startJobClientActorSystem(configuration);
		}
		catch (Exception e) {
			throw new ProgramInvocationException("Could start client actor system.", e);
		}

		LOG.info("Looking up JobManager");
		ActorRef jobManager;
		try {
			jobManager = JobManager.getJobManagerRemoteReference(jobManagerAddress, actorSystem, configuration);
		}
		catch (IOException e) {
			throw new ProgramInvocationException("Failed to resolve JobManager", e);
		}
		LOG.info("JobManager runs at " + jobManager.path());

		FiniteDuration timeout = AkkaUtils.getTimeout(configuration);
		LOG.info("Communication between client and JobManager will have a timeout of " + timeout);

		LOG.info("Checking and uploading JAR files");
		try {
			JobClient.uploadJarFiles(jobGraph, jobManager, timeout);
		}
		catch (IOException e) {
			throw new ProgramInvocationException("Could not upload the program's JAR files to the JobManager.", e);
		}

		try{
			if (wait) {
				SerializedJobExecutionResult result = JobClient.submitJobAndWait(actorSystem, 
						jobManager, jobGraph, timeout, printStatusDuringExecution);
				try {
					return result.toJobExecutionResult(this.userCodeClassLoader);
				}
				catch (Exception e) {
					throw new ProgramInvocationException(
							"Failed to deserialize the accumulator result after the job execution", e);
				}
			}
			else {
				JobClient.submitJobDetached(jobManager, jobGraph, timeout);
				// return a dummy execution result with the JobId
				return new JobSubmissionResult(jobGraph.getJobID());
			}
		}
		catch (JobExecutionException e) {
			throw new ProgramInvocationException("The program execution failed: " + e.getMessage(), e);
		}
		catch (Exception e) {
			throw new ProgramInvocationException("Exception during program execution.", e);
		}
		finally {
			actorSystem.shutdown();
			actorSystem.awaitTermination();
		}
	}

	/**
	 * Cancels a job identified by the job id.
	 * @param jobId the job id
	 * @throws Exception In case an error occurred.
	 */
	public void cancel(JobID jobId) throws Exception {
		final FiniteDuration timeout = AkkaUtils.getTimeout(configuration);

		ActorSystem actorSystem;
		try {
			actorSystem = JobClient.startJobClientActorSystem(configuration);
		} catch (Exception e) {
			throw new ProgramInvocationException("Could start client actor system.", e);
		}

		ActorRef jobManager;
		try {
			jobManager = JobManager.getJobManagerRemoteReference(jobManagerAddress, actorSystem, timeout);
		} catch (Exception e) {
			LOG.error("Error in getting the remote reference for the job manager", e);
			throw new ProgramInvocationException("Failed to resolve JobManager", e);
		}

		Future<Object> response = Patterns.ask(jobManager, new JobManagerMessages.CancelJob(jobId), new Timeout(timeout));
		Object result = Await.result(response, timeout);

		if (result instanceof JobManagerMessages.CancellationSuccess) {
			LOG.debug("Job cancellation with ID " + jobId + " succeeded.");
		} else if (result instanceof JobManagerMessages.CancellationFailure) {
			Throwable t = ((JobManagerMessages.CancellationFailure) result).cause();
			LOG.debug("Job cancellation with ID " + jobId + " failed.", t);
			throw new Exception("Failed to cancel the job because of \n" + t.getMessage());
		} else {
			throw new Exception("Unknown message received while cancelling.");
		}
	}


	// --------------------------------------------------------------------------------------------
	
	public static final class OptimizerPlanEnvironment extends ExecutionEnvironment {
		
		private final Optimizer compiler;
		
		private FlinkPlan optimizerPlan;
		
		
		private OptimizerPlanEnvironment(Optimizer compiler) {
			this.compiler = compiler;
		}
		
		@Override
		public JobExecutionResult execute(String jobName) throws Exception {
			Plan plan = createProgramPlan(jobName);
			this.optimizerPlan = compiler.compile(plan);
			
			// do not go on with anything now!
			throw new ProgramAbortException();
		}

		@Override
		public String getExecutionPlan() throws Exception {
			Plan plan = createProgramPlan(null, false);
			this.optimizerPlan = compiler.compile(plan);
			
			// do not go on with anything now!
			throw new ProgramAbortException();
		}
		
		private void setAsContext() {
			ExecutionEnvironmentFactory factory = new ExecutionEnvironmentFactory() {
				
				@Override
				public ExecutionEnvironment createExecutionEnvironment() {
					return OptimizerPlanEnvironment.this;
				}
			};
			initializeContextEnvironment(factory);
		}
		
		public void setPlan(FlinkPlan plan){
			this.optimizerPlan = plan;
		}
	}

	// --------------------------------------------------------------------------------------------

	/**
	 * A special exception used to abort programs when the caller is only interested in the
	 * program plan, rather than in the full execution.
	 */
	public static final class ProgramAbortException extends Error {
		private static final long serialVersionUID = 1L;
	}
}
