/**
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

package org.apache.hadoop.mapreduce.v2.app.job.impl;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceChildJVM;
import org.apache.hadoop.mapred.ShuffleHandler;
import org.apache.hadoop.mapred.Task;
import org.apache.hadoop.mapred.TaskAttemptContextImpl;
import org.apache.hadoop.mapred.WrappedJvmID;
import org.apache.hadoop.mapred.WrappedProgressSplitsBlock;
import org.apache.hadoop.mapreduce.JobCounter;
import org.apache.hadoop.mapreduce.MRJobConfig;
import org.apache.hadoop.mapreduce.OutputCommitter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskCounter;
import org.apache.hadoop.mapreduce.TypeConverter;
import org.apache.hadoop.mapreduce.filecache.DistributedCache;
import org.apache.hadoop.mapreduce.jobhistory.JobHistoryEvent;
import org.apache.hadoop.mapreduce.jobhistory.MapAttemptFinishedEvent;
import org.apache.hadoop.mapreduce.jobhistory.ReduceAttemptFinishedEvent;
import org.apache.hadoop.mapreduce.jobhistory.TaskAttemptStartedEvent;
import org.apache.hadoop.mapreduce.jobhistory.TaskAttemptUnsuccessfulCompletionEvent;
import org.apache.hadoop.mapreduce.security.TokenCache;
import org.apache.hadoop.mapreduce.security.token.JobTokenIdentifier;
import org.apache.hadoop.mapreduce.v2.MRConstants;
import org.apache.hadoop.mapreduce.v2.api.records.Counter;
import org.apache.hadoop.mapreduce.v2.api.records.Counters;
import org.apache.hadoop.mapreduce.v2.api.records.Phase;
import org.apache.hadoop.mapreduce.v2.api.records.TaskAttemptId;
import org.apache.hadoop.mapreduce.v2.api.records.TaskAttemptReport;
import org.apache.hadoop.mapreduce.v2.api.records.TaskAttemptState;
import org.apache.hadoop.mapreduce.v2.api.records.TaskId;
import org.apache.hadoop.mapreduce.v2.api.records.TaskType;
import org.apache.hadoop.mapreduce.v2.app.TaskAttemptListener;
import org.apache.hadoop.mapreduce.v2.app.job.event.JobCounterUpdateEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.JobDiagnosticsUpdateEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.JobEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.JobEventType;
import org.apache.hadoop.mapreduce.v2.app.job.event.JobTaskAttemptFetchFailureEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptContainerAssignedEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptContainerLaunchedEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptDiagnosticsUpdateEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptEventType;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptStatusUpdateEvent;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskAttemptStatusUpdateEvent.TaskAttemptStatus;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskEventType;
import org.apache.hadoop.mapreduce.v2.app.job.event.TaskTAttemptEvent;
import org.apache.hadoop.mapreduce.v2.app.launcher.ContainerLauncher;
import org.apache.hadoop.mapreduce.v2.app.launcher.ContainerLauncherEvent;
import org.apache.hadoop.mapreduce.v2.app.launcher.ContainerRemoteLaunchEvent;
import org.apache.hadoop.mapreduce.v2.app.rm.ContainerAllocator;
import org.apache.hadoop.mapreduce.v2.app.rm.ContainerAllocatorEvent;
import org.apache.hadoop.mapreduce.v2.app.rm.ContainerRequestEvent;
import org.apache.hadoop.mapreduce.v2.app.speculate.SpeculatorEvent;
import org.apache.hadoop.mapreduce.v2.app.taskclean.TaskCleanupEvent;
import org.apache.hadoop.mapreduce.v2.util.MRApps;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.yarn.Clock;
import org.apache.hadoop.yarn.YarnException;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.ContainerToken;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.event.EventHandler;
import org.apache.hadoop.yarn.factories.RecordFactory;
import org.apache.hadoop.yarn.factory.providers.RecordFactoryProvider;
import org.apache.hadoop.yarn.state.InvalidStateTransitonException;
import org.apache.hadoop.yarn.state.SingleArcTransition;
import org.apache.hadoop.yarn.state.StateMachine;
import org.apache.hadoop.yarn.state.StateMachineFactory;
import org.apache.hadoop.yarn.util.BuilderUtils;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.hadoop.yarn.util.RackResolver;

/**
 * Implementation of TaskAttempt interface.
 */
