/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.snapshots;

import com.carrotsearch.randomizedtesting.LifecycleScope;
import com.google.common.collect.ImmutableList;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryResponse;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotResponse;
import org.elasticsearch.action.admin.cluster.snapshots.delete.DeleteSnapshotResponse;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotResponse;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.routing.allocation.decider.FilterAllocationDecider;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.indices.InvalidIndexNameException;
import org.elasticsearch.snapshots.mockstore.MockRepositoryModule;
import org.elasticsearch.test.junit.annotations.TestLogging;
import org.elasticsearch.test.store.MockDirectoryHelper;
import org.junit.Test;

import java.io.File;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.*;

/**
 */
public class SharedClusterSnapshotRestoreTests extends AbstractSnapshotTests {


    @Override
    public Settings indexSettings() {
        // During restore we frequently restore index to exactly the same state it was before, that might cause the same
        // checksum file to be written twice during restore operation
        return ImmutableSettings.builder().put(super.indexSettings())
                .put(MockDirectoryHelper.RANDOM_PREVENT_DOUBLE_WRITE, false)
                .put(MockDirectoryHelper.RANDOM_NO_DELETE_OPEN_FILE, false) //TODO: Ask Simon if this is hiding an issue
                .build();
    }

    @Test
    public void basicWorkFlowTest() throws Exception {
        Client client = client();

        logger.info("-->  creating repository");
        PutRepositoryResponse putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType("fs").setSettings(ImmutableSettings.settingsBuilder()
                        .put("location", newTempDir(LifecycleScope.SUITE))
                        .put("compress", randomBoolean())
                        .put("chunk_size", randomIntBetween(100, 1000))
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        createIndex("test-idx-1", "test-idx-2", "test-idx-3");
        ensureGreen();

        logger.info("--> indexing some data");
        for (int i = 0; i < 100; i++) {
            index("test-idx-1", "doc", Integer.toString(i), "foo", "bar" + i);
            index("test-idx-2", "doc", Integer.toString(i), "foo", "baz" + i);
            index("test-idx-3", "doc", Integer.toString(i), "foo", "baz" + i);
        }
        refresh();
        assertThat(client.prepareCount("test-idx-1").get().getCount(), equalTo(100L));
        assertThat(client.prepareCount("test-idx-2").get().getCount(), equalTo(100L));
        assertThat(client.prepareCount("test-idx-3").get().getCount(), equalTo(100L));

        logger.info("--> snapshot");
        CreateSnapshotResponse createSnapshotResponse = client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap").setWaitForCompletion(true).setIndices("test-idx-*", "-test-idx-3").get();
        assertThat(createSnapshotResponse.getSnapshotInfo().successfulShards(), greaterThan(0));
        assertThat(createSnapshotResponse.getSnapshotInfo().successfulShards(), equalTo(createSnapshotResponse.getSnapshotInfo().totalShards()));

        assertThat(client.admin().cluster().prepareGetSnapshots("test-repo").setSnapshots("test-snap").get().getSnapshots().get(0).state(), equalTo(SnapshotState.SUCCESS));

        logger.info("--> delete some data");
        for (int i = 0; i < 50; i++) {
            client.prepareDelete("test-idx-1", "doc", Integer.toString(i)).get();
        }
        for (int i = 50; i < 100; i++) {
            client.prepareDelete("test-idx-2", "doc", Integer.toString(i)).get();
        }
        for (int i = 0; i < 100; i += 2) {
            client.prepareDelete("test-idx-3", "doc", Integer.toString(i)).get();
        }
        refresh();
        assertThat(client.prepareCount("test-idx-1").get().getCount(), equalTo(50L));
        assertThat(client.prepareCount("test-idx-2").get().getCount(), equalTo(50L));
        assertThat(client.prepareCount("test-idx-3").get().getCount(), equalTo(50L));

        logger.info("--> close indices");
        client.admin().indices().prepareClose("test-idx-1", "test-idx-2").get();

        logger.info("--> restore all indices from the snapshot");
        RestoreSnapshotResponse restoreSnapshotResponse = client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap").setWaitForCompletion(true).execute().actionGet();
        assertThat(restoreSnapshotResponse.getRestoreInfo().totalShards(), greaterThan(0));

        ensureGreen();
        assertThat(client.prepareCount("test-idx-1").get().getCount(), equalTo(100L));
        assertThat(client.prepareCount("test-idx-2").get().getCount(), equalTo(100L));
        assertThat(client.prepareCount("test-idx-3").get().getCount(), equalTo(50L));

        // Test restore after index deletion
        logger.info("--> delete indices");
        wipeIndices("test-idx-1", "test-idx-2");
        logger.info("--> restore one index after deletion");
        restoreSnapshotResponse = client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap").setWaitForCompletion(true).setIndices("test-idx-*", "-test-idx-2").execute().actionGet();
        assertThat(restoreSnapshotResponse.getRestoreInfo().totalShards(), greaterThan(0));
        ensureGreen();
        assertThat(client.prepareCount("test-idx-1").get().getCount(), equalTo(100L));
        ClusterState clusterState = client.admin().cluster().prepareState().get().getState();
        assertThat(clusterState.getMetaData().hasIndex("test-idx-1"), equalTo(true));
        assertThat(clusterState.getMetaData().hasIndex("test-idx-2"), equalTo(false));
    }

    @Test
    public void emptySnapshotTest() throws Exception {
        Client client = client();

        logger.info("-->  creating repository");
        PutRepositoryResponse putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType("fs").setSettings(ImmutableSettings.settingsBuilder().put("location", newTempDir())).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        logger.info("--> snapshot");
        CreateSnapshotResponse createSnapshotResponse = client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap").setWaitForCompletion(true).get();
        assertThat(createSnapshotResponse.getSnapshotInfo().totalShards(), equalTo(0));
        assertThat(createSnapshotResponse.getSnapshotInfo().successfulShards(), equalTo(0));

        assertThat(client.admin().cluster().prepareGetSnapshots("test-repo").setSnapshots("test-snap").get().getSnapshots().get(0).state(), equalTo(SnapshotState.SUCCESS));
    }

    @Test
    public void restoreTemplatesTest() throws Exception {
        Client client = client();

        logger.info("-->  creating repository");
        PutRepositoryResponse putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType("fs").setSettings(ImmutableSettings.settingsBuilder().put("location", newTempDir())).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        logger.info("-->  creating test template");
        assertThat(client.admin().indices().preparePutTemplate("test-template").setTemplate("te*").addMapping("test-mapping", "{}").get().isAcknowledged(), equalTo(true));

        logger.info("--> snapshot");
        CreateSnapshotResponse createSnapshotResponse = client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap").setIndices().setWaitForCompletion(true).get();
        assertThat(createSnapshotResponse.getSnapshotInfo().totalShards(), equalTo(0));
        assertThat(createSnapshotResponse.getSnapshotInfo().successfulShards(), equalTo(0));
        assertThat(client.admin().cluster().prepareGetSnapshots("test-repo").setSnapshots("test-snap").get().getSnapshots().get(0).state(), equalTo(SnapshotState.SUCCESS));

        logger.info("-->  delete test template");
        assertThat(client.admin().indices().prepareDeleteTemplate("test-template").get().isAcknowledged(), equalTo(true));
        ClusterStateResponse clusterStateResponse = client.admin().cluster().prepareState().setFilterRoutingTable(true).setFilterNodes(true).setFilterIndexTemplates("test-template").setFilterIndices().get();
        assertThat(clusterStateResponse.getState().getMetaData().templates().containsKey("test-template"), equalTo(false));

        logger.info("--> restore cluster state");
        RestoreSnapshotResponse restoreSnapshotResponse = client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap").setWaitForCompletion(true).setRestoreGlobalState(true).execute().actionGet();
        // We don't restore any indices here
        assertThat(restoreSnapshotResponse.getRestoreInfo().totalShards(), equalTo(0));

        logger.info("--> check that template is restored");
        clusterStateResponse = client.admin().cluster().prepareState().setFilterRoutingTable(true).setFilterNodes(true).setFilterIndexTemplates("test-template").setFilterIndices().get();
        assertThat(clusterStateResponse.getState().getMetaData().templates().containsKey("test-template"), equalTo(true));
    }

    @Test
    public void includeGlobalStateTest() throws Exception {
        Client client = client();

        logger.info("-->  creating repository");
        File location = newTempDir();
        PutRepositoryResponse putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType("fs").setSettings(ImmutableSettings.settingsBuilder().put("location", location)).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        logger.info("-->  creating test template");
        assertThat(client.admin().indices().preparePutTemplate("test-template").setTemplate("te*").addMapping("test-mapping", "{}").get().isAcknowledged(), equalTo(true));

        logger.info("--> snapshot without global state");
        CreateSnapshotResponse createSnapshotResponse = client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap-no-global-state").setIndices().setIncludeGlobalState(false).setWaitForCompletion(true).get();
        assertThat(createSnapshotResponse.getSnapshotInfo().totalShards(), equalTo(0));
        assertThat(createSnapshotResponse.getSnapshotInfo().successfulShards(), equalTo(0));
        assertThat(client.admin().cluster().prepareGetSnapshots("test-repo").setSnapshots("test-snap-no-global-state").get().getSnapshots().get(0).state(), equalTo(SnapshotState.SUCCESS));

        logger.info("--> snapshot with global state");
        createSnapshotResponse = client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap-with-global-state").setIndices().setIncludeGlobalState(true).setWaitForCompletion(true).get();
        assertThat(createSnapshotResponse.getSnapshotInfo().totalShards(), equalTo(0));
        assertThat(createSnapshotResponse.getSnapshotInfo().successfulShards(), equalTo(0));
        assertThat(client.admin().cluster().prepareGetSnapshots("test-repo").setSnapshots("test-snap-with-global-state").get().getSnapshots().get(0).state(), equalTo(SnapshotState.SUCCESS));

        logger.info("-->  delete test template");
        wipeTemplates("test-template");
        ClusterStateResponse clusterStateResponse = client.admin().cluster().prepareState().setFilterRoutingTable(true).setFilterNodes(true).setFilterIndexTemplates("test-template").setFilterIndices().get();
        assertThat(clusterStateResponse.getState().getMetaData().templates().containsKey("test-template"), equalTo(false));

        logger.info("--> try restoring cluster state from snapshot without global state");
        RestoreSnapshotResponse restoreSnapshotResponse = client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap-no-global-state").setWaitForCompletion(true).setRestoreGlobalState(true).execute().actionGet();
        assertThat(restoreSnapshotResponse.getRestoreInfo().totalShards(), equalTo(0));

        logger.info("--> check that template wasn't restored");
        clusterStateResponse = client.admin().cluster().prepareState().setFilterRoutingTable(true).setFilterNodes(true).setFilterIndexTemplates("test-template").setFilterIndices().get();
        assertThat(clusterStateResponse.getState().getMetaData().templates().containsKey("test-template"), equalTo(false));

        logger.info("--> restore cluster state");
        restoreSnapshotResponse = client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap-with-global-state").setWaitForCompletion(true).setRestoreGlobalState(true).execute().actionGet();
        assertThat(restoreSnapshotResponse.getRestoreInfo().totalShards(), equalTo(0));

        logger.info("--> check that template is restored");
        clusterStateResponse = client.admin().cluster().prepareState().setFilterRoutingTable(true).setFilterNodes(true).setFilterIndexTemplates("test-template").setFilterIndices().get();
        assertThat(clusterStateResponse.getState().getMetaData().templates().containsKey("test-template"), equalTo(true));

        createIndex("test-idx");
        ensureGreen();

        logger.info("--> indexing some data");
        for (int i = 0; i < 100; i++) {
            index("test-idx", "doc", Integer.toString(i), "foo", "bar" + i);
        }
        refresh();
        assertThat(client.prepareCount("test-idx").get().getCount(), equalTo(100L));

        logger.info("--> snapshot without global state but with indices");
        createSnapshotResponse = client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap-no-global-state-with-index").setIndices("test-idx").setIncludeGlobalState(false).setWaitForCompletion(true).get();
        assertThat(createSnapshotResponse.getSnapshotInfo().totalShards(), greaterThan(0));
        assertThat(createSnapshotResponse.getSnapshotInfo().successfulShards(), equalTo(createSnapshotResponse.getSnapshotInfo().totalShards()));
        assertThat(client.admin().cluster().prepareGetSnapshots("test-repo").setSnapshots("test-snap-no-global-state-with-index").get().getSnapshots().get(0).state(), equalTo(SnapshotState.SUCCESS));

        logger.info("-->  delete test template and index ");
        wipeIndices("test-idx");
        wipeTemplates("test-template");
        clusterStateResponse = client.admin().cluster().prepareState().setFilterRoutingTable(true).setFilterNodes(true).setFilterIndexTemplates("test-template").setFilterIndices().get();
        assertThat(clusterStateResponse.getState().getMetaData().templates().containsKey("test-template"), equalTo(false));

        logger.info("--> try restoring index and cluster state from snapshot without global state");
        restoreSnapshotResponse = client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap-no-global-state-with-index").setWaitForCompletion(true).setRestoreGlobalState(true).execute().actionGet();
        assertThat(restoreSnapshotResponse.getRestoreInfo().totalShards(), greaterThan(0));
        assertThat(restoreSnapshotResponse.getRestoreInfo().failedShards(), equalTo(0));

        ensureGreen();
        logger.info("--> check that template wasn't restored but index was");
        clusterStateResponse = client.admin().cluster().prepareState().setFilterRoutingTable(true).setFilterNodes(true).setFilterIndexTemplates("test-template").setFilterIndices().get();
        assertThat(clusterStateResponse.getState().getMetaData().templates().containsKey("test-template"), equalTo(false));
        assertThat(client.prepareCount("test-idx").get().getCount(), equalTo(100L));

    }

    @Test
    public void snapshotFileFailureDuringSnapshotTest() throws Exception {
        Client client = client();

        logger.info("-->  creating repository");
        PutRepositoryResponse putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType(MockRepositoryModule.class.getCanonicalName()).setSettings(
                        ImmutableSettings.settingsBuilder()
                                .put("location", newTempDir(LifecycleScope.TEST))
                                .put("random", randomAsciiOfLength(10))
                                .put("random_control_io_exception_rate", 0.2)
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        createIndex("test-idx");
        ensureGreen();

        logger.info("--> indexing some data");
        for (int i = 0; i < 100; i++) {
            index("test-idx", "doc", Integer.toString(i), "foo", "bar" + i);
        }
        refresh();
        assertThat(client.prepareCount("test-idx").get().getCount(), equalTo(100L));

        logger.info("--> snapshot");
        try {
            CreateSnapshotResponse createSnapshotResponse = client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap").setWaitForCompletion(true).setIndices("test-idx").get();
            if (createSnapshotResponse.getSnapshotInfo().totalShards() == createSnapshotResponse.getSnapshotInfo().successfulShards()) {
                // If we are here, that means we didn't have any failures, let's check it
                assertThat(getFailureCount("test-repo"), equalTo(0L));
            } else {
                assertThat(getFailureCount("test-repo"), greaterThan(0L));
                assertThat(createSnapshotResponse.getSnapshotInfo().shardFailures().size(), greaterThan(0));
                for (SnapshotShardFailure shardFailure : createSnapshotResponse.getSnapshotInfo().shardFailures()) {
                    assertThat(shardFailure.reason(), containsString("Random IOException"));
                    assertThat(shardFailure.nodeId(), notNullValue());
                    assertThat(shardFailure.index(), equalTo("test-idx"));
                }
                GetSnapshotsResponse getSnapshotsResponse = client.admin().cluster().prepareGetSnapshots("test-repo").addSnapshots("test-snap").get();
                assertThat(getSnapshotsResponse.getSnapshots().size(), equalTo(1));
                SnapshotInfo snapshotInfo = getSnapshotsResponse.getSnapshots().get(0);
                if (snapshotInfo.state() == SnapshotState.SUCCESS) {
                    assertThat(snapshotInfo.shardFailures().size(), greaterThan(0));
                    assertThat(snapshotInfo.totalShards(), greaterThan(snapshotInfo.successfulShards()));
                }
            }
        } catch (Exception ex) {
            assertThat(getFailureCount("test-repo"), greaterThan(0L));
            assertThat(ExceptionsHelper.detailedMessage(ex), containsString("IOException"));
        }
    }

    @Test
    public void dataFileFailureDuringSnapshotTest() throws Exception {
        Client client = client();
        logger.info("-->  creating repository");
        PutRepositoryResponse putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType(MockRepositoryModule.class.getCanonicalName()).setSettings(
                        ImmutableSettings.settingsBuilder()
                                .put("location", newTempDir(LifecycleScope.TEST))
                                .put("random", randomAsciiOfLength(10))
                                .put("random_data_file_io_exception_rate", 0.1)
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        createIndex("test-idx");
        ensureGreen();

        logger.info("--> indexing some data");
        for (int i = 0; i < 100; i++) {
            index("test-idx", "doc", Integer.toString(i), "foo", "bar" + i);
        }
        refresh();
        assertThat(client.prepareCount("test-idx").get().getCount(), equalTo(100L));

        logger.info("--> snapshot");
        CreateSnapshotResponse createSnapshotResponse = client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap").setWaitForCompletion(true).setIndices("test-idx").get();
        if (createSnapshotResponse.getSnapshotInfo().totalShards() == createSnapshotResponse.getSnapshotInfo().successfulShards()) {
            // If we are here, that means we didn't have any failures, let's check it
            assertThat(getFailureCount("test-repo"), equalTo(0L));
        } else {
            assertThat(getFailureCount("test-repo"), greaterThan(0L));
            assertThat(createSnapshotResponse.getSnapshotInfo().shardFailures().size(), greaterThan(0));
            for (SnapshotShardFailure shardFailure : createSnapshotResponse.getSnapshotInfo().shardFailures()) {
                assertThat(shardFailure.nodeId(), notNullValue());
                assertThat(shardFailure.index(), equalTo("test-idx"));
            }
            GetSnapshotsResponse getSnapshotsResponse = client.admin().cluster().prepareGetSnapshots("test-repo").addSnapshots("test-snap").get();
            assertThat(getSnapshotsResponse.getSnapshots().size(), equalTo(1));
            SnapshotInfo snapshotInfo = getSnapshotsResponse.getSnapshots().get(0);
            assertThat(snapshotInfo.shardFailures().size(), greaterThan(0));
            assertThat(snapshotInfo.totalShards(), greaterThan(snapshotInfo.successfulShards()));
        }

    }

    @Test
    public void dataFileFailureDuringRestoreTest() throws Exception {
        File repositoryLocation = newTempDir(LifecycleScope.TEST);
        Client client = client();
        logger.info("-->  creating repository");
        PutRepositoryResponse putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType("fs").setSettings(ImmutableSettings.settingsBuilder().put("location", repositoryLocation)).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        createIndex("test-idx");
        ensureGreen();

        logger.info("--> indexing some data");
        for (int i = 0; i < 100; i++) {
            index("test-idx", "doc", Integer.toString(i), "foo", "bar" + i);
        }
        refresh();
        assertThat(client.prepareCount("test-idx").get().getCount(), equalTo(100L));

        logger.info("--> snapshot");
        CreateSnapshotResponse createSnapshotResponse = client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap").setWaitForCompletion(true).setIndices("test-idx").get();
        assertThat(createSnapshotResponse.getSnapshotInfo().state(), equalTo(SnapshotState.SUCCESS));
        assertThat(createSnapshotResponse.getSnapshotInfo().totalShards(), equalTo(createSnapshotResponse.getSnapshotInfo().successfulShards()));

        logger.info("-->  update repository with mock version");
        putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType(MockRepositoryModule.class.getCanonicalName()).setSettings(
                        ImmutableSettings.settingsBuilder()
                                .put("location", repositoryLocation)
                                .put("random", randomAsciiOfLength(10))
                                .put("random_data_file_io_exception_rate", 0.3)
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        // Test restore after index deletion
        logger.info("--> delete index");
        wipeIndices("test-idx");
        logger.info("--> restore index after deletion");
        RestoreSnapshotResponse restoreSnapshotResponse = client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap").setWaitForCompletion(true).execute().actionGet();
        assertThat(restoreSnapshotResponse.getRestoreInfo().totalShards(), greaterThan(0));
        ensureGreen();
        CountResponse countResponse = client.prepareCount("test-idx").get();
        assertThat(countResponse.getCount(), equalTo(100L));
    }


    @Test
    @TestLogging("snapshots:TRACE")
    public void deletionOfFailingToRecoverIndexShouldStopRestore() throws Exception {
        File repositoryLocation = newTempDir(LifecycleScope.TEST);
        Client client = client();
        logger.info("-->  creating repository");
        PutRepositoryResponse putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType("fs").setSettings(ImmutableSettings.settingsBuilder().put("location", repositoryLocation)).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        createIndex("test-idx");
        ensureGreen();

        logger.info("--> indexing some data");
        for (int i = 0; i < 100; i++) {
            index("test-idx", "doc", Integer.toString(i), "foo", "bar" + i);
        }
        refresh();
        assertThat(client.prepareCount("test-idx").get().getCount(), equalTo(100L));

        logger.info("--> snapshot");
        CreateSnapshotResponse createSnapshotResponse = client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap").setWaitForCompletion(true).setIndices("test-idx").get();
        assertThat(createSnapshotResponse.getSnapshotInfo().state(), equalTo(SnapshotState.SUCCESS));
        assertThat(createSnapshotResponse.getSnapshotInfo().totalShards(), equalTo(createSnapshotResponse.getSnapshotInfo().successfulShards()));

        logger.info("-->  update repository with mock version");
        putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType(MockRepositoryModule.class.getCanonicalName()).setSettings(
                        ImmutableSettings.settingsBuilder()
                                .put("location", repositoryLocation)
                                .put("random", randomAsciiOfLength(10))
                                .put("random_data_file_io_exception_rate", 1.0) // Fail completely
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        // Test restore after index deletion
        logger.info("--> delete index");
        wipeIndices("test-idx");
        logger.info("--> restore index after deletion");
        ListenableActionFuture<RestoreSnapshotResponse> restoreSnapshotResponseFuture =
                client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap").setWaitForCompletion(true).execute();

        logger.info("--> wait for the index to appear");
        //  that would mean that recovery process started and failing
        assertThat(waitForIndex("test-idx", TimeValue.timeValueSeconds(10)), equalTo(true));

        logger.info("--> delete index");
        wipeIndices("test-idx");
        logger.info("--> get restore results");
        // Now read restore results and make sure it failed
        RestoreSnapshotResponse restoreSnapshotResponse = restoreSnapshotResponseFuture.actionGet(TimeValue.timeValueSeconds(10));
        assertThat(restoreSnapshotResponse.getRestoreInfo().failedShards(), greaterThan(0));
        assertThat(restoreSnapshotResponse.getRestoreInfo().totalShards(), equalTo(restoreSnapshotResponse.getRestoreInfo().failedShards()));

        logger.info("--> restoring working repository");
        putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType("fs").setSettings(ImmutableSettings.settingsBuilder().put("location", repositoryLocation)).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        logger.info("--> trying to restore index again");
        restoreSnapshotResponse = client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap").setWaitForCompletion(true).execute().actionGet();
        assertThat(restoreSnapshotResponse.getRestoreInfo().totalShards(), greaterThan(0));
        assertThat(restoreSnapshotResponse.getRestoreInfo().failedShards(), equalTo(0));
        ensureGreen();
        CountResponse countResponse = client.prepareCount("test-idx").get();
        assertThat(countResponse.getCount(), equalTo(100L));

    }

    @Test
    public void unallocatedShardsTest() throws Exception {
        Client client = client();

        logger.info("-->  creating repository");
        PutRepositoryResponse putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType("fs").setSettings(ImmutableSettings.settingsBuilder()
                        .put("location", newTempDir(LifecycleScope.SUITE))
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        logger.info("-->  creating index that cannot be allocated");
        prepareCreate("test-idx", 2, ImmutableSettings.builder().put(FilterAllocationDecider.INDEX_ROUTING_INCLUDE_GROUP + ".tag", "nowhere").put("index.number_of_shards", 3)).get();

        logger.info("--> snapshot");
        CreateSnapshotResponse createSnapshotResponse = client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap").setWaitForCompletion(true).setIndices("test-idx").get();
        assertThat(createSnapshotResponse.getSnapshotInfo().successfulShards(), equalTo(0));
        assertThat(createSnapshotResponse.getSnapshotInfo().totalShards(), equalTo(3));
        assertThat(createSnapshotResponse.getSnapshotInfo().shardFailures().size(), equalTo(3));
        assertThat(createSnapshotResponse.getSnapshotInfo().shardFailures().get(0).reason(), startsWith("primary shard is not allocated"));
    }

    @Test
    public void deleteSnapshotTest() throws Exception {
        final int numberOfSnapshots = between(5, 15);
        Client client = client();

        File repo = newTempDir(LifecycleScope.SUITE);
        logger.info("-->  creating repository at " + repo.getAbsolutePath());
        PutRepositoryResponse putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType("fs").setSettings(ImmutableSettings.settingsBuilder()
                        .put("location", repo)
                        .put("compress", false)
                        .put("chunk_size", randomIntBetween(100, 1000))
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        createIndex("test-idx");
        ensureGreen();

        int[] numberOfFiles = new int[numberOfSnapshots];
        logger.info("--> creating {} snapshots ", numberOfSnapshots);
        for (int i = 0; i < numberOfSnapshots; i++) {
            for (int j = 0; j < 10; j++) {
                index("test-idx", "doc", Integer.toString(i * 10 + j), "foo", "bar" + i * 10 + j);
            }
            refresh();
            logger.info("--> snapshot {}", i);
            CreateSnapshotResponse createSnapshotResponse = client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap-" + i).setWaitForCompletion(true).setIndices("test-idx").get();
            assertThat(createSnapshotResponse.getSnapshotInfo().successfulShards(), greaterThan(0));
            assertThat(createSnapshotResponse.getSnapshotInfo().successfulShards(), equalTo(createSnapshotResponse.getSnapshotInfo().totalShards()));
            // Store number of files after each snapshot
            numberOfFiles[i] = numberOfFiles(repo);
        }
        assertThat(client.prepareCount("test-idx").get().getCount(), equalTo(10L * numberOfSnapshots));
        int numberOfFilesBeforeDeletion = numberOfFiles(repo);

        logger.info("--> delete all snapshots except the first one and last one");
        for (int i = 1; i < numberOfSnapshots - 1; i++) {
            client.admin().cluster().prepareDeleteSnapshot("test-repo", "test-snap-" + i).get();
        }

        int numberOfFilesAfterDeletion = numberOfFiles(repo);

        assertThat(numberOfFilesAfterDeletion, lessThan(numberOfFilesBeforeDeletion));

        logger.info("--> delete index");
        wipeIndices("test-idx");

        logger.info("--> restore index");
        String lastSnapshot = "test-snap-" + (numberOfSnapshots - 1);
        RestoreSnapshotResponse restoreSnapshotResponse = client.admin().cluster().prepareRestoreSnapshot("test-repo", lastSnapshot).setWaitForCompletion(true).execute().actionGet();
        assertThat(restoreSnapshotResponse.getRestoreInfo().totalShards(), greaterThan(0));

        ensureGreen();
        assertThat(client.prepareCount("test-idx").get().getCount(), equalTo(10L * numberOfSnapshots));

        logger.info("--> delete the last snapshot");
        client.admin().cluster().prepareDeleteSnapshot("test-repo", lastSnapshot).get();
        logger.info("--> make sure that number of files is back to what it was when the first snapshot was made");
        assertThat(numberOfFiles(repo), equalTo(numberOfFiles[0]));
    }

    @Test
    @TestLogging("snapshots:TRACE")
    public void snapshotClosedIndexTest() throws Exception {
        Client client = client();

        logger.info("-->  creating repository");
        PutRepositoryResponse putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType("fs").setSettings(ImmutableSettings.settingsBuilder()
                        .put("location", newTempDir(LifecycleScope.SUITE))
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        createIndex("test-idx", "test-idx-closed");
        ensureGreen();
        logger.info("-->  closing index test-idx-closed");
        client.admin().indices().prepareClose("test-idx-closed").get();
        ClusterStateResponse stateResponse = client.admin().cluster().prepareState().get();
        assertThat(stateResponse.getState().metaData().index("test-idx-closed").state(), equalTo(IndexMetaData.State.CLOSE));
        assertThat(stateResponse.getState().routingTable().index("test-idx-closed"), nullValue());

        logger.info("--> snapshot");
        CreateSnapshotResponse createSnapshotResponse = client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap").setWaitForCompletion(true).setIndices("test-idx*").get();
        assertThat(createSnapshotResponse.getSnapshotInfo().indices().size(), equalTo(1));
        assertThat(createSnapshotResponse.getSnapshotInfo().shardFailures().size(), equalTo(0));

        logger.info("-->  deleting snapshot");
        client.admin().cluster().prepareDeleteSnapshot("test-repo", "test-snap").get();
    }

    @Test
    public void renameOnRestoreTest() throws Exception {
        Client client = client();

        logger.info("-->  creating repository");
        PutRepositoryResponse putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType("fs").setSettings(ImmutableSettings.settingsBuilder()
                        .put("location", newTempDir(LifecycleScope.SUITE))
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        createIndex("test-idx-1", "test-idx-2");
        ensureGreen();

        logger.info("--> indexing some data");
        for (int i = 0; i < 100; i++) {
            index("test-idx-1", "doc", Integer.toString(i), "foo", "bar" + i);
            index("test-idx-2", "doc", Integer.toString(i), "foo", "bar" + i);
        }
        refresh();
        assertThat(client.prepareCount("test-idx-1").get().getCount(), equalTo(100L));
        assertThat(client.prepareCount("test-idx-2").get().getCount(), equalTo(100L));

        logger.info("--> snapshot");
        CreateSnapshotResponse createSnapshotResponse = client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap").setWaitForCompletion(true).setIndices("test-idx-1", "test-idx-2").get();
        assertThat(createSnapshotResponse.getSnapshotInfo().successfulShards(), greaterThan(0));
        assertThat(createSnapshotResponse.getSnapshotInfo().successfulShards(), equalTo(createSnapshotResponse.getSnapshotInfo().totalShards()));

        logger.info("--> restore indices with different names");
        RestoreSnapshotResponse restoreSnapshotResponse = client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap")
                .setRenamePattern("(.+)").setRenameReplacement("$1-copy").setWaitForCompletion(true).execute().actionGet();
        assertThat(restoreSnapshotResponse.getRestoreInfo().totalShards(), greaterThan(0));

        ensureGreen();
        assertThat(client.prepareCount("test-idx-1-copy").get().getCount(), equalTo(100L));
        assertThat(client.prepareCount("test-idx-2-copy").get().getCount(), equalTo(100L));

        logger.info("--> close indices");
        client.admin().indices().prepareClose("test-idx-1", "test-idx-2-copy").get();

        logger.info("--> restore indices with different names");
        restoreSnapshotResponse = client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap")
                .setRenamePattern("(.+-2)").setRenameReplacement("$1-copy").setWaitForCompletion(true).execute().actionGet();
        assertThat(restoreSnapshotResponse.getRestoreInfo().totalShards(), greaterThan(0));

        logger.info("--> try renaming indices using the same name");
        try {
            client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap").setRenamePattern("(.+)").setRenameReplacement("same-name").setWaitForCompletion(true).execute().actionGet();
            fail("Shouldn't be here");
        } catch (SnapshotRestoreException ex) {
            // Expected
        }

        logger.info("--> try renaming indices using the same name");
        try {
            client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap").setRenamePattern("test-idx-2").setRenameReplacement("test-idx-1").setWaitForCompletion(true).execute().actionGet();
            fail("Shouldn't be here");
        } catch (SnapshotRestoreException ex) {
            // Expected
        }

        logger.info("--> try renaming indices using invalid index name");
        try {
            client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap").setIndices("test-idx-1").setRenamePattern(".+").setRenameReplacement("__WRONG__").setWaitForCompletion(true).execute().actionGet();
            fail("Shouldn't be here");
        } catch (InvalidIndexNameException ex) {
            // Expected
        }
    }

    @Test
    @TestLogging("cluster.routing.allocation.decider:TRACE")
    public void moveShardWhileSnapshottingTest() throws Exception {
        Client client = client();
        File repositoryLocation = newTempDir(LifecycleScope.TEST);
        logger.info("-->  creating repository");
        PutRepositoryResponse putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType(MockRepositoryModule.class.getCanonicalName()).setSettings(
                        ImmutableSettings.settingsBuilder()
                                .put("location", repositoryLocation)
                                .put("random", randomAsciiOfLength(10))
                                .put("wait_after_unblock", 200)
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        // Create index on 2 nodes and make sure each node has a primary by setting no replicas
        assertAcked(prepareCreate("test-idx", 2, ImmutableSettings.builder().put("number_of_replicas", 0)));

        logger.info("--> indexing some data");
        for (int i = 0; i < 100; i++) {
            index("test-idx", "doc", Integer.toString(i), "foo", "bar" + i);
        }
        refresh();
        assertThat(client.prepareCount("test-idx").get().getCount(), equalTo(100L));

        // Pick one node and block it
        String blockedNode = blockNodeWithIndex("test-idx");

        logger.info("--> snapshot");
        client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap").setWaitForCompletion(false).setIndices("test-idx").get();

        logger.info("--> waiting for block to kick in");
        waitForBlock(blockedNode, "test-repo", TimeValue.timeValueSeconds(60));

        logger.info("--> execution was blocked on node [{}], moving shards away from this node", blockedNode);
        ImmutableSettings.Builder excludeSettings = ImmutableSettings.builder().put("index.routing.allocation.exclude._name", blockedNode);
        client().admin().indices().prepareUpdateSettings("test-idx").setSettings(excludeSettings).get();

        logger.info("--> unblocking blocked node");
        unblockNode(blockedNode);
        logger.info("--> waiting for completion");
        SnapshotInfo snapshotInfo = waitForCompletion("test-repo", "test-snap", TimeValue.timeValueSeconds(600));
        logger.info("Number of failed shards [{}]", snapshotInfo.shardFailures().size());
        logger.info("--> done");

        ImmutableList<SnapshotInfo> snapshotInfos = client().admin().cluster().prepareGetSnapshots("test-repo").setSnapshots("test-snap").get().getSnapshots();

        assertThat(snapshotInfos.size(), equalTo(1));
        assertThat(snapshotInfos.get(0).state(), equalTo(SnapshotState.SUCCESS));
        assertThat(snapshotInfos.get(0).shardFailures().size(), equalTo(0));

        logger.info("--> delete index");
        wipeIndices("test-idx");

        logger.info("--> replace mock repository with real one at the same location");
        putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType("fs").setSettings(ImmutableSettings.settingsBuilder().put("location", repositoryLocation)
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        logger.info("--> restore index");
        RestoreSnapshotResponse restoreSnapshotResponse = client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap").setWaitForCompletion(true).execute().actionGet();
        assertThat(restoreSnapshotResponse.getRestoreInfo().totalShards(), greaterThan(0));

        ensureGreen();
        assertThat(client.prepareCount("test-idx").get().getCount(), equalTo(100L));
    }

    @Test
    @TestLogging("cluster.routing.allocation.decider:TRACE")
    public void deleteRepositoryWhileSnapshottingTest() throws Exception {
        Client client = client();
        File repositoryLocation = newTempDir(LifecycleScope.TEST);
        logger.info("-->  creating repository");
        PutRepositoryResponse putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType(MockRepositoryModule.class.getCanonicalName()).setSettings(
                        ImmutableSettings.settingsBuilder()
                                .put("location", repositoryLocation)
                                .put("random", randomAsciiOfLength(10))
                                .put("wait_after_unblock", 200)
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        // Create index on 2 nodes and make sure each node has a primary by setting no replicas
        assertAcked(prepareCreate("test-idx", 2, ImmutableSettings.builder().put("number_of_replicas", 0)));

        logger.info("--> indexing some data");
        for (int i = 0; i < 100; i++) {
            index("test-idx", "doc", Integer.toString(i), "foo", "bar" + i);
        }
        refresh();
        assertThat(client.prepareCount("test-idx").get().getCount(), equalTo(100L));

        // Pick one node and block it
        String blockedNode = blockNodeWithIndex("test-idx");

        logger.info("--> snapshot");
        client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap").setWaitForCompletion(false).setIndices("test-idx").get();

        logger.info("--> waiting for block to kick in");
        waitForBlock(blockedNode, "test-repo", TimeValue.timeValueSeconds(60));

        logger.info("--> execution was blocked on node [{}], trying to delete repository", blockedNode);

        try {
            client.admin().cluster().prepareDeleteRepository("test-repo").execute().get();
            fail("shouldn't be able to delete in-use repository");
        } catch (Exception ex) {
            logger.info("--> in-use repository deletion failed");
        }

        logger.info("--> trying to move repository to another location");
        try {
            client.admin().cluster().preparePutRepository("test-repo")
                    .setType("fs").setSettings(ImmutableSettings.settingsBuilder().put("location", new File(repositoryLocation, "test"))
            ).get();
            fail("shouldn't be able to replace in-use repository");
        } catch (Exception ex) {
            logger.info("--> in-use repository replacement failed");
        }

        logger.info("--> trying to create a repository with different name");
        putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo-2")
                .setType("fs").setSettings(ImmutableSettings.settingsBuilder().put("location", new File(repositoryLocation, "test"))
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        logger.info("--> unblocking blocked node");
        unblockNode(blockedNode);
        logger.info("--> waiting for completion");
        SnapshotInfo snapshotInfo = waitForCompletion("test-repo", "test-snap", TimeValue.timeValueSeconds(600));
        logger.info("Number of failed shards [{}]", snapshotInfo.shardFailures().size());
        logger.info("--> done");

        ImmutableList<SnapshotInfo> snapshotInfos = client().admin().cluster().prepareGetSnapshots("test-repo").setSnapshots("test-snap").get().getSnapshots();

        assertThat(snapshotInfos.size(), equalTo(1));
        assertThat(snapshotInfos.get(0).state(), equalTo(SnapshotState.SUCCESS));
        assertThat(snapshotInfos.get(0).shardFailures().size(), equalTo(0));

        logger.info("--> delete index");
        wipeIndices("test-idx");

        logger.info("--> replace mock repository with real one at the same location");
        putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType("fs").setSettings(ImmutableSettings.settingsBuilder().put("location", repositoryLocation)
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        logger.info("--> restore index");
        RestoreSnapshotResponse restoreSnapshotResponse = client.admin().cluster().prepareRestoreSnapshot("test-repo", "test-snap").setWaitForCompletion(true).execute().actionGet();
        assertThat(restoreSnapshotResponse.getRestoreInfo().totalShards(), greaterThan(0));

        ensureGreen();
        assertThat(client.prepareCount("test-idx").get().getCount(), equalTo(100L));
    }

    @Test
    public void urlRepositoryTest() throws Exception {
        Client client = client();

        logger.info("-->  creating repository");
        File repositoryLocation = newTempDir(LifecycleScope.SUITE);
        PutRepositoryResponse putRepositoryResponse = client.admin().cluster().preparePutRepository("test-repo")
                .setType("fs").setSettings(ImmutableSettings.settingsBuilder()
                        .put("location", repositoryLocation)
                        .put("compress", randomBoolean())
                        .put("chunk_size", randomIntBetween(100, 1000))
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        createIndex("test-idx");
        ensureGreen();

        logger.info("--> indexing some data");
        for (int i = 0; i < 100; i++) {
            index("test-idx", "doc", Integer.toString(i), "foo", "bar" + i);
        }
        refresh();
        assertThat(client.prepareCount("test-idx").get().getCount(), equalTo(100L));

        logger.info("--> snapshot");
        CreateSnapshotResponse createSnapshotResponse = client.admin().cluster().prepareCreateSnapshot("test-repo", "test-snap").setWaitForCompletion(true).setIndices("test-idx").get();
        assertThat(createSnapshotResponse.getSnapshotInfo().successfulShards(), greaterThan(0));
        assertThat(createSnapshotResponse.getSnapshotInfo().successfulShards(), equalTo(createSnapshotResponse.getSnapshotInfo().totalShards()));

        assertThat(client.admin().cluster().prepareGetSnapshots("test-repo").setSnapshots("test-snap").get().getSnapshots().get(0).state(), equalTo(SnapshotState.SUCCESS));

        logger.info("--> delete index");
        wipeIndices("test-idx");

        logger.info("--> create read-only URL repository");
        putRepositoryResponse = client.admin().cluster().preparePutRepository("url-repo")
                .setType("url").setSettings(ImmutableSettings.settingsBuilder()
                        .put("url", repositoryLocation.toURI().toURL())
                        .put("list_directories", randomBoolean())
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));
        logger.info("--> restore index after deletion");
        RestoreSnapshotResponse restoreSnapshotResponse = client.admin().cluster().prepareRestoreSnapshot("url-repo", "test-snap").setWaitForCompletion(true).setIndices("test-idx").execute().actionGet();
        assertThat(restoreSnapshotResponse.getRestoreInfo().totalShards(), greaterThan(0));
        ensureGreen();
        assertThat(client.prepareCount("test-idx").get().getCount(), equalTo(100L));

        logger.info("--> list available shapshots");
        GetSnapshotsResponse getSnapshotsResponse = client.admin().cluster().prepareGetSnapshots("url-repo").get();
        assertThat(getSnapshotsResponse.getSnapshots(), notNullValue());
        assertThat(getSnapshotsResponse.getSnapshots().size(), equalTo(1));

        logger.info("--> delete snapshot");
        DeleteSnapshotResponse deleteSnapshotResponse = client.admin().cluster().prepareDeleteSnapshot("test-repo", "test-snap").get();
        assertAcked(deleteSnapshotResponse);

        logger.info("--> list available shapshot again, no snapshots should be returned");
        getSnapshotsResponse = client.admin().cluster().prepareGetSnapshots("url-repo").get();
        assertThat(getSnapshotsResponse.getSnapshots(), notNullValue());
        assertThat(getSnapshotsResponse.getSnapshots().size(), equalTo(0));
    }

    private boolean waitForIndex(String index, TimeValue timeout) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeout.millis()) {
            if (client().admin().indices().prepareExists(index).execute().actionGet().isExists()) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }
}
