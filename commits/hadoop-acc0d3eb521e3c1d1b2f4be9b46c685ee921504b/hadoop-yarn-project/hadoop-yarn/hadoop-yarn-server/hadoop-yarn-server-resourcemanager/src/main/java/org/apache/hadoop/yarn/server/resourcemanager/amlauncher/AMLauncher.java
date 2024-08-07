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

package org.apache.hadoop.yarn.server.resourcemanager.amlauncher;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.PrivilegedAction;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.DataInputByteBuffer;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.ContainerManagementProtocol;
import org.apache.hadoop.yarn.api.protocolrecords.StartContainerRequest;
import org.apache.hadoop.yarn.api.protocolrecords.StopContainerRequest;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.NodeId;
import org.apache.hadoop.yarn.event.EventHandler;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.factories.RecordFactory;
import org.apache.hadoop.yarn.factory.providers.RecordFactoryProvider;
import org.apache.hadoop.yarn.ipc.YarnRPC;
import org.apache.hadoop.yarn.security.AMRMTokenIdentifier;
import org.apache.hadoop.yarn.security.ContainerTokenIdentifier;
import org.apache.hadoop.yarn.server.resourcemanager.RMContext;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.attempt.RMAppAttempt;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.attempt.RMAppAttemptEvent;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.attempt.RMAppAttemptEventType;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.attempt.event.RMAppAttemptLaunchFailedEvent;
import org.apache.hadoop.yarn.util.ConverterUtils;

/**
 * The launch of the AM itself.
 */
public class AMLauncher implements Runnable {

  private static final Log LOG = LogFactory.getLog(AMLauncher.class);

  private ContainerManagementProtocol containerMgrProxy;

  private final RMAppAttempt application;
  private final Configuration conf;
  private final RecordFactory recordFactory = 
      RecordFactoryProvider.getRecordFactory(null);
  private final AMLauncherEventType eventType;
  private final RMContext rmContext;
  private final Container masterContainer;
  
  @SuppressWarnings("rawtypes")
  private final EventHandler handler;
  
  public AMLauncher(RMContext rmContext, RMAppAttempt application,
      AMLauncherEventType eventType, Configuration conf) {
    this.application = application;
    this.conf = conf;
    this.eventType = eventType;
    this.rmContext = rmContext;
    this.handler = rmContext.getDispatcher().getEventHandler();
    this.masterContainer = application.getMasterContainer();
  }
  
  private void connect() throws IOException {
    ContainerId masterContainerID = masterContainer.getId();
    
    containerMgrProxy = getContainerMgrProxy(masterContainerID);
  }
  
  private void launch() throws IOException, YarnException {
    connect();
    ContainerId masterContainerID = masterContainer.getId();
    ApplicationSubmissionContext applicationContext =
      application.getSubmissionContext();
    LOG.info("Setting up container " + masterContainer
        + " for AM " + application.getAppAttemptId());  
    ContainerLaunchContext launchContext =
        createAMContainerLaunchContext(applicationContext, masterContainerID);
    StartContainerRequest request = 
        recordFactory.newRecordInstance(StartContainerRequest.class);
    request.setContainerLaunchContext(launchContext);
    request.setContainerToken(masterContainer.getContainerToken());
    containerMgrProxy.startContainer(request);
    LOG.info("Done launching container " + masterContainer
        + " for AM " + application.getAppAttemptId());
  }
  
  private void cleanup() throws IOException, YarnException {
    connect();
    ContainerId containerId = masterContainer.getId();
    StopContainerRequest stopRequest = 
        recordFactory.newRecordInstance(StopContainerRequest.class);
    stopRequest.setContainerId(containerId);
    containerMgrProxy.stopContainer(stopRequest);
  }

