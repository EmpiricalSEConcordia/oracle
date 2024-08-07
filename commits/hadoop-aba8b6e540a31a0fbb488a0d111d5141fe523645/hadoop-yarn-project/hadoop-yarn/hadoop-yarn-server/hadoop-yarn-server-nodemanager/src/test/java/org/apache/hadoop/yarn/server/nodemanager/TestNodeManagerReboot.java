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

package org.apache.hadoop.yarn.server.nodemanager;

import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FileContext;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.UnsupportedFileSystemException;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.yarn.api.ContainerManager;
import org.apache.hadoop.yarn.api.protocolrecords.GetContainerStatusRequest;
import org.apache.hadoop.yarn.api.protocolrecords.StartContainerRequest;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.ContainerToken;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.NodeId;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.URL;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.event.Dispatcher;
import org.apache.hadoop.yarn.exceptions.YarnRemoteException;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.Container;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.ContainerState;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.ContainerLocalizer;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.ResourceLocalizationService;
import org.apache.hadoop.yarn.util.BuilderUtils;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.hadoop.yarn.util.Records;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

public class TestNodeManagerReboot {

  static final File basedir =
      new File("target", TestNodeManagerReboot.class.getName());
  static final File logsDir = new File(basedir, "logs");
  static final File nmLocalDir = new File(basedir, "nm0");
  static final File localResourceDir = new File(basedir, "resource");

  static final String user = System.getProperty("user.name");
  private FileContext localFS;
  private MyNodeManager nm;
  private DeletionService delService;
  static final Log LOG = LogFactory.getLog(TestNodeManagerReboot.class);

  @Before
  public void setup() throws UnsupportedFileSystemException {
    localFS = FileContext.getLocalFSFileContext();
  }

  @After
  public void tearDown() throws IOException, InterruptedException {
    localFS.delete(new Path(basedir.getPath()), true);
    if (nm != null) {
      nm.stop();
    }
  }

