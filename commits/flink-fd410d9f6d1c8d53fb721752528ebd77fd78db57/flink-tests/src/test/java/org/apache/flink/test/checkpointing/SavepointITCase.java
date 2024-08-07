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

package org.apache.flink.test.checkpointing;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.io.FileUtils;
import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.akka.AkkaUtils;
import org.apache.flink.runtime.checkpoint.SubtaskState;
import org.apache.flink.runtime.checkpoint.TaskState;
import org.apache.flink.runtime.checkpoint.savepoint.SavepointV1;
import org.apache.flink.runtime.deployment.TaskDeploymentDescriptor;
import org.apache.flink.runtime.execution.SuppressRestartsException;
import org.apache.flink.runtime.instance.ActorGateway;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobVertex;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.messages.JobManagerMessages.CancelJob;
import org.apache.flink.runtime.messages.JobManagerMessages.DisposeSavepoint;
import org.apache.flink.runtime.messages.JobManagerMessages.TriggerSavepoint;
import org.apache.flink.runtime.messages.JobManagerMessages.TriggerSavepointSuccess;
import org.apache.flink.runtime.state.ChainedStateHandle;
import org.apache.flink.runtime.state.CheckpointListener;
import org.apache.flink.runtime.state.StreamStateHandle;
import org.apache.flink.runtime.state.filesystem.FileStateHandle;
import org.apache.flink.runtime.state.filesystem.FsStateBackend;
import org.apache.flink.runtime.state.filesystem.FsStateBackendFactory;
import org.apache.flink.runtime.testingUtils.TestingCluster;
import org.apache.flink.runtime.testingUtils.TestingJobManagerMessages.RequestSavepoint;
import org.apache.flink.runtime.testingUtils.TestingJobManagerMessages.ResponseSavepoint;
import org.apache.flink.runtime.testingUtils.TestingTaskManagerMessages;
import org.apache.flink.runtime.testingUtils.TestingTaskManagerMessages.ResponseSubmitTaskListener;
import org.apache.flink.runtime.testutils.CommonTestUtils;
import org.apache.flink.streaming.api.checkpoint.Checkpointed;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.DiscardingSink;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.testutils.junit.RetryRule;
import org.apache.flink.util.TestLogger;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Deadline;
import scala.concurrent.duration.FiniteDuration;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.apache.flink.runtime.messages.JobManagerMessages.getDisposeSavepointSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Integration test for triggering and resuming from savepoints.
 */
public class SavepointITCase extends TestLogger {

	private static final Logger LOG = LoggerFactory.getLogger(SavepointITCase.class);

	@Rule
	public RetryRule retryRule = new RetryRule();