  // Protected. For tests.
  protected ContainerManagementProtocol getContainerMgrProxy(
      final ContainerId containerId) {

    final NodeId node = masterContainer.getNodeId();
    final InetSocketAddress containerManagerBindAddress =
        NetUtils.createSocketAddrForHost(node.getHost(), node.getPort());

    final YarnRPC rpc = YarnRPC.create(conf); // TODO: Don't create again and again.

    UserGroupInformation currentUser = UserGroupInformation
        .createRemoteUser(containerId.toString());
    if (UserGroupInformation.isSecurityEnabled()) {
      Token<ContainerTokenIdentifier> token =
          ConverterUtils.convertFromYarn(masterContainer
              .getContainerToken(), containerManagerBindAddress);
      currentUser.addToken(token);
    }
    return currentUser.doAs(new PrivilegedAction<ContainerManagementProtocol>() {
      @Override
      public ContainerManagementProtocol run() {
        return (ContainerManagementProtocol) rpc.getProxy(ContainerManagementProtocol.class,
            containerManagerBindAddress, conf);
      }
    });
  }

  private ContainerLaunchContext createAMContainerLaunchContext(
      ApplicationSubmissionContext applicationMasterContext,
      ContainerId containerID) throws IOException {

    // Construct the actual Container
    ContainerLaunchContext container = 
        applicationMasterContext.getAMContainerSpec();
    LOG.info("Command to launch container "
        + containerID
        + " : "
        + StringUtils.arrayToString(container.getCommands().toArray(
            new String[0])));
    
    // Finalize the container
    setupTokens(container, containerID);
    
    return container;
  }

  private void setupTokens(
      ContainerLaunchContext container, ContainerId containerID)
      throws IOException {
    Map<String, String> environment = container.getEnvironment();
    environment.put(ApplicationConstants.APPLICATION_WEB_PROXY_BASE_ENV,
        application.getWebProxyBase());
    // Set AppSubmitTime and MaxAppAttempts to be consumable by the AM.
    ApplicationId applicationId =
        application.getAppAttemptId().getApplicationId();
    environment.put(
        ApplicationConstants.APP_SUBMIT_TIME_ENV,
        String.valueOf(rmContext.getRMApps()
            .get(applicationId)
            .getSubmitTime()));
    environment.put(ApplicationConstants.MAX_APP_ATTEMPTS_ENV,
        String.valueOf(rmContext.getRMApps().get(
            applicationId).getMaxAppAttempts()));
 
    if (UserGroupInformation.isSecurityEnabled()) {
      // TODO: Security enabled/disabled info should come from RM.

      Credentials credentials = new Credentials();

      DataInputByteBuffer dibb = new DataInputByteBuffer();
      if (container.getTokens() != null) {
        // TODO: Don't do this kind of checks everywhere.
        dibb.reset(container.getTokens());
        credentials.readTokenStorageStream(dibb);
      }

      // Add application token
      Token<AMRMTokenIdentifier> amrmToken =
          application.getAMRMToken();
      if(amrmToken != null) {
        credentials.addToken(amrmToken.getService(), amrmToken);
      }
      DataOutputBuffer dob = new DataOutputBuffer();
      credentials.writeTokenStorageToStream(dob);
      container.setTokens(ByteBuffer.wrap(dob.getData(), 0,
        dob.getLength()));
    }
  }
  
  @SuppressWarnings("unchecked")
  public void run() {
    switch (eventType) {
    case LAUNCH:
      try {
        LOG.info("Launching master" + application.getAppAttemptId());
        launch();
        handler.handle(new RMAppAttemptEvent(application.getAppAttemptId(),
            RMAppAttemptEventType.LAUNCHED));
      } catch(Exception ie) {
        String message = "Error launching " + application.getAppAttemptId()
            + ". Got exception: " + StringUtils.stringifyException(ie);
        LOG.info(message);
        handler.handle(new RMAppAttemptLaunchFailedEvent(application
            .getAppAttemptId(), message));
      }
      break;
    case CLEANUP:
      try {
        LOG.info("Cleaning master " + application.getAppAttemptId());
        cleanup();
      } catch(IOException ie) {
        LOG.info("Error cleaning master ", ie);
      } catch (YarnException e) {
        LOG.info("Error cleaning master ", e);
      }
      break;
    default:
      LOG.warn("Received unknown event-type " + eventType + ". Ignoring.");
      break;
    }
  }
}