public abstract class TaskAttemptImpl implements
    org.apache.hadoop.mapreduce.v2.app.job.TaskAttempt,
      EventHandler<TaskAttemptEvent> {

  private static final Log LOG = LogFactory.getLog(TaskAttemptImpl.class);
  private static final long MEMORY_SPLITS_RESOLUTION = 1024; //TODO Make configurable?
  private static final int MAP_MEMORY_MB_DEFAULT = 1024;
  private static final int REDUCE_MEMORY_MB_DEFAULT = 1024;
  private final static RecordFactory recordFactory = RecordFactoryProvider.getRecordFactory(null);

  protected final Configuration conf;
  protected final Path jobFile;
  protected final int partition;
  protected final EventHandler eventHandler;
  private final TaskAttemptId attemptId;
  private final Clock clock;
  private final org.apache.hadoop.mapred.JobID oldJobId;
  private final TaskAttemptListener taskAttemptListener;
  private final OutputCommitter committer;
  private final Resource resourceCapability;
  private final String[] dataLocalHosts;
  private final List<String> diagnostics = new ArrayList<String>();
  private final Lock readLock;
  private final Lock writeLock;
  private Collection<Token<? extends TokenIdentifier>> fsTokens;
  private Token<JobTokenIdentifier> jobToken;
  private static AtomicBoolean initialClasspathFlag = new AtomicBoolean();
  private static String initialClasspath = null;
  private final Object classpathLock = new Object();
  private long launchTime;
  private long finishTime;
  private WrappedProgressSplitsBlock progressSplitBlock;
  private int shufflePort = -1;

  private static final CleanupContainerTransition CLEANUP_CONTAINER_TRANSITION =
    new CleanupContainerTransition();

  private static final DiagnosticInformationUpdater 
    DIAGNOSTIC_INFORMATION_UPDATE_TRANSITION 
      = new DiagnosticInformationUpdater();

  private static final StateMachineFactory
        <TaskAttemptImpl, TaskAttemptState, TaskAttemptEventType, TaskAttemptEvent>
        stateMachineFactory
    = new StateMachineFactory
             <TaskAttemptImpl, TaskAttemptState, TaskAttemptEventType, TaskAttemptEvent>
           (TaskAttemptState.NEW)

     // Transitions from the NEW state.
     .addTransition(TaskAttemptState.NEW, TaskAttemptState.UNASSIGNED,
         TaskAttemptEventType.TA_SCHEDULE, new RequestContainerTransition(false))
     .addTransition(TaskAttemptState.NEW, TaskAttemptState.UNASSIGNED,
         TaskAttemptEventType.TA_RESCHEDULE, new RequestContainerTransition(true))
     .addTransition(TaskAttemptState.NEW, TaskAttemptState.KILLED,
         TaskAttemptEventType.TA_KILL, new KilledTransition())
     .addTransition(TaskAttemptState.NEW, TaskAttemptState.FAILED,
         TaskAttemptEventType.TA_FAILMSG, new FailedTransition())

     // Transitions from the UNASSIGNED state.
     .addTransition(TaskAttemptState.UNASSIGNED,
         TaskAttemptState.ASSIGNED, TaskAttemptEventType.TA_ASSIGNED,
         new ContainerAssignedTransition())
     .addTransition(TaskAttemptState.UNASSIGNED, TaskAttemptState.KILLED,
         TaskAttemptEventType.TA_KILL, new DeallocateContainerTransition(
             TaskAttemptState.KILLED, true))
     .addTransition(TaskAttemptState.UNASSIGNED, TaskAttemptState.FAILED,
         TaskAttemptEventType.TA_FAILMSG, new DeallocateContainerTransition(
             TaskAttemptState.FAILED, true))

     // Transitions from the ASSIGNED state.
     .addTransition(TaskAttemptState.ASSIGNED, TaskAttemptState.RUNNING,
         TaskAttemptEventType.TA_CONTAINER_LAUNCHED,
         new LaunchedContainerTransition())
     .addTransition(TaskAttemptState.ASSIGNED, TaskAttemptState.ASSIGNED,
         TaskAttemptEventType.TA_DIAGNOSTICS_UPDATE,
         DIAGNOSTIC_INFORMATION_UPDATE_TRANSITION)
     .addTransition(TaskAttemptState.ASSIGNED, TaskAttemptState.FAILED,
         TaskAttemptEventType.TA_CONTAINER_LAUNCH_FAILED,
         new DeallocateContainerTransition(TaskAttemptState.FAILED, false))
     .addTransition(TaskAttemptState.ASSIGNED, 
         TaskAttemptState.KILL_CONTAINER_CLEANUP,
         TaskAttemptEventType.TA_KILL, CLEANUP_CONTAINER_TRANSITION)
     .addTransition(TaskAttemptState.ASSIGNED, 
         TaskAttemptState.FAIL_CONTAINER_CLEANUP,
         TaskAttemptEventType.TA_FAILMSG, CLEANUP_CONTAINER_TRANSITION)

     // Transitions from RUNNING state.
     .addTransition(TaskAttemptState.RUNNING, TaskAttemptState.RUNNING,
         TaskAttemptEventType.TA_UPDATE, new StatusUpdater())
     .addTransition(TaskAttemptState.RUNNING, TaskAttemptState.RUNNING,
         TaskAttemptEventType.TA_DIAGNOSTICS_UPDATE,
         DIAGNOSTIC_INFORMATION_UPDATE_TRANSITION)
     // If no commit is required, task directly goes to success
     .addTransition(TaskAttemptState.RUNNING,
         TaskAttemptState.SUCCESS_CONTAINER_CLEANUP,
         TaskAttemptEventType.TA_DONE, CLEANUP_CONTAINER_TRANSITION)
     // If commit is required, task goes through commit pending state.
     .addTransition(TaskAttemptState.RUNNING,
         TaskAttemptState.COMMIT_PENDING,
         TaskAttemptEventType.TA_COMMIT_PENDING, new CommitPendingTransition())
     // Failure handling while RUNNING
     .addTransition(TaskAttemptState.RUNNING,
         TaskAttemptState.FAIL_CONTAINER_CLEANUP,
         TaskAttemptEventType.TA_FAILMSG, CLEANUP_CONTAINER_TRANSITION)
      //for handling container exit without sending the done or fail msg
     .addTransition(TaskAttemptState.RUNNING,
         TaskAttemptState.FAIL_CONTAINER_CLEANUP,
         TaskAttemptEventType.TA_CONTAINER_COMPLETED,
         CLEANUP_CONTAINER_TRANSITION)
     // Timeout handling while RUNNING
     .addTransition(TaskAttemptState.RUNNING,
         TaskAttemptState.FAIL_CONTAINER_CLEANUP,
         TaskAttemptEventType.TA_TIMED_OUT, CLEANUP_CONTAINER_TRANSITION)
     // Kill handling
     .addTransition(TaskAttemptState.RUNNING,
         TaskAttemptState.KILL_CONTAINER_CLEANUP, TaskAttemptEventType.TA_KILL,
         CLEANUP_CONTAINER_TRANSITION)

     // Transitions from COMMIT_PENDING state
     .addTransition(TaskAttemptState.COMMIT_PENDING,
         TaskAttemptState.COMMIT_PENDING, TaskAttemptEventType.TA_UPDATE,
         new StatusUpdater())
     .addTransition(TaskAttemptState.COMMIT_PENDING,
         TaskAttemptState.COMMIT_PENDING,
         TaskAttemptEventType.TA_DIAGNOSTICS_UPDATE,
         DIAGNOSTIC_INFORMATION_UPDATE_TRANSITION)
     .addTransition(TaskAttemptState.COMMIT_PENDING,
         TaskAttemptState.SUCCESS_CONTAINER_CLEANUP,
         TaskAttemptEventType.TA_DONE, CLEANUP_CONTAINER_TRANSITION)
     .addTransition(TaskAttemptState.COMMIT_PENDING,
         TaskAttemptState.KILL_CONTAINER_CLEANUP, TaskAttemptEventType.TA_KILL,
         CLEANUP_CONTAINER_TRANSITION)
     .addTransition(TaskAttemptState.COMMIT_PENDING,
         TaskAttemptState.FAIL_CONTAINER_CLEANUP,
         TaskAttemptEventType.TA_FAILMSG, CLEANUP_CONTAINER_TRANSITION)
     .addTransition(TaskAttemptState.COMMIT_PENDING,
         TaskAttemptState.FAIL_CONTAINER_CLEANUP,
         TaskAttemptEventType.TA_CONTAINER_COMPLETED,
         CLEANUP_CONTAINER_TRANSITION)
     .addTransition(TaskAttemptState.COMMIT_PENDING,
         TaskAttemptState.FAIL_CONTAINER_CLEANUP,
         TaskAttemptEventType.TA_TIMED_OUT, CLEANUP_CONTAINER_TRANSITION)

     // Transitions from SUCCESS_CONTAINER_CLEANUP state
     // kill and cleanup the container
     .addTransition(TaskAttemptState.SUCCESS_CONTAINER_CLEANUP,
         TaskAttemptState.SUCCEEDED, TaskAttemptEventType.TA_CONTAINER_CLEANED,
         new SucceededTransition())
     .addTransition(
          TaskAttemptState.SUCCESS_CONTAINER_CLEANUP,
          TaskAttemptState.SUCCESS_CONTAINER_CLEANUP,
          TaskAttemptEventType.TA_DIAGNOSTICS_UPDATE,
          DIAGNOSTIC_INFORMATION_UPDATE_TRANSITION)
      // Ignore-able events
     .addTransition(TaskAttemptState.SUCCESS_CONTAINER_CLEANUP,
         TaskAttemptState.SUCCESS_CONTAINER_CLEANUP,
         EnumSet.of(TaskAttemptEventType.TA_KILL,
             TaskAttemptEventType.TA_FAILMSG,
             TaskAttemptEventType.TA_TIMED_OUT,
             TaskAttemptEventType.TA_CONTAINER_COMPLETED))

     // Transitions from FAIL_CONTAINER_CLEANUP state.
     .addTransition(TaskAttemptState.FAIL_CONTAINER_CLEANUP,
         TaskAttemptState.FAIL_TASK_CLEANUP,
         TaskAttemptEventType.TA_CONTAINER_CLEANED, new TaskCleanupTransition())
     .addTransition(TaskAttemptState.FAIL_CONTAINER_CLEANUP,
         TaskAttemptState.FAIL_CONTAINER_CLEANUP,
         TaskAttemptEventType.TA_DIAGNOSTICS_UPDATE,
         DIAGNOSTIC_INFORMATION_UPDATE_TRANSITION)
      // Ignore-able events
     .addTransition(TaskAttemptState.FAIL_CONTAINER_CLEANUP,
         TaskAttemptState.FAIL_CONTAINER_CLEANUP,
         EnumSet.of(TaskAttemptEventType.TA_KILL,
             TaskAttemptEventType.TA_CONTAINER_COMPLETED,
             TaskAttemptEventType.TA_UPDATE,
             TaskAttemptEventType.TA_COMMIT_PENDING,
             TaskAttemptEventType.TA_CONTAINER_LAUNCHED,
             TaskAttemptEventType.TA_DONE,
             TaskAttemptEventType.TA_FAILMSG,
             TaskAttemptEventType.TA_TIMED_OUT))

      // Transitions from KILL_CONTAINER_CLEANUP
     .addTransition(TaskAttemptState.KILL_CONTAINER_CLEANUP,
         TaskAttemptState.KILL_TASK_CLEANUP,
         TaskAttemptEventType.TA_CONTAINER_CLEANED, new TaskCleanupTransition())
     .addTransition(TaskAttemptState.KILL_CONTAINER_CLEANUP,
         TaskAttemptState.KILL_CONTAINER_CLEANUP,
         TaskAttemptEventType.TA_DIAGNOSTICS_UPDATE,
         DIAGNOSTIC_INFORMATION_UPDATE_TRANSITION)
     // Ignore-able events
     .addTransition(
         TaskAttemptState.KILL_CONTAINER_CLEANUP,
         TaskAttemptState.KILL_CONTAINER_CLEANUP,
         EnumSet.of(TaskAttemptEventType.TA_KILL,
             TaskAttemptEventType.TA_CONTAINER_COMPLETED,
             TaskAttemptEventType.TA_UPDATE,
             TaskAttemptEventType.TA_COMMIT_PENDING,
             TaskAttemptEventType.TA_CONTAINER_LAUNCHED,
             TaskAttemptEventType.TA_DONE,
             TaskAttemptEventType.TA_FAILMSG,
             TaskAttemptEventType.TA_TIMED_OUT))

     // Transitions from FAIL_TASK_CLEANUP
     // run the task cleanup
     .addTransition(TaskAttemptState.FAIL_TASK_CLEANUP,
         TaskAttemptState.FAILED, TaskAttemptEventType.TA_CLEANUP_DONE,
         new FailedTransition())
     .addTransition(TaskAttemptState.FAIL_TASK_CLEANUP,
         TaskAttemptState.FAIL_TASK_CLEANUP,
         TaskAttemptEventType.TA_DIAGNOSTICS_UPDATE,
         DIAGNOSTIC_INFORMATION_UPDATE_TRANSITION)
      // Ignore-able events
     .addTransition(TaskAttemptState.FAIL_TASK_CLEANUP,
         TaskAttemptState.FAIL_TASK_CLEANUP,
         EnumSet.of(TaskAttemptEventType.TA_KILL,
             TaskAttemptEventType.TA_CONTAINER_COMPLETED,
             TaskAttemptEventType.TA_UPDATE,
             TaskAttemptEventType.TA_COMMIT_PENDING,
             TaskAttemptEventType.TA_DONE,
             TaskAttemptEventType.TA_FAILMSG))

     // Transitions from KILL_TASK_CLEANUP
     .addTransition(TaskAttemptState.KILL_TASK_CLEANUP,
         TaskAttemptState.KILLED, TaskAttemptEventType.TA_CLEANUP_DONE,
         new KilledTransition())
     .addTransition(TaskAttemptState.KILL_TASK_CLEANUP,
         TaskAttemptState.KILL_TASK_CLEANUP,
         TaskAttemptEventType.TA_DIAGNOSTICS_UPDATE,
         DIAGNOSTIC_INFORMATION_UPDATE_TRANSITION)
     // Ignore-able events
     .addTransition(TaskAttemptState.KILL_TASK_CLEANUP,
         TaskAttemptState.KILL_TASK_CLEANUP,
         EnumSet.of(TaskAttemptEventType.TA_KILL,
             TaskAttemptEventType.TA_CONTAINER_COMPLETED,
             TaskAttemptEventType.TA_UPDATE,
             TaskAttemptEventType.TA_COMMIT_PENDING,
             TaskAttemptEventType.TA_DONE,
             TaskAttemptEventType.TA_FAILMSG))

      // Transitions from SUCCEEDED
     .addTransition(TaskAttemptState.SUCCEEDED, //only possible for map attempts
         TaskAttemptState.FAILED,
         TaskAttemptEventType.TA_TOO_MANY_FETCH_FAILURE,
         new TooManyFetchFailureTransition())
     .addTransition(
         TaskAttemptState.SUCCEEDED, TaskAttemptState.SUCCEEDED,
         TaskAttemptEventType.TA_DIAGNOSTICS_UPDATE,
         DIAGNOSTIC_INFORMATION_UPDATE_TRANSITION)
     // Ignore-able events for SUCCEEDED state
     .addTransition(TaskAttemptState.SUCCEEDED,
         TaskAttemptState.SUCCEEDED,
         EnumSet.of(TaskAttemptEventType.TA_KILL,
             TaskAttemptEventType.TA_FAILMSG,
             TaskAttemptEventType.TA_CONTAINER_COMPLETED))

     // Transitions from FAILED state
     .addTransition(TaskAttemptState.FAILED, TaskAttemptState.FAILED,
       TaskAttemptEventType.TA_DIAGNOSTICS_UPDATE,
       DIAGNOSTIC_INFORMATION_UPDATE_TRANSITION)
     // Ignore-able events for FAILED state
     .addTransition(TaskAttemptState.FAILED, TaskAttemptState.FAILED,
         EnumSet.of(TaskAttemptEventType.TA_KILL,
             TaskAttemptEventType.TA_ASSIGNED,
             TaskAttemptEventType.TA_CONTAINER_COMPLETED,
             TaskAttemptEventType.TA_UPDATE,
             TaskAttemptEventType.TA_CONTAINER_LAUNCHED,
             TaskAttemptEventType.TA_COMMIT_PENDING,
             TaskAttemptEventType.TA_DONE,
             TaskAttemptEventType.TA_FAILMSG))

     // Transitions from KILLED state
     .addTransition(TaskAttemptState.KILLED, TaskAttemptState.KILLED,
         TaskAttemptEventType.TA_DIAGNOSTICS_UPDATE,
         DIAGNOSTIC_INFORMATION_UPDATE_TRANSITION)
     // Ignore-able events for KILLED state
     .addTransition(TaskAttemptState.KILLED, TaskAttemptState.KILLED,
         EnumSet.of(TaskAttemptEventType.TA_KILL,
             TaskAttemptEventType.TA_ASSIGNED,
             TaskAttemptEventType.TA_CONTAINER_COMPLETED,
             TaskAttemptEventType.TA_UPDATE,
             TaskAttemptEventType.TA_CONTAINER_LAUNCHED,
             TaskAttemptEventType.TA_COMMIT_PENDING,
             TaskAttemptEventType.TA_DONE,
             TaskAttemptEventType.TA_FAILMSG))

     // create the topology tables
     .installTopology();

  private final StateMachine
         <TaskAttemptState, TaskAttemptEventType, TaskAttemptEvent>
    stateMachine;

  private ContainerId containerID;
  private String nodeHostName;
  private String containerMgrAddress;
  private String nodeHttpAddress;
  private WrappedJvmID jvmID;
  private ContainerToken containerToken;
  private Resource assignedCapability;
  
  //this takes good amount of memory ~ 30KB. Instantiate it lazily
  //and make it null once task is launched.
  private org.apache.hadoop.mapred.Task remoteTask;
  
  //this is the last status reported by the REMOTE running attempt
  private TaskAttemptStatus reportedStatus;

  public TaskAttemptImpl(TaskId taskId, int i, EventHandler eventHandler,
      TaskAttemptListener taskAttemptListener, Path jobFile, int partition,
      Configuration conf, String[] dataLocalHosts, OutputCommitter committer,
      Token<JobTokenIdentifier> jobToken,
      Collection<Token<? extends TokenIdentifier>> fsTokens, Clock clock) {
    oldJobId = TypeConverter.fromYarn(taskId.getJobId());
    this.conf = conf;
    this.clock = clock;
    attemptId = recordFactory.newRecordInstance(TaskAttemptId.class);
    attemptId.setTaskId(taskId);
    attemptId.setId(i);
    this.taskAttemptListener = taskAttemptListener;

    // Initialize reportedStatus
    reportedStatus = new TaskAttemptStatus();
    initTaskAttemptStatus(reportedStatus);

    ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    readLock = readWriteLock.readLock();
    writeLock = readWriteLock.writeLock();

    this.fsTokens = fsTokens;
    this.jobToken = jobToken;
    this.eventHandler = eventHandler;
    this.committer = committer;
    this.jobFile = jobFile;
    this.partition = partition;

    //TODO:create the resource reqt for this Task attempt
    this.resourceCapability = recordFactory.newRecordInstance(Resource.class);
    this.resourceCapability.setMemory(getMemoryRequired(conf, taskId.getTaskType()));
    this.dataLocalHosts = dataLocalHosts;
    RackResolver.init(conf);

    // This "this leak" is okay because the retained pointer is in an
    //  instance variable.
    stateMachine = stateMachineFactory.make(this);
  }

  private int getMemoryRequired(Configuration conf, TaskType taskType) {
    int memory = 1024;
    if (taskType == TaskType.MAP)  {
      memory = conf.getInt(MRJobConfig.MAP_MEMORY_MB, MAP_MEMORY_MB_DEFAULT);
    } else if (taskType == TaskType.REDUCE) {
      memory = conf.getInt(MRJobConfig.REDUCE_MEMORY_MB, REDUCE_MEMORY_MB_DEFAULT);
    }
    
    return memory;
  }

  /**
   * Create a {@link LocalResource} record with all the given parameters.
   * TODO: This should pave way for Builder pattern.
   */
  private static LocalResource createLocalResource(FileSystem fc,
      RecordFactory recordFactory, Path file, LocalResourceType type,
      LocalResourceVisibility visibility) throws IOException {
    FileStatus fstat = fc.getFileStatus(file);
    LocalResource resource =
        recordFactory.newRecordInstance(LocalResource.class);
    resource.setResource(ConverterUtils.getYarnUrlFromPath(fc.resolvePath(fstat
        .getPath())));
    resource.setType(type);
    resource.setVisibility(visibility);
    resource.setSize(fstat.getLen());
    resource.setTimestamp(fstat.getModificationTime());
    return resource;
  }

  /**
   * Lock this on initialClasspath so that there is only one fork in the AM for
   * getting the initial class-path. TODO: This should go away once we construct
   * a parent CLC and use it for all the containers.
   */
  private String getInitialClasspath() throws IOException {
    synchronized (classpathLock) {
      if (initialClasspathFlag.get()) {
        return initialClasspath;
      }
      Map<String, String> env = new HashMap<String, String>();
      MRApps.setInitialClasspath(env);
      initialClasspath = env.get(MRApps.CLASSPATH);
      initialClasspathFlag.set(true);
      return initialClasspath;
    }
  }


  /**
   * Create the {@link ContainerLaunchContext} for this attempt.
   */
  private ContainerLaunchContext createContainerLaunchContext() {

    ContainerLaunchContext container =
        recordFactory.newRecordInstance(ContainerLaunchContext.class);

    try {
      FileSystem remoteFS = FileSystem.get(conf);

      // //////////// Set up JobJar to be localized properly on the remote NM.
      if (conf.get(MRJobConfig.JAR) != null) {
        Path remoteJobJar = (new Path(remoteTask.getConf().get(
              MRJobConfig.JAR))).makeQualified(remoteFS.getUri(), 
                                               remoteFS.getWorkingDirectory());
        container.setLocalResource(
            MRConstants.JOB_JAR,
            createLocalResource(remoteFS, recordFactory, remoteJobJar,
                LocalResourceType.FILE, LocalResourceVisibility.APPLICATION));
        LOG.info("The job-jar file on the remote FS is "
            + remoteJobJar.toUri().toASCIIString());
      } else {
        // Job jar may be null. For e.g, for pipes, the job jar is the hadoop
        // mapreduce jar itself which is already on the classpath.
        LOG.info("Job jar is not present. "
            + "Not adding any jar to the list of resources.");
      }
      // //////////// End of JobJar setup

      // //////////// Set up JobConf to be localized properly on the remote NM.
      Path path =
          MRApps.getStagingAreaDir(conf, UserGroupInformation
              .getCurrentUser().getShortUserName());
      Path remoteJobSubmitDir =
          new Path(path, oldJobId.toString());
      Path remoteJobConfPath = 
          new Path(remoteJobSubmitDir, MRConstants.JOB_CONF_FILE);
      container.setLocalResource(
          MRConstants.JOB_CONF_FILE,
          createLocalResource(remoteFS, recordFactory, remoteJobConfPath,
              LocalResourceType.FILE, LocalResourceVisibility.APPLICATION));
      LOG.info("The job-conf file on the remote FS is "
          + remoteJobConfPath.toUri().toASCIIString());
      // //////////// End of JobConf setup

      // Setup DistributedCache
      setupDistributedCache(remoteFS, conf, container);

      // Setup up tokens
      Credentials taskCredentials = new Credentials();

      if (UserGroupInformation.isSecurityEnabled()) {
        // Add file-system tokens
        for (Token<? extends TokenIdentifier> token : fsTokens) {
          LOG.info("Putting fs-token for NM use for launching container : "
              + token.toString());
          taskCredentials.addToken(token.getService(), token);
        }
      }

      // LocalStorageToken is needed irrespective of whether security is enabled
      // or not.
      TokenCache.setJobToken(jobToken, taskCredentials);

      DataOutputBuffer containerTokens_dob = new DataOutputBuffer();
      LOG.info("Size of containertokens_dob is "
          + taskCredentials.numberOfTokens());
      taskCredentials.writeTokenStorageToStream(containerTokens_dob);
      container.setContainerTokens(
          ByteBuffer.wrap(containerTokens_dob.getData(), 0,
              containerTokens_dob.getLength()));

      // Add shuffle token
      LOG.info("Putting shuffle token in serviceData");
      container
          .setServiceData(
              ShuffleHandler.MAPREDUCE_SHUFFLE_SERVICEID,
              ShuffleHandler.serializeServiceData(jobToken));

      MRApps.addToClassPath(container.getAllEnv(), getInitialClasspath());
    } catch (IOException e) {
      throw new YarnException(e);
    }
    
    container.setContainerId(containerID);
    container.setUser(conf.get(MRJobConfig.USER_NAME)); // TODO: Fix

    File workDir = new File("$PWD"); // Will be expanded by the shell.
    String containerLogDir =
        new File(ApplicationConstants.LOG_DIR_EXPANSION_VAR).toString();
    String childTmpDir = new File(workDir, "tmp").toString();
    String javaHome = "${JAVA_HOME}"; // Will be expanded by the shell.
    String nmLdLibraryPath = "{LD_LIBRARY_PATH}"; // Expanded by the shell?
    List<String> classPaths = new ArrayList<String>();

    String localizedApplicationTokensFile =
        new File(workDir, MRConstants.APPLICATION_TOKENS_FILE).toString();
    classPaths.add(MRConstants.JOB_JAR);
    classPaths.add(MRConstants.YARN_MAPREDUCE_APP_JAR_PATH);
    classPaths.add(workDir.toString()); // TODO

    // Construct the actual Container
    container.addAllCommands(MapReduceChildJVM.getVMCommand(
        taskAttemptListener.getAddress(), remoteTask, javaHome,
        workDir.toString(), containerLogDir, childTmpDir, jvmID));

    MapReduceChildJVM.setVMEnv(container.getAllEnv(), classPaths,
        workDir.toString(), containerLogDir, nmLdLibraryPath, remoteTask,
        localizedApplicationTokensFile);

    // Construct the actual Container
    container.setContainerId(containerID);
    container.setUser(conf.get(MRJobConfig.USER_NAME));
    container.setResource(assignedCapability);
    return container;
  }

  private static long[] parseTimeStamps(String[] strs) {
    if (null == strs) {
      return null;
    }
    long[] result = new long[strs.length];
    for(int i=0; i < strs.length; ++i) {
      result[i] = Long.parseLong(strs[i]);
    }
    return result;
  }

  private void setupDistributedCache(FileSystem remoteFS, Configuration conf, 
      ContainerLaunchContext container) throws IOException {
    
    // Cache archives
    parseDistributedCacheArtifacts(remoteFS, container, LocalResourceType.ARCHIVE, 
        DistributedCache.getCacheArchives(conf), 
        parseTimeStamps(DistributedCache.getArchiveTimestamps(conf)), 
        getFileSizes(conf, MRJobConfig.CACHE_ARCHIVES_SIZES), 
        DistributedCache.getArchiveVisibilities(conf), 
        DistributedCache.getArchiveClassPaths(conf));
    
    // Cache files
    parseDistributedCacheArtifacts(remoteFS, container, LocalResourceType.FILE, 
        DistributedCache.getCacheFiles(conf),
        parseTimeStamps(DistributedCache.getFileTimestamps(conf)),
        getFileSizes(conf, MRJobConfig.CACHE_FILES_SIZES),
        DistributedCache.getFileVisibilities(conf),
        DistributedCache.getFileClassPaths(conf));
  }

  // TODO - Move this to MR!
  // Use TaskDistributedCacheManager.CacheFiles.makeCacheFiles(URI[], 
  // long[], boolean[], Path[], FileType)
  private void parseDistributedCacheArtifacts(
      FileSystem remoteFS, ContainerLaunchContext container, LocalResourceType type,
      URI[] uris, long[] timestamps, long[] sizes, boolean visibilities[], 
      Path[] pathsToPutOnClasspath) throws IOException {

    if (uris != null) {
      // Sanity check
      if ((uris.length != timestamps.length) || (uris.length != sizes.length) ||
          (uris.length != visibilities.length)) {
        throw new IllegalArgumentException("Invalid specification for " +
        		"distributed-cache artifacts of type " + type + " :" +
        		" #uris=" + uris.length +
        		" #timestamps=" + timestamps.length +
        		" #visibilities=" + visibilities.length
        		);
      }
      
      Map<String, Path> classPaths = new HashMap<String, Path>();
      if (pathsToPutOnClasspath != null) {
        for (Path p : pathsToPutOnClasspath) {
          p = remoteFS.resolvePath(p.makeQualified(remoteFS.getUri(),
              remoteFS.getWorkingDirectory()));
          classPaths.put(p.toUri().getPath().toString(), p);
        }
      }
      for (int i = 0; i < uris.length; ++i) {
        URI u = uris[i];
        Path p = new Path(u);
        p = remoteFS.resolvePath(p.makeQualified(remoteFS.getUri(),
            remoteFS.getWorkingDirectory()));
        // Add URI fragment or just the filename
        Path name = new Path((null == u.getFragment())
          ? p.getName()
          : u.getFragment());
        if (name.isAbsolute()) {
          throw new IllegalArgumentException("Resource name must be relative");
        }
        String linkName = name.toUri().getPath();
        container.setLocalResource(
            linkName,
            BuilderUtils.newLocalResource(
                p.toUri(), type, 
                visibilities[i]
                  ? LocalResourceVisibility.PUBLIC
                  : LocalResourceVisibility.PRIVATE,
                sizes[i], timestamps[i])
        );
        if (classPaths.containsKey(u.getPath())) {
          Map<String, String> environment = container.getAllEnv();
          MRApps.addToClassPath(environment, linkName);
        }
      }
    }
  }
  
  // TODO - Move this to MR!
  private static long[] getFileSizes(Configuration conf, String key) {
    String[] strs = conf.getStrings(key);
    if (strs == null) {
      return null;
    }
    long[] result = new long[strs.length];
    for(int i=0; i < strs.length; ++i) {
      result[i] = Long.parseLong(strs[i]);
    }
    return result;
  }
  
  @Override
  public ContainerId getAssignedContainerID() {
    readLock.lock();
    try {
      return containerID;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public String getAssignedContainerMgrAddress() {
    readLock.lock();
    try {
      return containerMgrAddress;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public long getLaunchTime() {
    readLock.lock();
    try {
      return launchTime;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public long getFinishTime() {
    readLock.lock();
    try {
      return finishTime;
    } finally {
      readLock.unlock();
    }
  }


  @Override
  public int getShufflePort() {
    readLock.lock();
    try {
      return shufflePort;
    } finally {
      readLock.unlock();
    }
  }

  /**If container Assigned then return the node's address, otherwise null.
   */
  @Override
  public String getNodeHttpAddress() {
    readLock.lock();
    try {
      return nodeHttpAddress;
    } finally {
      readLock.unlock();
    }
  }

  protected abstract org.apache.hadoop.mapred.Task createRemoteTask();

  @Override
  public TaskAttemptId getID() {
    return attemptId;
  }

  @Override
  public boolean isFinished() {
    readLock.lock();
    try {
      // TODO: Use stateMachine level method?
      return (getState() == TaskAttemptState.SUCCEEDED || 
          getState() == TaskAttemptState.FAILED ||
          getState() == TaskAttemptState.KILLED);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public TaskAttemptReport getReport() {
    TaskAttemptReport result = recordFactory.newRecordInstance(TaskAttemptReport.class);
    readLock.lock();
    try {
      result.setTaskAttemptId(attemptId);
      //take the LOCAL state of attempt
      //DO NOT take from reportedStatus
      
      result.setTaskAttemptState(getState());
      result.setProgress(reportedStatus.progress);
      result.setStartTime(launchTime);
      result.setFinishTime(finishTime);
      result.setDiagnosticInfo(reportedStatus.diagnosticInfo);
      result.setPhase(reportedStatus.phase);
      result.setStateString(reportedStatus.stateString);
      result.setCounters(getCounters());
      return result;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public List<String> getDiagnostics() {
    List<String> result = new ArrayList<String>();
    readLock.lock();
    try {
      result.addAll(diagnostics);
      return result;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Counters getCounters() {
    readLock.lock();
    try {
      Counters counters = reportedStatus.counters;
      if (counters == null) {
        counters = recordFactory.newRecordInstance(Counters.class);
//        counters.groups = new HashMap<String, CounterGroup>();
      }
      return counters;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public float getProgress() {
    readLock.lock();
    try {
      return reportedStatus.progress;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public TaskAttemptState getState() {
    readLock.lock();
    try {
      return stateMachine.getCurrentState();
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public void handle(TaskAttemptEvent event) {
    LOG.info("Processing " + event.getTaskAttemptID() +
        " of type " + event.getType());
    writeLock.lock();
    try {
      final TaskAttemptState oldState = getState();
      try {
        stateMachine.doTransition(event.getType(), event);
      } catch (InvalidStateTransitonException e) {
        LOG.error("Can't handle this event at current state", e);
        eventHandler.handle(new JobDiagnosticsUpdateEvent(
            this.attemptId.getTaskId().getJobId(), "Invalid event " + event.getType() + 
            " on TaskAttempt " + this.attemptId));
        eventHandler.handle(new JobEvent(this.attemptId.getTaskId().getJobId(),
            JobEventType.INTERNAL_ERROR));
      }
      if (oldState != getState()) {
          LOG.info(attemptId + " TaskAttempt Transitioned from " 
           + oldState + " to "
           + getState());
      }
    } finally {
      writeLock.unlock();
    }
  }

  //always called in write lock
  private void setFinishTime() {
    //set the finish time only if launch time is set
    if (launchTime != 0) {
      finishTime = clock.getTime();
    }
  }
  
  private static long computeSlotMillis(TaskAttemptImpl taskAttempt) {
    TaskType taskType = taskAttempt.getID().getTaskId().getTaskType();
    int slotMemoryReq =
        taskAttempt.getMemoryRequired(taskAttempt.conf, taskType);
    int simSlotsRequired =
        slotMemoryReq
            / (taskType == TaskType.MAP ? MAP_MEMORY_MB_DEFAULT
                : REDUCE_MEMORY_MB_DEFAULT);
    // Simulating MRv1 slots for counters by assuming *_MEMORY_MB_DEFAULT
    // corresponds to a MrV1 slot.
    // Fallow slot millis is not applicable in MRv2 - since a container is
    // either assigned with the required memory or is not. No partial
    // reserveations
    long slotMillisIncrement =
        simSlotsRequired
            * (taskAttempt.getFinishTime() - taskAttempt.getLaunchTime());
    return slotMillisIncrement;
  }
  
  private static JobCounterUpdateEvent createJobCounterUpdateEventTAFailed(
      TaskAttemptImpl taskAttempt) {
    TaskType taskType = taskAttempt.getID().getTaskId().getTaskType();
    JobCounterUpdateEvent jce = new JobCounterUpdateEvent(taskAttempt.getID().getTaskId().getJobId());
    
    long slotMillisIncrement = computeSlotMillis(taskAttempt);
    
    if (taskType == TaskType.MAP) {
      jce.addCounterUpdate(JobCounter.NUM_FAILED_MAPS, 1);
      jce.addCounterUpdate(JobCounter.SLOTS_MILLIS_MAPS, slotMillisIncrement);
    } else {
      jce.addCounterUpdate(JobCounter.NUM_FAILED_REDUCES, 1);
      jce.addCounterUpdate(JobCounter.SLOTS_MILLIS_REDUCES, slotMillisIncrement);
    }
    return jce;
  }

  private static TaskAttemptUnsuccessfulCompletionEvent createTaskAttemptUnsuccessfulCompletionEvent(
      TaskAttemptImpl taskAttempt, TaskAttemptState attemptState) {
    TaskAttemptUnsuccessfulCompletionEvent tauce = new TaskAttemptUnsuccessfulCompletionEvent(
        TypeConverter.fromYarn(taskAttempt.attemptId),
        TypeConverter.fromYarn(taskAttempt.attemptId.getTaskId().getTaskType()),
        attemptState.toString(), taskAttempt.finishTime,
        taskAttempt.nodeHostName == null ? "UNKNOWN" : taskAttempt.nodeHostName,
        taskAttempt.reportedStatus.diagnosticInfo.toString(),
        taskAttempt.getProgressSplitBlock().burst());
    return tauce;
  }

  private WrappedProgressSplitsBlock getProgressSplitBlock() {
    readLock.lock();
    try {
      if (progressSplitBlock == null) {
        progressSplitBlock = new WrappedProgressSplitsBlock(conf.getInt(
            MRJobConfig.MR_AM_NUM_PROGRESS_SPLITS,
            MRJobConfig.DEFAULT_MR_AM_NUM_PROGRESS_SPLITS));
      }
      return progressSplitBlock;
    } finally {
      readLock.unlock();
    }
  }
  
  private void updateProgressSplits() {
    double newProgress = reportedStatus.progress;
    Counters counters = reportedStatus.counters;
    if (counters == null)
      return;

    WrappedProgressSplitsBlock splitsBlock = getProgressSplitBlock();
    if (splitsBlock != null) {
      long now = clock.getTime();
      long start = getLaunchTime(); // TODO Ensure not 0

      if (start != 0 && now - start <= Integer.MAX_VALUE) {
        splitsBlock.getProgressWallclockTime().extend(newProgress,
            (int) (now - start));
      }

      Counter cpuCounter = counters.getCounter(
          TaskCounter.CPU_MILLISECONDS);
      if (cpuCounter != null && cpuCounter.getValue() <= Integer.MAX_VALUE) {
        splitsBlock.getProgressCPUTime().extend(newProgress,
            (int) cpuCounter.getValue());
      }

      Counter virtualBytes = counters.getCounter(
          TaskCounter.VIRTUAL_MEMORY_BYTES);
      if (virtualBytes != null) {
        splitsBlock.getProgressVirtualMemoryKbytes().extend(newProgress,
            (int) (virtualBytes.getValue() / (MEMORY_SPLITS_RESOLUTION)));
      }

      Counter physicalBytes = counters.getCounter(
          TaskCounter.PHYSICAL_MEMORY_BYTES);
      if (physicalBytes != null) {
        splitsBlock.getProgressPhysicalMemoryKbytes().extend(newProgress,
            (int) (physicalBytes.getValue() / (MEMORY_SPLITS_RESOLUTION)));
      }
    }
  }

  private static class RequestContainerTransition implements
      SingleArcTransition<TaskAttemptImpl, TaskAttemptEvent> {
    private final boolean rescheduled;
    public RequestContainerTransition(boolean rescheduled) {
      this.rescheduled = rescheduled;
    }
    @Override
    public void transition(TaskAttemptImpl taskAttempt, 
        TaskAttemptEvent event) {
      // Tell any speculator that we're requesting a container
      taskAttempt.eventHandler.handle
          (new SpeculatorEvent(taskAttempt.getID().getTaskId(), +1));
      //request for container
      if (rescheduled) {
        taskAttempt.eventHandler.handle(
            ContainerRequestEvent.createContainerRequestEventForFailedContainer(
                taskAttempt.attemptId, 
                taskAttempt.resourceCapability));
      } else {
        int i = 0;
        String[] racks = new String[taskAttempt.dataLocalHosts.length];
        for (String host : taskAttempt.dataLocalHosts) {
          racks[i++] = RackResolver.resolve(host).getNetworkLocation();
        }
        taskAttempt.eventHandler.handle(
            new ContainerRequestEvent(taskAttempt.attemptId, 
                taskAttempt.resourceCapability, 
                taskAttempt.dataLocalHosts, racks));
      }
    }
  }

  private static class ContainerAssignedTransition implements
      SingleArcTransition<TaskAttemptImpl, TaskAttemptEvent> {
    @Override
    public void transition(final TaskAttemptImpl taskAttempt, 
        TaskAttemptEvent event) {
      TaskAttemptContainerAssignedEvent cEvent = 
        (TaskAttemptContainerAssignedEvent) event;
      taskAttempt.containerID = cEvent.getContainer().getId();
      taskAttempt.nodeHostName = cEvent.getContainer().getNodeId().getHost();
      taskAttempt.containerMgrAddress = cEvent.getContainer().getNodeId()
          .toString();
      taskAttempt.nodeHttpAddress = cEvent.getContainer().getNodeHttpAddress();
      taskAttempt.containerToken = cEvent.getContainer().getContainerToken();
      taskAttempt.assignedCapability = cEvent.getContainer().getResource();
      // this is a _real_ Task (classic Hadoop mapred flavor):
      taskAttempt.remoteTask = taskAttempt.createRemoteTask();
      taskAttempt.jvmID = new WrappedJvmID(
          taskAttempt.remoteTask.getTaskID().getJobID(), 
          taskAttempt.remoteTask.isMapTask(), taskAttempt.containerID.getId());
      
      //launch the container
      //create the container object to be launched for a given Task attempt
      taskAttempt.eventHandler.handle(
          new ContainerRemoteLaunchEvent(taskAttempt.attemptId, 
              taskAttempt.containerID, 
              taskAttempt.containerMgrAddress, taskAttempt.containerToken) {
        @Override
        public ContainerLaunchContext getContainer() {
          return taskAttempt.createContainerLaunchContext();
        }
        @Override
        public Task getRemoteTask() {  // classic mapred Task, not YARN version
          return taskAttempt.remoteTask;
        }
      });

      // send event to speculator that our container needs are satisfied
      taskAttempt.eventHandler.handle
          (new SpeculatorEvent(taskAttempt.getID().getTaskId(), -1));
    }
  }

  private static class DeallocateContainerTransition implements
      SingleArcTransition<TaskAttemptImpl, TaskAttemptEvent> {
    private final TaskAttemptState finalState;
    private final boolean withdrawsContainerRequest;
    DeallocateContainerTransition
        (TaskAttemptState finalState, boolean withdrawsContainerRequest) {
      this.finalState = finalState;
      this.withdrawsContainerRequest = withdrawsContainerRequest;
    }
    @Override
    public void transition(TaskAttemptImpl taskAttempt, 
        TaskAttemptEvent event) {
      //set the finish time
      taskAttempt.setFinishTime();
      //send the deallocate event to ContainerAllocator
      taskAttempt.eventHandler.handle(
          new ContainerAllocatorEvent(taskAttempt.attemptId,
          ContainerAllocator.EventType.CONTAINER_DEALLOCATE));

      // send event to speculator that we withdraw our container needs, if
      //  we're transitioning out of UNASSIGNED
      if (withdrawsContainerRequest) {
        taskAttempt.eventHandler.handle
            (new SpeculatorEvent(taskAttempt.getID().getTaskId(), -1));
      }

      switch(finalState) {
        case FAILED:
          taskAttempt.eventHandler.handle(new TaskTAttemptEvent(
              taskAttempt.attemptId,
              TaskEventType.T_ATTEMPT_FAILED));
          break;
        case KILLED:
          taskAttempt.eventHandler.handle(new TaskTAttemptEvent(
              taskAttempt.attemptId,
              TaskEventType.T_ATTEMPT_KILLED));
          break;
      }
      if (taskAttempt.getLaunchTime() != 0) {
        TaskAttemptUnsuccessfulCompletionEvent tauce =
            createTaskAttemptUnsuccessfulCompletionEvent(taskAttempt,
                finalState);
        taskAttempt.eventHandler
            .handle(createJobCounterUpdateEventTAFailed(taskAttempt));
        taskAttempt.eventHandler.handle(new JobHistoryEvent(
            taskAttempt.attemptId.getTaskId().getJobId(), tauce));
      } else {
        LOG.debug("Not generating HistoryFinish event since start event not generated for taskAttempt: "
            + taskAttempt.getID());
      }
    }
  }

  private static class LaunchedContainerTransition implements
      SingleArcTransition<TaskAttemptImpl, TaskAttemptEvent> {
    @Override
    public void transition(TaskAttemptImpl taskAttempt, 
        TaskAttemptEvent evnt) {

      TaskAttemptContainerLaunchedEvent event =
        (TaskAttemptContainerLaunchedEvent) evnt;

      //set the launch time
      taskAttempt.launchTime = taskAttempt.clock.getTime();
      // register it to TaskAttemptListener so that it start listening
      // for it
      taskAttempt.taskAttemptListener.register(
          taskAttempt.attemptId, taskAttempt.remoteTask, taskAttempt.jvmID);
      //TODO Resolve to host / IP in case of a local address.
      InetSocketAddress nodeHttpInetAddr =
          NetUtils.createSocketAddr(taskAttempt.nodeHttpAddress); // TODO:
                                                                  // Costly?
      JobCounterUpdateEvent jce =
          new JobCounterUpdateEvent(taskAttempt.attemptId.getTaskId()
              .getJobId());
      jce.addCounterUpdate(
          taskAttempt.attemptId.getTaskId().getTaskType() == TaskType.MAP ? 
              JobCounter.TOTAL_LAUNCHED_MAPS: JobCounter.TOTAL_LAUNCHED_REDUCES
              , 1);
      taskAttempt.eventHandler.handle(jce);
      
      TaskAttemptStartedEvent tase =
        new TaskAttemptStartedEvent(TypeConverter.fromYarn(taskAttempt.attemptId),
            TypeConverter.fromYarn(taskAttempt.attemptId.getTaskId().getTaskType()),
            taskAttempt.launchTime,
            nodeHttpInetAddr.getHostName(), nodeHttpInetAddr.getPort());
      taskAttempt.eventHandler.handle
          (new JobHistoryEvent(taskAttempt.attemptId.getTaskId().getJobId(), tase));
      taskAttempt.eventHandler.handle
          (new SpeculatorEvent
              (taskAttempt.attemptId, true, taskAttempt.clock.getTime()));
      //make remoteTask reference as null as it is no more needed
      //and free up the memory
      taskAttempt.remoteTask = null;
      taskAttempt.shufflePort = event.getShufflePort();
      
      //tell the Task that attempt has started
      taskAttempt.eventHandler.handle(new TaskTAttemptEvent(
          taskAttempt.attemptId, 
         TaskEventType.T_ATTEMPT_LAUNCHED));
    }
  }
   
  private static class CommitPendingTransition implements
      SingleArcTransition<TaskAttemptImpl, TaskAttemptEvent> {
    @Override
    public void transition(TaskAttemptImpl taskAttempt, 
        TaskAttemptEvent event) {
      taskAttempt.eventHandler.handle(new TaskTAttemptEvent(
          taskAttempt.attemptId, 
         TaskEventType.T_ATTEMPT_COMMIT_PENDING));
    }
  }

  private static class TaskCleanupTransition implements
      SingleArcTransition<TaskAttemptImpl, TaskAttemptEvent> {
    @Override
    public void transition(TaskAttemptImpl taskAttempt, 
        TaskAttemptEvent event) {
      TaskAttemptContext taskContext =
        new TaskAttemptContextImpl(new JobConf(taskAttempt.conf),
            TypeConverter.fromYarn(taskAttempt.attemptId));
      taskAttempt.eventHandler.handle(new TaskCleanupEvent(
          taskAttempt.attemptId,
          taskAttempt.committer,
          taskContext));
    }
  }

  private static class SucceededTransition implements
      SingleArcTransition<TaskAttemptImpl, TaskAttemptEvent> {
    @Override
    public void transition(TaskAttemptImpl taskAttempt, 
        TaskAttemptEvent event) {
      //set the finish time
      taskAttempt.setFinishTime();
      String taskType = 
          TypeConverter.fromYarn(taskAttempt.attemptId.getTaskId().getTaskType()).toString();
      LOG.info("In TaskAttemptImpl taskType: " + taskType);
      long slotMillis = computeSlotMillis(taskAttempt);
      JobCounterUpdateEvent jce =
          new JobCounterUpdateEvent(taskAttempt.attemptId.getTaskId()
              .getJobId());
      jce.addCounterUpdate(
        taskAttempt.attemptId.getTaskId().getTaskType() == TaskType.MAP ? 
          JobCounter.SLOTS_MILLIS_MAPS : JobCounter.SLOTS_MILLIS_REDUCES,
          slotMillis);
      taskAttempt.eventHandler.handle(jce);
      taskAttempt.logAttemptFinishedEvent(TaskAttemptState.SUCCEEDED);
      taskAttempt.eventHandler.handle(new TaskTAttemptEvent(
          taskAttempt.attemptId,
          TaskEventType.T_ATTEMPT_SUCCEEDED));
      taskAttempt.eventHandler.handle
      (new SpeculatorEvent
          (taskAttempt.reportedStatus, taskAttempt.clock.getTime()));
   }
  }

  private static class FailedTransition implements
      SingleArcTransition<TaskAttemptImpl, TaskAttemptEvent> {
    @Override
    public void transition(TaskAttemptImpl taskAttempt, TaskAttemptEvent event) {
      // set the finish time
      taskAttempt.setFinishTime();
      
      if (taskAttempt.getLaunchTime() != 0) {
        taskAttempt.eventHandler
            .handle(createJobCounterUpdateEventTAFailed(taskAttempt));
        TaskAttemptUnsuccessfulCompletionEvent tauce =
            createTaskAttemptUnsuccessfulCompletionEvent(taskAttempt,
                TaskAttemptState.FAILED);
        taskAttempt.eventHandler.handle(new JobHistoryEvent(
            taskAttempt.attemptId.getTaskId().getJobId(), tauce));
        // taskAttempt.logAttemptFinishedEvent(TaskAttemptState.FAILED); Not
        // handling failed map/reduce events.
      }else {
        LOG.debug("Not generating HistoryFinish event since start event not generated for taskAttempt: "
            + taskAttempt.getID());
      }
      taskAttempt.eventHandler.handle(new TaskTAttemptEvent(
          taskAttempt.attemptId, TaskEventType.T_ATTEMPT_FAILED));
    }
  }

  private void logAttemptFinishedEvent(TaskAttemptState state) {
    //Log finished events only if an attempt started.
    if (getLaunchTime() == 0) return; 
    if (attemptId.getTaskId().getTaskType() == TaskType.MAP) {
      MapAttemptFinishedEvent mfe =
         new MapAttemptFinishedEvent(TypeConverter.fromYarn(attemptId),
         TypeConverter.fromYarn(attemptId.getTaskId().getTaskType()),
         state.toString(),
         this.reportedStatus.mapFinishTime,
         finishTime, this.nodeHostName == null ? "UNKNOWN" : this.nodeHostName,
         this.reportedStatus.stateString,
         TypeConverter.fromYarn(getCounters()),
         getProgressSplitBlock().burst());
         eventHandler.handle(
           new JobHistoryEvent(attemptId.getTaskId().getJobId(), mfe));
    } else {
       ReduceAttemptFinishedEvent rfe =
         new ReduceAttemptFinishedEvent(TypeConverter.fromYarn(attemptId),
         TypeConverter.fromYarn(attemptId.getTaskId().getTaskType()),
         state.toString(),
         this.reportedStatus.shuffleFinishTime,
         this.reportedStatus.sortFinishTime,
         finishTime, this.containerMgrAddress == null ? "UNKNOWN" : this.containerMgrAddress,
         this.reportedStatus.stateString,
         TypeConverter.fromYarn(getCounters()),
         getProgressSplitBlock().burst());
         eventHandler.handle(
           new JobHistoryEvent(attemptId.getTaskId().getJobId(), rfe));
    }
  }

  private static class TooManyFetchFailureTransition implements
      SingleArcTransition<TaskAttemptImpl, TaskAttemptEvent> {
    @Override
    public void transition(TaskAttemptImpl taskAttempt, TaskAttemptEvent event) {
      //add to diagnostic
      taskAttempt.addDiagnosticInfo("Too Many fetch failures.Failing the attempt");
      //set the finish time
      taskAttempt.setFinishTime();
      
      if (taskAttempt.getLaunchTime() != 0) {
        taskAttempt.eventHandler
            .handle(createJobCounterUpdateEventTAFailed(taskAttempt));
        TaskAttemptUnsuccessfulCompletionEvent tauce =
            createTaskAttemptUnsuccessfulCompletionEvent(taskAttempt,
                TaskAttemptState.FAILED);
        taskAttempt.eventHandler.handle(new JobHistoryEvent(
            taskAttempt.attemptId.getTaskId().getJobId(), tauce));
      }else {
        LOG.debug("Not generating HistoryFinish event since start event not generated for taskAttempt: "
            + taskAttempt.getID());
      }
      taskAttempt.eventHandler.handle(new TaskTAttemptEvent(
          taskAttempt.attemptId, TaskEventType.T_ATTEMPT_FAILED));
    }
  }

  private static class KilledTransition implements
      SingleArcTransition<TaskAttemptImpl, TaskAttemptEvent> {

    @Override
    public void transition(TaskAttemptImpl taskAttempt,
        TaskAttemptEvent event) {
      //set the finish time
      taskAttempt.setFinishTime();
      if (taskAttempt.getLaunchTime() != 0) {
        taskAttempt.eventHandler
            .handle(createJobCounterUpdateEventTAFailed(taskAttempt));
        TaskAttemptUnsuccessfulCompletionEvent tauce =
            createTaskAttemptUnsuccessfulCompletionEvent(taskAttempt,
                TaskAttemptState.KILLED);
        taskAttempt.eventHandler.handle(new JobHistoryEvent(
            taskAttempt.attemptId.getTaskId().getJobId(), tauce));
      }else {
        LOG.debug("Not generating HistoryFinish event since start event not generated for taskAttempt: "
            + taskAttempt.getID());
      }
//      taskAttempt.logAttemptFinishedEvent(TaskAttemptState.KILLED); Not logging Map/Reduce attempts in case of failure.
      taskAttempt.eventHandler.handle(new TaskTAttemptEvent(
          taskAttempt.attemptId,
          TaskEventType.T_ATTEMPT_KILLED));
    }
  }

  private static class CleanupContainerTransition implements
       SingleArcTransition<TaskAttemptImpl, TaskAttemptEvent> {
    @Override
    public void transition(TaskAttemptImpl taskAttempt, 
        TaskAttemptEvent event) {
      // unregister it to TaskAttemptListener so that it stops listening
      // for it
      taskAttempt.taskAttemptListener.unregister(
          taskAttempt.attemptId, taskAttempt.jvmID);
      taskAttempt.reportedStatus.progress = 1.0f;
      taskAttempt.updateProgressSplits();
      //send the cleanup event to containerLauncher
      taskAttempt.eventHandler.handle(new ContainerLauncherEvent(
          taskAttempt.attemptId, 
          taskAttempt.containerID, taskAttempt.containerMgrAddress,
          taskAttempt.containerToken,
          ContainerLauncher.EventType.CONTAINER_REMOTE_CLEANUP));
    }
  }

  private void addDiagnosticInfo(String diag) {
    if (diag != null && !diag.equals("")) {
      diagnostics.add(diag);
    }
  }

  private static class StatusUpdater 
       implements SingleArcTransition<TaskAttemptImpl, TaskAttemptEvent> {
    @Override
    public void transition(TaskAttemptImpl taskAttempt, 
        TaskAttemptEvent event) {
      // Status update calls don't really change the state of the attempt.
      TaskAttemptStatus newReportedStatus =
          ((TaskAttemptStatusUpdateEvent) event)
              .getReportedTaskAttemptStatus();
      // Now switch the information in the reportedStatus
      taskAttempt.reportedStatus = newReportedStatus;
      taskAttempt.reportedStatus.taskState = taskAttempt.getState();

      // send event to speculator about the reported status
      taskAttempt.eventHandler.handle
          (new SpeculatorEvent
              (taskAttempt.reportedStatus, taskAttempt.clock.getTime()));
      
      //add to diagnostic
      taskAttempt.addDiagnosticInfo(newReportedStatus.diagnosticInfo);
      taskAttempt.updateProgressSplits();
      
      //if fetch failures are present, send the fetch failure event to job
      //this only will happen in reduce attempt type
      if (taskAttempt.reportedStatus.fetchFailedMaps != null && 
          taskAttempt.reportedStatus.fetchFailedMaps.size() > 0) {
        taskAttempt.eventHandler.handle(new JobTaskAttemptFetchFailureEvent(
            taskAttempt.attemptId, taskAttempt.reportedStatus.fetchFailedMaps));
      }
    }
  }

  private static class DiagnosticInformationUpdater 
        implements SingleArcTransition<TaskAttemptImpl, TaskAttemptEvent> {
    @Override
    public void transition(TaskAttemptImpl taskAttempt, 
        TaskAttemptEvent event) {
      TaskAttemptDiagnosticsUpdateEvent diagEvent =
          (TaskAttemptDiagnosticsUpdateEvent) event;
      LOG.info("Diagnostics report from " + taskAttempt.attemptId + ": "
          + diagEvent.getDiagnosticInfo());
      taskAttempt.addDiagnosticInfo(diagEvent.getDiagnosticInfo());
    }
  }

  private void initTaskAttemptStatus(TaskAttemptStatus result) {
    result.progress = 0.0f;
    result.diagnosticInfo = "";
    result.phase = Phase.STARTING;
    result.stateString = "NEW";
    result.taskState = TaskAttemptState.NEW;
    Counters counters = recordFactory.newRecordInstance(Counters.class);
//    counters.groups = new HashMap<String, CounterGroup>();
    result.counters = counters;
  }

}
