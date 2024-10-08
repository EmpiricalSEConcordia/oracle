/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package org.elasticsearch.action.index;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.TransportActions;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.create.TransportCreateIndexAction;
import org.elasticsearch.action.support.replication.TransportShardReplicationOperationAction;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.action.index.MappingUpdatedAction;
import org.elasticsearch.cluster.action.shard.ShardStateAction;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.routing.ShardsIterator;
import org.elasticsearch.common.UUID;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.mapper.DocumentMapper;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.ParsedDocument;
import org.elasticsearch.index.shard.service.IndexShard;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;

/**
 * Performs the index operation.
 *
 * <p>Allows for the following settings:
 * <ul>
 * <li><b>autoCreateIndex</b>: When set to <tt>true</tt>, will automatically create an index if one does not exists.
 * Defaults to <tt>true</tt>.
 * <li><b>allowIdGeneration</b>: If the id is set not, should it be generated. Defaults to <tt>true</tt>.
 * </ul>
 *
 * @author kimchy (shay.banon)
 */
public class TransportIndexAction extends TransportShardReplicationOperationAction<IndexRequest, IndexResponse> {

    private final boolean autoCreateIndex;

    private final boolean allowIdGeneration;

    private final TransportCreateIndexAction createIndexAction;

    private final MappingUpdatedAction mappingUpdatedAction;

    @Inject public TransportIndexAction(Settings settings, TransportService transportService, ClusterService clusterService,
                                        IndicesService indicesService, ThreadPool threadPool, ShardStateAction shardStateAction,
                                        TransportCreateIndexAction createIndexAction, MappingUpdatedAction mappingUpdatedAction) {
        super(settings, transportService, clusterService, indicesService, threadPool, shardStateAction);
        this.createIndexAction = createIndexAction;
        this.mappingUpdatedAction = mappingUpdatedAction;
        this.autoCreateIndex = settings.getAsBoolean("action.auto_create_index", true);
        this.allowIdGeneration = settings.getAsBoolean("action.allow_id_generation", true);
    }

    @Override protected void doExecute(final IndexRequest indexRequest, final ActionListener<IndexResponse> listener) {
        if (allowIdGeneration) {
            if (indexRequest.id() == null) {
                indexRequest.id(UUID.randomBase64UUID());
                // since we generate the id, change it to CREATE
                indexRequest.opType(IndexRequest.OpType.CREATE);
            }
        }
        if (autoCreateIndex && !clusterService.state().metaData().hasConcreteIndex(indexRequest.index())) {
            createIndexAction.execute(new CreateIndexRequest(indexRequest.index()).cause("auto(index api)"), new ActionListener<CreateIndexResponse>() {
                @Override public void onResponse(CreateIndexResponse result) {
                    TransportIndexAction.super.doExecute(indexRequest, listener);
                }

                @Override public void onFailure(Throwable e) {
                    if (ExceptionsHelper.unwrapCause(e) instanceof IndexAlreadyExistsException) {
                        // we have the index, do it
                        TransportIndexAction.super.doExecute(indexRequest, listener);
                    } else {
                        listener.onFailure(e);
                    }
                }
            });
        } else {
            super.doExecute(indexRequest, listener);
        }
    }

    @Override protected IndexRequest newRequestInstance() {
        return new IndexRequest();
    }

    @Override protected IndexResponse newResponseInstance() {
        return new IndexResponse();
    }

    @Override protected String transportAction() {
        return TransportActions.INDEX;
    }

    @Override protected void checkBlock(IndexRequest request, ClusterState state) {
        state.blocks().indexBlockedRaiseException(ClusterBlockLevel.WRITE, request.index());
    }

    @Override protected ShardsIterator shards(ClusterState clusterState, IndexRequest request) {
        return clusterService.operationRouting()
                .indexShards(clusterService.state(), request.index(), request.type(), request.id());
    }

    @Override protected IndexResponse shardOperationOnPrimary(ShardOperationRequest shardRequest) {
        IndexShard indexShard = indexShard(shardRequest);
        final IndexRequest request = shardRequest.request;
        ParsedDocument doc;
        if (request.opType() == IndexRequest.OpType.INDEX) {
            Engine.Index index = indexShard.prepareIndex(request.type(), request.id(), request.source());
            index.refresh(request.refresh());
            doc = indexShard.index(index);
        } else {
            Engine.Create create = indexShard(shardRequest).prepareCreate(request.type(), request.id(), request.source());
            create.refresh(request.refresh());
            doc = indexShard(shardRequest).create(create);
        }
        if (doc.mappersAdded()) {
            updateMappingOnMaster(request);
        }
        return new IndexResponse(request.index(), request.type(), request.id());
    }

    @Override protected void shardOperationOnReplica(ShardOperationRequest shardRequest) {
        IndexRequest request = shardRequest.request;
        if (request.opType() == IndexRequest.OpType.INDEX) {
            indexShard(shardRequest).index(request.type(), request.id(), request.source());
        } else {
            indexShard(shardRequest).create(request.type(), request.id(), request.source());
        }
    }

    private void updateMappingOnMaster(final IndexRequest request) {
        try {
            MapperService mapperService = indicesService.indexServiceSafe(request.index()).mapperService();
            final DocumentMapper documentMapper = mapperService.documentMapper(request.type());
            documentMapper.refreshSource();

            mappingUpdatedAction.execute(new MappingUpdatedAction.MappingUpdatedRequest(request.index(), request.type(), documentMapper.mappingSource()), new ActionListener<MappingUpdatedAction.MappingUpdatedResponse>() {
                @Override public void onResponse(MappingUpdatedAction.MappingUpdatedResponse mappingUpdatedResponse) {
                    // all is well
                }

                @Override public void onFailure(Throwable e) {
                    try {
                        logger.warn("Failed to update master on updated mapping for index [" + request.index() + "], type [" + request.type() + "] and source [" + documentMapper.mappingSource().string() + "]", e);
                    } catch (IOException e1) {
                        // ignore
                    }
                }
            });
        } catch (Exception e) {
            logger.warn("Failed to update master on updated mapping for index [" + request.index() + "], type [" + request.type() + "]", e);
        }
    }
}
