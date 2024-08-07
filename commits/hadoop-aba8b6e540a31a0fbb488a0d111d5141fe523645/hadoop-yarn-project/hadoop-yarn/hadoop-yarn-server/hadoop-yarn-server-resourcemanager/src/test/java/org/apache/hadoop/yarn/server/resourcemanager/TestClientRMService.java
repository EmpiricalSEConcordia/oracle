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

package org.apache.hadoop.yarn.server.resourcemanager;

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;

import junit.framework.Assert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.yarn.MockApps;
import org.apache.hadoop.yarn.api.ClientRMProtocol;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationReportRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationReportResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetClusterNodesRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetQueueInfoRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetQueueInfoResponse;
import org.apache.hadoop.yarn.api.protocolrecords.RenewDelegationTokenRequest;
import org.apache.hadoop.yarn.api.protocolrecords.SubmitApplicationRequest;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.DelegationToken;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.QueueInfo;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.event.Dispatcher;
import org.apache.hadoop.yarn.event.Event;
import org.apache.hadoop.yarn.event.EventHandler;
import org.apache.hadoop.yarn.exceptions.YarnRemoteException;
import org.apache.hadoop.yarn.factories.RecordFactory;
import org.apache.hadoop.yarn.factory.providers.RecordFactoryProvider;
import org.apache.hadoop.yarn.ipc.YarnRPC;
import org.apache.hadoop.yarn.security.client.RMDelegationTokenIdentifier;
import org.apache.hadoop.yarn.server.resourcemanager.recovery.NullRMStateStore;
import org.apache.hadoop.yarn.server.resourcemanager.recovery.RMStateStore;
import org.apache.hadoop.yarn.server.resourcemanager.resource.Resources;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.RMApp;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.RMAppEvent;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.RMAppImpl;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.YarnScheduler;
import org.apache.hadoop.yarn.server.resourcemanager.security.RMDelegationTokenSecretManager;
import org.apache.hadoop.yarn.server.security.ApplicationACLsManager;
import org.apache.hadoop.yarn.util.BuilderUtils;
import org.apache.hadoop.yarn.util.Records;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestClientRMService {

  private static final Log LOG = LogFactory.getLog(TestClientRMService.class);

  private RecordFactory recordFactory = RecordFactoryProvider
      .getRecordFactory(null);

  private static RMDelegationTokenSecretManager dtsm;
  
  @BeforeClass
  public static void setupSecretManager() throws IOException {
    RMContext rmContext = mock(RMContext.class);
    when(rmContext.getStateStore()).thenReturn(new NullRMStateStore());
    dtsm = new RMDelegationTokenSecretManager(60000, 60000, 60000, 60000, rmContext);
    dtsm.startThreads();  
  }

  @AfterClass
  public static void teardownSecretManager() {
    if (dtsm != null) {
      dtsm.stopThreads();
    }
  }
  
  @Test
  public void testGetClusterNodes() throws Exception {
    MockRM rm = new MockRM() {
      protected ClientRMService createClientRMService() {
        return new ClientRMService(this.rmContext, scheduler,
          this.rmAppManager, this.applicationACLsManager,
          this.rmDTSecretManager);
      };
    };
    rm.start();

    // Add a healthy node
    MockNM node = rm.registerNode("host:1234", 1024);
    node.nodeHeartbeat(true);

    // Create a client.
    Configuration conf = new Configuration();
    YarnRPC rpc = YarnRPC.create(conf);
    InetSocketAddress rmAddress = rm.getClientRMService().getBindAddress();
    LOG.info("Connecting to ResourceManager at " + rmAddress);
    ClientRMProtocol client =
        (ClientRMProtocol) rpc
          .getProxy(ClientRMProtocol.class, rmAddress, conf);

    // Make call
    GetClusterNodesRequest request =
        Records.newRecord(GetClusterNodesRequest.class);
    List<NodeReport> nodeReports =
        client.getClusterNodes(request).getNodeReports();
    Assert.assertEquals(1, nodeReports.size());
    Assert.assertTrue("Node is expected to be healthy!", nodeReports.get(0)
      .getNodeHealthStatus().getIsNodeHealthy());

    // Now make the node unhealthy.
    node.nodeHeartbeat(false);

    // Call again
    nodeReports = client.getClusterNodes(request).getNodeReports();
    Assert.assertEquals(1, nodeReports.size());
    Assert.assertFalse("Node is expected to be unhealthy!", nodeReports.get(0)
      .getNodeHealthStatus().getIsNodeHealthy());
  }
  
  @Test
  public void testGetApplicationReport() throws YarnRemoteException {
    RMContext rmContext = mock(RMContext.class);
    when(rmContext.getRMApps()).thenReturn(
        new ConcurrentHashMap<ApplicationId, RMApp>());
    ClientRMService rmService = new ClientRMService(rmContext, null, null,
        null, null);
    RecordFactory recordFactory = RecordFactoryProvider.getRecordFactory(null);
    GetApplicationReportRequest request = recordFactory
        .newRecordInstance(GetApplicationReportRequest.class);
    request.setApplicationId(ApplicationId.newInstance(0, 0));
    GetApplicationReportResponse applicationReport = rmService
        .getApplicationReport(request);
    Assert.assertNull("It should return null as application report for absent application.",
        applicationReport.getApplicationReport());
  }
  
  @Test
  public void testGetQueueInfo() throws Exception {
    YarnScheduler yarnScheduler = mock(YarnScheduler.class);
    RMContext rmContext = mock(RMContext.class);
    mockRMContext(yarnScheduler, rmContext);
    ClientRMService rmService = new ClientRMService(rmContext, yarnScheduler,
        null, null, null);
    GetQueueInfoRequest request = recordFactory
        .newRecordInstance(GetQueueInfoRequest.class);
    request.setQueueName("testqueue");
    request.setIncludeApplications(true);
    GetQueueInfoResponse queueInfo = rmService.getQueueInfo(request);
    List<ApplicationReport> applications = queueInfo.getQueueInfo()
        .getApplications();
    Assert.assertEquals(2, applications.size());
    request.setQueueName("nonexistentqueue");
    request.setIncludeApplications(true);
    // should not throw exception on nonexistent queue
    queueInfo = rmService.getQueueInfo(request);
  }

  private static final UserGroupInformation owner =
      UserGroupInformation.createRemoteUser("owner");
  private static final UserGroupInformation other =
      UserGroupInformation.createRemoteUser("other");
  
  @Test
  public void testTokenRenewalByOwner() throws Exception {
    owner.doAs(new PrivilegedExceptionAction<Void>() {
      @Override
      public Void run() throws Exception {
        checkTokenRenewal(owner, owner);
        return null;
      }
    });
  }
  
  @Test
  public void testTokenRenewalWrongUser() throws Exception {
    try {
      owner.doAs(new PrivilegedExceptionAction<Void>() {
        @Override
        public Void run() throws Exception {
          try {
            checkTokenRenewal(owner, other);
            return null;
          } catch (YarnRemoteException ex) {
            Assert.assertTrue(ex.getMessage().contains(
                "Client " + owner.getUserName() +
                " tries to renew a token with renewer specified as " +
                other.getUserName()));
            throw ex;
          }
        }
      });
    } catch (Exception e) {
      return;
    }
    Assert.fail("renew should have failed");
  }

  @Test
  public void testTokenRenewalByLoginUser() throws Exception {
    UserGroupInformation.getLoginUser().doAs(new PrivilegedExceptionAction<Void>() {
      @Override
      public Void run() throws Exception {
        checkTokenRenewal(owner, owner);
        checkTokenRenewal(owner, other);
        return null;
      }
    });
  }

  private void checkTokenRenewal(UserGroupInformation owner,
      UserGroupInformation renewer) throws IOException, YarnRemoteException {
    RMDelegationTokenIdentifier tokenIdentifier =
        new RMDelegationTokenIdentifier(
            new Text(owner.getUserName()), new Text(renewer.getUserName()), null);
    Token<?> token =
        new Token<RMDelegationTokenIdentifier>(tokenIdentifier, dtsm);
    DelegationToken dToken = BuilderUtils.newDelegationToken(
        token.getIdentifier(), token.getKind().toString(),
        token.getPassword(), token.getService().toString());
    RenewDelegationTokenRequest request =
        Records.newRecord(RenewDelegationTokenRequest.class);
    request.setDelegationToken(dToken);

    RMContext rmContext = mock(RMContext.class);
    ClientRMService rmService = new ClientRMService(
        rmContext, null, null, null, dtsm);
    rmService.renewDelegationToken(request);
  }

  @Test (timeout = 30000)
  @SuppressWarnings ("rawtypes")
  public void testAppSubmit() throws Exception {
    YarnScheduler yarnScheduler = mockYarnScheduler();
    RMContext rmContext = mock(RMContext.class);
    mockRMContext(yarnScheduler, rmContext);
    RMStateStore stateStore = mock(RMStateStore.class);
    when(rmContext.getStateStore()).thenReturn(stateStore);
    RMAppManager appManager = new RMAppManager(rmContext, yarnScheduler,
        null, mock(ApplicationACLsManager.class), new Configuration());
    when(rmContext.getDispatcher().getEventHandler()).thenReturn(
        new EventHandler<Event>() {
          public void handle(Event event) {}
        });
    ClientRMService rmService =
        new ClientRMService(rmContext, yarnScheduler, appManager, null, null);

    // without name and queue
    ApplicationId appId1 = getApplicationId(100);
    SubmitApplicationRequest submitRequest1 = mockSubmitAppRequest(
        appId1, null, null);
    try {
      rmService.submitApplication(submitRequest1);
    } catch (YarnRemoteException e) {
      Assert.fail("Exception is not expected.");
    }
    RMApp app1 = rmContext.getRMApps().get(appId1);
    Assert.assertNotNull("app doesn't exist", app1);
    Assert.assertEquals("app name doesn't match",
        YarnConfiguration.DEFAULT_APPLICATION_NAME, app1.getName());
    Assert.assertEquals("app queue doesn't match",
        YarnConfiguration.DEFAULT_QUEUE_NAME, app1.getQueue());

    // with name and queue
    String name = MockApps.newAppName();
    String queue = MockApps.newQueue();
    ApplicationId appId2 = getApplicationId(101);
    SubmitApplicationRequest submitRequest2 = mockSubmitAppRequest(
        appId2, name, queue);
    try {
      rmService.submitApplication(submitRequest2);
    } catch (YarnRemoteException e) {
      Assert.fail("Exception is not expected.");
    }
    RMApp app2 = rmContext.getRMApps().get(appId2);
    Assert.assertNotNull("app doesn't exist", app2);
    Assert.assertEquals("app name doesn't match", name, app2.getName());
    Assert.assertEquals("app queue doesn't match", queue, app2.getQueue());

    // duplicate appId
    try {
      rmService.submitApplication(submitRequest2);
      Assert.fail("Exception is expected.");
    } catch (YarnRemoteException e) {
      Assert.assertTrue("The thrown exception is not expected.",
          e.getMessage().contains("Cannot add a duplicate!"));
    }
  }
  
  @Test(timeout=4000)
  public void testConcurrentAppSubmit()
      throws IOException, InterruptedException, BrokenBarrierException,
      YarnRemoteException {
    YarnScheduler yarnScheduler = mockYarnScheduler();
    RMContext rmContext = mock(RMContext.class);
    mockRMContext(yarnScheduler, rmContext);
    RMStateStore stateStore = mock(RMStateStore.class);
    when(rmContext.getStateStore()).thenReturn(stateStore);
    RMAppManager appManager = new RMAppManager(rmContext, yarnScheduler,
        null, mock(ApplicationACLsManager.class), new Configuration());

    final ApplicationId appId1 = getApplicationId(100);
    final ApplicationId appId2 = getApplicationId(101);
    final SubmitApplicationRequest submitRequest1 = mockSubmitAppRequest(
        appId1, null, null);
    final SubmitApplicationRequest submitRequest2 = mockSubmitAppRequest(
        appId2, null, null);
    
    final CyclicBarrier startBarrier = new CyclicBarrier(2);
    final CyclicBarrier endBarrier = new CyclicBarrier(2);

    @SuppressWarnings("rawtypes")
    EventHandler eventHandler = new EventHandler() {
      @Override
      public void handle(Event rawEvent) {
        if (rawEvent instanceof RMAppEvent) {
          RMAppEvent event = (RMAppEvent) rawEvent;
          if (event.getApplicationId().equals(appId1)) {
            try {
              startBarrier.await();
              endBarrier.await();
            } catch (BrokenBarrierException e) {
              LOG.warn("Broken Barrier", e);
            } catch (InterruptedException e) {
              LOG.warn("Interrupted while awaiting barriers", e);
            }
          }
        }
      }
    };

    when(rmContext.getDispatcher().getEventHandler()).thenReturn(eventHandler);
      
    final ClientRMService rmService =
        new ClientRMService(rmContext, yarnScheduler, appManager, null, null);

    // submit an app and wait for it to block while in app submission
    Thread t = new Thread() {
      @Override
      public void run() {
        try {
          rmService.submitApplication(submitRequest1);
        } catch (YarnRemoteException e) {}
      }
    };
    t.start();
    
    // submit another app, so go through while the first app is blocked
    startBarrier.await();
    rmService.submitApplication(submitRequest2);
    endBarrier.await();
    t.join();
  }

  private SubmitApplicationRequest mockSubmitAppRequest(ApplicationId appId,
      String name, String queue) {
    ContainerLaunchContext amContainerSpec = mock(ContainerLaunchContext.class);

    Resource resource = Resources.createResource(
        YarnConfiguration.DEFAULT_RM_SCHEDULER_MINIMUM_ALLOCATION_MB);

    ApplicationSubmissionContext submissionContext =
        recordFactory.newRecordInstance(ApplicationSubmissionContext.class);
    submissionContext.setAMContainerSpec(amContainerSpec);
    submissionContext.setApplicationName(name);
    submissionContext.setQueue(queue);
    submissionContext.setApplicationId(appId);
    submissionContext.setResource(resource);

   SubmitApplicationRequest submitRequest =
       recordFactory.newRecordInstance(SubmitApplicationRequest.class);
   submitRequest.setApplicationSubmissionContext(submissionContext);
   return submitRequest;
  }

  private void mockRMContext(YarnScheduler yarnScheduler, RMContext rmContext)
      throws IOException {
    Dispatcher dispatcher = mock(Dispatcher.class);
    when(rmContext.getDispatcher()).thenReturn(dispatcher);
    QueueInfo queInfo = recordFactory.newRecordInstance(QueueInfo.class);
    queInfo.setQueueName("testqueue");
    when(yarnScheduler.getQueueInfo(eq("testqueue"), anyBoolean(), anyBoolean()))
        .thenReturn(queInfo);
    when(yarnScheduler.getQueueInfo(eq("nonexistentqueue"), anyBoolean(), anyBoolean()))
        .thenThrow(new IOException("queue does not exist"));
    ConcurrentHashMap<ApplicationId, RMApp> apps = getRMApps(rmContext,
        yarnScheduler);
    when(rmContext.getRMApps()).thenReturn(apps);
  }

  private ConcurrentHashMap<ApplicationId, RMApp> getRMApps(
      RMContext rmContext, YarnScheduler yarnScheduler) {
    ConcurrentHashMap<ApplicationId, RMApp> apps = 
      new ConcurrentHashMap<ApplicationId, RMApp>();
    ApplicationId applicationId1 = getApplicationId(1);
    ApplicationId applicationId2 = getApplicationId(2);
    ApplicationId applicationId3 = getApplicationId(3);
    YarnConfiguration config = new YarnConfiguration();
    apps.put(applicationId1, getRMApp(rmContext, yarnScheduler, applicationId1,
        config, "testqueue"));
    apps.put(applicationId2, getRMApp(rmContext, yarnScheduler, applicationId2,
        config, "a"));
    apps.put(applicationId3, getRMApp(rmContext, yarnScheduler, applicationId3,
        config, "testqueue"));
    return apps;
  }

  private ApplicationId getApplicationId(int id) {
    return ApplicationId.newInstance(123456, id);
  }

  private RMAppImpl getRMApp(RMContext rmContext, YarnScheduler yarnScheduler,
      ApplicationId applicationId3, YarnConfiguration config, String queueName) {
    ApplicationSubmissionContext asContext = mock(ApplicationSubmissionContext.class);
    when(asContext.getMaxAppAttempts()).thenReturn(1);
    return new RMAppImpl(applicationId3, rmContext, config, null, null,
        queueName, asContext, yarnScheduler, null , System
            .currentTimeMillis(), "YARN");
  }

  private static YarnScheduler mockYarnScheduler() {
    YarnScheduler yarnScheduler = mock(YarnScheduler.class);
    when(yarnScheduler.getMinimumResourceCapability()).thenReturn(
        Resources.createResource(
            YarnConfiguration.DEFAULT_RM_SCHEDULER_MINIMUM_ALLOCATION_MB));
    when(yarnScheduler.getMaximumResourceCapability()).thenReturn(
        Resources.createResource(
            YarnConfiguration.DEFAULT_RM_SCHEDULER_MAXIMUM_ALLOCATION_MB));
    return yarnScheduler;
  }
}