	/**
	 * Tests that it is possible to submit a job, trigger a savepoint, and
	 * later restart the job on a new cluster. The savepoint is written to
	 * a file.
	 *
	 * <ol>
	 * <li>Submit job, wait for some checkpoints to complete</li>
	 * <li>Trigger savepoint and verify that savepoint has been created</li>
	 * <li>Shut down the cluster, re-submit the job from the savepoint, and
	 * verify that the initial state has been reset</li>
	 * <li>Cancel job, dispose the savepoint, and verify that everything
	 * has been cleaned up</li>
	 * </ol>
	 */
	@Test
	public void testTriggerSavepointAndResume() throws Exception {
		// Config
		int numTaskManagers = 2;
		int numSlotsPerTaskManager = 2;
		int parallelism = numTaskManagers * numSlotsPerTaskManager;

		// Test deadline
		final Deadline deadline = new FiniteDuration(5, TimeUnit.MINUTES).fromNow();

		// The number of checkpoints to complete before triggering the savepoint
		final int numberOfCompletedCheckpoints = 10;

		// Temporary directory for file state backend
		final File tmpDir = CommonTestUtils.createTempDirectory();

		LOG.info("Created temporary directory: " + tmpDir + ".");

		TestingCluster flink = null;

		try {
			// Create a test actor system
			ActorSystem testActorSystem = AkkaUtils.createDefaultActorSystem();

			// Flink configuration
			final Configuration config = new Configuration();
			config.setInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, numTaskManagers);
			config.setInteger(ConfigConstants.TASK_MANAGER_NUM_TASK_SLOTS, numSlotsPerTaskManager);

			final File checkpointDir = new File(tmpDir, "checkpoints");
			final File savepointDir = new File(tmpDir, "savepoints");

			if (!checkpointDir.mkdir() || !savepointDir.mkdirs()) {
				fail("Test setup failed: failed to create temporary directories.");
			}

			LOG.info("Created temporary checkpoint directory: " + checkpointDir + ".");
			LOG.info("Created temporary savepoint directory: " + savepointDir + ".");

			config.setString(ConfigConstants.STATE_BACKEND, "filesystem");
			config.setString(FsStateBackendFactory.CHECKPOINT_DIRECTORY_URI_CONF_KEY,
					checkpointDir.toURI().toString());
			config.setString(ConfigConstants.SAVEPOINT_DIRECTORY_KEY,
					savepointDir.toURI().toString());

			LOG.info("Flink configuration: " + config + ".");

			// Start Flink
			flink = new TestingCluster(config);
			LOG.info("Starting Flink cluster.");
			flink.start();

			// Retrieve the job manager
			LOG.info("Retrieving JobManager.");
			ActorGateway jobManager = Await.result(
					flink.leaderGateway().future(),
					deadline.timeLeft());
			LOG.info("JobManager: " + jobManager + ".");

			// Submit the job
			final JobGraph jobGraph = createJobGraph(parallelism, 0, 1000, 1000);
			final JobID jobId = jobGraph.getJobID();

			// Wait for the source to be notified about the expected number
			// of completed checkpoints
			InfiniteTestSource.CheckpointCompleteLatch = new CountDownLatch(
					numberOfCompletedCheckpoints);

			LOG.info("Submitting job " + jobGraph.getJobID() + " in detached mode.");

			flink.submitJobDetached(jobGraph);

			LOG.info("Waiting for " + numberOfCompletedCheckpoints +
					" checkpoint complete notifications.");

			// Wait...
			InfiniteTestSource.CheckpointCompleteLatch.await();

			LOG.info("Received all " + numberOfCompletedCheckpoints +
					" checkpoint complete notifications.");

			// ...and then trigger the savepoint
			LOG.info("Triggering a savepoint.");

			Future<Object> savepointPathFuture = jobManager.ask(
					new TriggerSavepoint(jobId, Option.<String>empty()), deadline.timeLeft());

			final String savepointPath = ((TriggerSavepointSuccess) Await
					.result(savepointPathFuture, deadline.timeLeft())).savepointPath();
			LOG.info("Retrieved savepoint path: " + savepointPath + ".");

			// Only one savepoint should exist
			File[] files = savepointDir.listFiles();
			if (files != null) {
				assertEquals("Savepoint not created in expected directory", 1, files.length);
			} else {
				fail("Savepoint not created in expected directory");
			}

			// Retrieve the savepoint from the testing job manager
			LOG.info("Requesting the savepoint.");
			Future<Object> savepointFuture = jobManager.ask(
					new RequestSavepoint(savepointPath),
					deadline.timeLeft());

			SavepointV1 savepoint = (SavepointV1) ((ResponseSavepoint) Await.result(
					savepointFuture, deadline.timeLeft())).savepoint();
			LOG.info("Retrieved savepoint: " + savepointPath + ".");

			// Shut down the Flink cluster (thereby canceling the job)
			LOG.info("Shutting down Flink cluster.");
			flink.shutdown();

			// - Verification START -------------------------------------------

			// Only one checkpoint of the savepoint should exist
			String errMsg = "Checkpoints directory not cleaned up properly.";
			files = checkpointDir.listFiles();
			if (files != null) {
				assertEquals(errMsg, 1, files.length);
			}
			else {
				fail(errMsg);
			}

			// Only one savepoint should exist
			errMsg = "Savepoints directory cleaned up.";
			files = savepointDir.listFiles();
			if (files != null) {
				assertEquals(errMsg, 1, files.length);
			}
			else {
				fail(errMsg);
			}

			// - Verification END ---------------------------------------------

			// Restart the cluster
			LOG.info("Restarting Flink cluster.");
			flink.start();

			// Retrieve the job manager
			LOG.info("Retrieving JobManager.");
			jobManager = Await.result(
					flink.leaderGateway().future(),
					deadline.timeLeft());
			LOG.info("JobManager: " + jobManager + ".");

			final Throwable[] error = new Throwable[1];
			final TestingCluster finalFlink = flink;
			final Multimap<JobVertexID, TaskDeploymentDescriptor> tdds = HashMultimap.create();
			new JavaTestKit(testActorSystem) {{

				new Within(deadline.timeLeft()) {
					@Override
					protected void run() {
						try {
							// Register to all submit task messages for job
							for (ActorRef taskManager : finalFlink.getTaskManagersAsJava()) {
								taskManager.tell(new TestingTaskManagerMessages
										.RegisterSubmitTaskListener(jobId), getTestActor());
							}

							// Set the savepoint path
							jobGraph.setSavepointPath(savepointPath);

							LOG.info("Resubmitting job " + jobGraph.getJobID() + " with " +
									"savepoint path " + savepointPath + " in detached mode.");

							finalFlink.submitJobDetached(jobGraph);

							int numTasks = 0;
							for (JobVertex jobVertex : jobGraph.getVertices()) {
								numTasks += jobVertex.getParallelism();
							}

							// Gather the task deployment descriptors
							LOG.info("Gathering " + numTasks + " submitted " +
									"TaskDeploymentDescriptor instances.");

							for (int i = 0; i < numTasks; i++) {
								ResponseSubmitTaskListener resp = (ResponseSubmitTaskListener)
										expectMsgAnyClassOf(getRemainingTime(),
												ResponseSubmitTaskListener.class);

								TaskDeploymentDescriptor tdd = resp.tdd();

								LOG.info("Received: " + tdd.toString() + ".");

								tdds.put(tdd.getVertexID(), tdd);
							}
						}
						catch (Throwable t) {
							error[0] = t;
						}
					}
				};
			}};

			// - Verification START -------------------------------------------

			errMsg = "Error during gathering of TaskDeploymentDescriptors";
			assertNull(errMsg, error[0]);

			// Verify that all tasks, which are part of the savepoint
			// have a matching task deployment descriptor.
			for (TaskState taskState : savepoint.getTaskStates()) {
				Collection<TaskDeploymentDescriptor> taskTdds = tdds.get(taskState.getJobVertexID());

				errMsg = "Missing task for savepoint state for operator "
						+ taskState.getJobVertexID() + ".";
				assertTrue(errMsg, taskTdds.size() > 0);

				assertEquals(taskState.getNumberCollectedStates(), taskTdds.size());

				for (TaskDeploymentDescriptor tdd : taskTdds) {
					SubtaskState subtaskState = taskState.getState(tdd.getIndexInSubtaskGroup());

					assertNotNull(subtaskState);
					errMsg = "Initial operator state mismatch.";
					assertEquals(errMsg, subtaskState.getChainedStateHandle(), tdd.getOperatorState());
				}
			}

			// - Verification END ---------------------------------------------

			LOG.info("Cancelling job " + jobId + ".");
			jobManager.tell(new CancelJob(jobId));

			LOG.info("Disposing savepoint " + savepointPath + ".");
			Future<Object> disposeFuture = jobManager.ask(
					new DisposeSavepoint(savepointPath),
					deadline.timeLeft());

			errMsg = "Failed to dispose savepoint " + savepointPath + ".";
			Object resp = Await.result(disposeFuture, deadline.timeLeft());
			assertTrue(errMsg, resp.getClass() ==
					getDisposeSavepointSuccess().getClass());

			// - Verification START -------------------------------------------

			// The checkpoint files
			List<File> checkpointFiles = new ArrayList<>();

			for (TaskState stateForTaskGroup : savepoint.getTaskStates()) {
				for (SubtaskState subtaskState : stateForTaskGroup.getStates()) {
					ChainedStateHandle<StreamStateHandle> streamTaskState = subtaskState.getChainedStateHandle();

					for (int i = 0; i < streamTaskState.getLength(); i++) {
						if (streamTaskState.get(i) != null) {
							FileStateHandle fileStateHandle = (FileStateHandle) streamTaskState.get(i);
							checkpointFiles.add(new File(fileStateHandle.getFilePath().toUri()));
						}
					}
				}
			}

			// The checkpoint of the savepoint should have been discarded
			for (File f : checkpointFiles) {
				errMsg = "Checkpoint file " + f + " not cleaned up properly.";
				assertFalse(errMsg, f.exists());
			}

			if (checkpointFiles.size() > 0) {
				File parent = checkpointFiles.get(0).getParentFile();
				errMsg = "Checkpoint parent directory " + parent + " not cleaned up properly.";
				assertFalse(errMsg, parent.exists());
			}

			// All savepoints should have been cleaned up
			errMsg = "Savepoints directory not cleaned up properly: " +
					Arrays.toString(savepointDir.listFiles()) + ".";
			assertEquals(errMsg, 0, savepointDir.listFiles().length);

			// - Verification END ---------------------------------------------
		}
		finally {
			if (flink != null) {
				flink.shutdown();
			}

			if (tmpDir != null) {
				FileUtils.deleteDirectory(tmpDir);
			}
		}
	}

	@Test
	public void testSubmitWithUnknownSavepointPath() throws Exception {
		// Config
		int numTaskManagers = 1;
		int numSlotsPerTaskManager = 1;
		int parallelism = numTaskManagers * numSlotsPerTaskManager;

		// Test deadline
		final Deadline deadline = new FiniteDuration(5, TimeUnit.MINUTES).fromNow();

		final File tmpDir = CommonTestUtils.createTempDirectory();
		final File savepointDir = new File(tmpDir, "savepoints");

		TestingCluster flink = null;

		try {
			// Flink configuration
			final Configuration config = new Configuration();
			config.setInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, numTaskManagers);
			config.setInteger(ConfigConstants.TASK_MANAGER_NUM_TASK_SLOTS, numSlotsPerTaskManager);
			config.setString(ConfigConstants.SAVEPOINT_DIRECTORY_KEY,
					savepointDir.toURI().toString());

			LOG.info("Flink configuration: " + config + ".");

			// Start Flink
			flink = new TestingCluster(config);
			LOG.info("Starting Flink cluster.");
			flink.start();

			// Retrieve the job manager
			LOG.info("Retrieving JobManager.");
			ActorGateway jobManager = Await.result(
					flink.leaderGateway().future(),
					deadline.timeLeft());
			LOG.info("JobManager: " + jobManager + ".");

			// High value to ensure timeouts if restarted.
			int numberOfRetries = 1000;
			// Submit the job
			// Long delay to ensure that the test times out if the job
			// manager tries to restart the job.
			final JobGraph jobGraph = createJobGraph(parallelism, numberOfRetries, 3600000, 1000);

			// Set non-existing savepoint path
			jobGraph.setSavepointPath("unknown path");
			assertEquals("unknown path", jobGraph.getSnapshotSettings().getSavepointPath());

			LOG.info("Submitting job " + jobGraph.getJobID() + " in detached mode.");

			try {
				flink.submitJobAndWait(jobGraph, false);
			}
			catch (Exception e) {
				assertEquals(SuppressRestartsException.class, e.getCause().getClass());
				assertEquals(IllegalArgumentException.class, e.getCause().getCause().getClass());
			}
		}
		finally {
			if (flink != null) {
				flink.shutdown();
			}
		}
	}

	// ------------------------------------------------------------------------
	// Test program
	// ------------------------------------------------------------------------

	/**
	 * Creates a streaming JobGraph from the StreamEnvironment.
	 */
	private JobGraph createJobGraph(
			int parallelism,
			int numberOfRetries,
			long restartDelay,
			int checkpointingInterval) {

		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.setParallelism(parallelism);
		env.enableCheckpointing(checkpointingInterval);
		env.disableOperatorChaining();
		env.getConfig().setRestartStrategy(RestartStrategies.fixedDelayRestart(numberOfRetries, restartDelay));
		env.getConfig().disableSysoutLogging();

		DataStream<Integer> stream = env
				.addSource(new InfiniteTestSource())
				.shuffle()
				.map(new StatefulCounter());

		stream.addSink(new DiscardingSink<Integer>());

		return env.getStreamGraph().getJobGraph();
	}

	private static class InfiniteTestSource
			implements SourceFunction<Integer>, CheckpointListener {

		private static final long serialVersionUID = 1L;
		private volatile boolean running = true;

		// Test control
		private static CountDownLatch CheckpointCompleteLatch = new CountDownLatch(1);

		@Override
		public void run(SourceContext<Integer> ctx) throws Exception {
			while (running) {
				ctx.collect(1);
			}
		}

		@Override
		public void cancel() {
			running = false;
		}

		@Override
		public void notifyCheckpointComplete(long checkpointId) throws Exception {
			CheckpointCompleteLatch.countDown();
		}
	}

	private static class StatefulCounter
			extends RichMapFunction<Integer, Integer>
			implements Checkpointed<byte[]> {

		private static final long serialVersionUID = 7317800376639115920L;
		private byte[] data;

		@Override
		public void open(Configuration parameters) throws Exception {
			if (data == null) {
				// We need this to be large, because we want to test with files
				Random rand = new Random(getRuntimeContext().getIndexOfThisSubtask());
				data = new byte[FsStateBackend.DEFAULT_FILE_STATE_THRESHOLD + 1];
				rand.nextBytes(data);
			}
		}

		@Override
		public Integer map(Integer value) throws Exception {
			for (int i = 0; i < data.length; i++) {
				data[i] += 1;
			}
			return value;
		}

		@Override
		public byte[] snapshotState(long checkpointId, long checkpointTimestamp) throws Exception {
			return data;
		}

		@Override
		public void restoreState(byte[] data) throws Exception {
			this.data = data;
		}
	}

	/**
	 * Test source that counts calls to restoreState and that can be configured
	 * to fail on restoreState calls.
	 */
	private static class RestoreStateCountingAndFailingSource
			implements SourceFunction<Integer>, Checkpointed, CheckpointListener {

		private static final long serialVersionUID = 1L;

		private static volatile int numRestoreStateCalls = 0;
		private static volatile boolean failOnRestoreStateCall = false;
		private static volatile CountDownLatch checkpointCompleteLatch = new CountDownLatch(1);
		private static volatile int emitted = 0;

		private volatile boolean running = true;

		@Override
		public void run(SourceContext<Integer> ctx) throws Exception {
			while (running) {
				ctx.collect(1);
				emitted++;

				if (failOnRestoreStateCall) {
					throw new RuntimeException("Restore test failure");
				}
			}
		}

		@Override
		public void cancel() {
			running = false;
		}

		@Override
		public Serializable snapshotState(long checkpointId, long checkpointTimestamp) throws Exception {
			return 1;
		}

		@Override
		public void restoreState(Serializable state) throws Exception {
			numRestoreStateCalls++;
		}

		@Override
		public void notifyCheckpointComplete(long checkpointId) throws Exception {
			checkpointCompleteLatch.countDown();
		}
	}

}