  @Test(timeout = 2000000)
  public void testClearLocalDirWhenNodeReboot() throws IOException,
      YarnRemoteException, InterruptedException {
    nm = new MyNodeManager();
    nm.start();

    final ContainerManager containerManager = nm.getContainerManager();

    // create files under fileCache
    createFiles(nmLocalDir.getAbsolutePath(), ContainerLocalizer.FILECACHE, 100);
    localResourceDir.mkdirs();

    ContainerLaunchContext containerLaunchContext =
        Records.newRecord(ContainerLaunchContext.class);
    // Construct the Container-id
    ContainerId cId = createContainerId();

    URL localResourceUri =
        ConverterUtils.getYarnUrlFromPath(localFS
            .makeQualified(new Path(localResourceDir.getAbsolutePath())));

    LocalResource localResource =
        Records.newRecord(LocalResource.class);
    localResource.setResource(localResourceUri);
    localResource.setSize(-1);
    localResource.setVisibility(LocalResourceVisibility.APPLICATION);
    localResource.setType(LocalResourceType.FILE);
    localResource.setTimestamp(localResourceDir.lastModified());
    String destinationFile = "dest_file";
    Map<String, LocalResource> localResources =
        new HashMap<String, LocalResource>();
    localResources.put(destinationFile, localResource);
    containerLaunchContext.setLocalResources(localResources);
    List<String> commands = new ArrayList<String>();
    containerLaunchContext.setCommands(commands);
    Resource resource = Records.newRecord(Resource.class);
    resource.setMemory(1024);
    NodeId nodeId = BuilderUtils.newNodeId("127.0.0.1", 12345);
    ContainerToken containerToken =
        BuilderUtils.newContainerToken(cId, nodeId.getHost(), nodeId.getPort(),
          user, resource, System.currentTimeMillis() + 10000L, 123,
          "password".getBytes(), 0);
    
    final StartContainerRequest startRequest =
        Records.newRecord(StartContainerRequest.class);
    startRequest.setContainerLaunchContext(containerLaunchContext);
    startRequest.setContainerToken(containerToken);
    final UserGroupInformation currentUser = UserGroupInformation
        .createRemoteUser(cId.toString());
    currentUser.doAs(new PrivilegedExceptionAction<Void>() {
      @Override
      public Void run() throws YarnRemoteException, IOException {
        containerManager.startContainer(startRequest);
        return null;
      }
    });

    GetContainerStatusRequest request =
        Records.newRecord(GetContainerStatusRequest.class);
    request.setContainerId(cId);
    Container container =
        nm.getNMContext().getContainers().get(request.getContainerId());

    final int MAX_TRIES = 20;
    int numTries = 0;
    while (!container.getContainerState().equals(ContainerState.DONE)
        && numTries <= MAX_TRIES) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException ex) {
        // Do nothing
      }
      numTries++;
    }

    Assert.assertEquals(ContainerState.DONE, container.getContainerState());

    Assert.assertTrue(
        "The container should create a subDir named currentUser: " + user +
            "under localDir/usercache",
        numOfLocalDirs(nmLocalDir.getAbsolutePath(),
            ContainerLocalizer.USERCACHE) > 0);

    Assert.assertTrue("There should be files or Dirs under nm_private when " +
        "container is launched", numOfLocalDirs(nmLocalDir.getAbsolutePath(),
        ResourceLocalizationService.NM_PRIVATE_DIR) > 0);

    // restart the NodeManager
    nm.stop();
    nm = new MyNodeManager();
    nm.start();    

    numTries = 0;
    while ((numOfLocalDirs(nmLocalDir.getAbsolutePath(), ContainerLocalizer
        .USERCACHE) > 0 || numOfLocalDirs(nmLocalDir.getAbsolutePath(),
        ContainerLocalizer.FILECACHE) > 0 || numOfLocalDirs(nmLocalDir
        .getAbsolutePath(), ResourceLocalizationService.NM_PRIVATE_DIR)
        > 0) && numTries < MAX_TRIES) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException ex) {
        // Do nothing
      }
      numTries++;
    }

    Assert.assertTrue("After NM reboots, all local files should be deleted",
        numOfLocalDirs(nmLocalDir.getAbsolutePath(), ContainerLocalizer
            .USERCACHE) == 0 && numOfLocalDirs(nmLocalDir.getAbsolutePath(),
            ContainerLocalizer.FILECACHE) == 0 && numOfLocalDirs(nmLocalDir
            .getAbsolutePath(), ResourceLocalizationService.NM_PRIVATE_DIR)
              == 0);
    verify(delService, times(1)).delete(eq(user),
        argThat(new PathInclude(user)));
    verify(delService, times(1)).delete(
        (String) isNull(),
        argThat(new PathInclude(ResourceLocalizationService.NM_PRIVATE_DIR
            + "_DEL_")));
    verify(delService, times(1)).delete((String) isNull(),
        argThat(new PathInclude(ContainerLocalizer.FILECACHE + "_DEL_")));
    verify(delService, times(1)).delete((String) isNull(),
        argThat(new PathInclude(ContainerLocalizer.USERCACHE + "_DEL_")));

  }

  private int numOfLocalDirs(String localDir, String localSubDir) {
    File[] listOfFiles = new File(localDir, localSubDir).listFiles();
    if (listOfFiles == null) {
      return 0;
    } else {
      return listOfFiles.length;
    }
  }

  private void createFiles(String dir, String subDir, int numOfFiles) {
    for (int i = 0; i < numOfFiles; i++) {
      File newFile = new File(dir + "/" + subDir, "file_" + (i + 1));
      try {
        newFile.createNewFile();
      } catch (IOException e) {
        // Do nothing
      }
    }
  }

  private ContainerId createContainerId() {
    ApplicationId appId = ApplicationId.newInstance(0, 0);
    ApplicationAttemptId appAttemptId = ApplicationAttemptId.newInstance(appId, 1);
    ContainerId containerId = ContainerId.newInstance(appAttemptId, 0);
    return containerId;
  }

  private class MyNodeManager extends NodeManager {

    public MyNodeManager() {
      super();
      this.init(createNMConfig());
    }

    @Override
    protected NodeStatusUpdater createNodeStatusUpdater(Context context,
        Dispatcher dispatcher, NodeHealthCheckerService healthChecker) {
      MockNodeStatusUpdater myNodeStatusUpdater = new MockNodeStatusUpdater(
          context, dispatcher, healthChecker, metrics);
      return myNodeStatusUpdater;
    }

    @Override
    protected DeletionService createDeletionService(ContainerExecutor exec) {
      delService = spy(new DeletionService(exec));
      return delService;
    }

    private YarnConfiguration createNMConfig() {
      YarnConfiguration conf = new YarnConfiguration();
      conf.setInt(YarnConfiguration.NM_PMEM_MB, 5 * 1024); // 5GB
      conf.set(YarnConfiguration.NM_ADDRESS, "127.0.0.1:12345");
      conf.set(YarnConfiguration.NM_LOCALIZER_ADDRESS, "127.0.0.1:12346");
      conf.set(YarnConfiguration.NM_LOG_DIRS, logsDir.getAbsolutePath());
      conf.set(YarnConfiguration.NM_LOCAL_DIRS, nmLocalDir.getAbsolutePath());
      return conf;
    }
  }

  class PathInclude extends ArgumentMatcher<Path> {

    final String part;

    PathInclude(String part) {
      this.part = part;
    }

    @Override
    public boolean matches(Object o) {
      return ((Path) o).getName().indexOf(part) != -1;
    }
  }
}
