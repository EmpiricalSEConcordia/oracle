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

package org.elasticsearch.client.action.deletebyquery;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.deletebyquery.DeleteByQueryRequest;
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse;
import org.elasticsearch.action.support.PlainListenableActionFuture;
import org.elasticsearch.action.support.replication.ReplicationType;
import org.elasticsearch.client.internal.InternalClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.util.TimeValue;
import org.elasticsearch.util.xcontent.builder.XContentBuilder;

import java.util.Map;

/**
 * @author kimchy (shay.banon)
 */
public class DeleteByQueryRequestBuilder {

    private final InternalClient client;

    private final DeleteByQueryRequest request;

    public DeleteByQueryRequestBuilder(InternalClient client) {
        this.client = client;
        this.request = new DeleteByQueryRequest();
    }

    /**
     * The indices the delete by query will run against.
     */
    public DeleteByQueryRequestBuilder setIndices(String... indices) {
        request.indices(indices);
        return this;
    }

    /**
     * The types of documents the query will run against. Defaults to all types.
     */
    public DeleteByQueryRequestBuilder setTypes(String... types) {
        request.types(types);
        return this;
    }

    /**
     * The query source to execute.
     *
     * @see org.elasticsearch.index.query.xcontent.QueryBuilders
     */
    public DeleteByQueryRequestBuilder setQuery(QueryBuilder queryBuilder) {
        request.query(queryBuilder);
        return this;
    }

    /**
     * The query source to execute. It is preferable to use either {@link #setQuery(byte[])}
     * or {@link #setQuery(org.elasticsearch.index.query.QueryBuilder)}.
     */
    public DeleteByQueryRequestBuilder setQuery(String querySource) {
        request.query(querySource);
        return this;
    }

    /**
     * The query source to execute in the form of a map.
     */
    public DeleteByQueryRequestBuilder setQuery(Map<String, Object> querySource) {
        request.query(querySource);
        return this;
    }

    /**
     * The query source to execute in the form of a builder.
     */
    public DeleteByQueryRequestBuilder setQuery(XContentBuilder builder) {
        request.query(builder);
        return this;
    }

    /**
     * The query source to execute.
     */
    public DeleteByQueryRequestBuilder setQuery(byte[] querySource) {
        request.query(querySource);
        return this;
    }

    /**
     * The query source to execute.
     */
    public DeleteByQueryRequestBuilder query(byte[] querySource, int offset, int length, boolean unsafe) {
        request.query(querySource, offset, length, unsafe);
        return this;
    }

    /**
     * A timeout to wait if the delete by query operation can't be performed immediately. Defaults to <tt>1m</tt>.
     */
    public DeleteByQueryRequestBuilder setTimeout(TimeValue timeout) {
        request.timeout(timeout);
        return this;
    }

    /**
     * A timeout to wait if the delete by query operation can't be performed immediately. Defaults to <tt>1m</tt>.
     */
    public DeleteByQueryRequestBuilder setTimeout(String timeout) {
        request.timeout(timeout);
        return this;
    }

    /**
     * The replication type to use with this operation.
     */
    public DeleteByQueryRequestBuilder replicationType(ReplicationType replicationType) {
        request.replicationType(replicationType);
        return this;
    }

    /**
     * The replication type to use with this operation.
     */
    public DeleteByQueryRequestBuilder replicationType(String replicationType) {
        request.replicationType(replicationType);
        return this;
    }

    /**
     * Should the listener be called on a separate thread if needed.
     */
    public DeleteByQueryRequestBuilder setListenerThreaded(boolean threadedListener) {
        request.listenerThreaded(threadedListener);
        return this;
    }

    /**
     * Executes the operation asynchronously and returns a future.
     */
    public ListenableActionFuture<DeleteByQueryResponse> execute() {
        PlainListenableActionFuture<DeleteByQueryResponse> future = new PlainListenableActionFuture<DeleteByQueryResponse>(request.listenerThreaded(), client.threadPool());
        client.deleteByQuery(request, future);
        return future;
    }

    /**
     * Executes the operation asynchronously with the provided listener.
     */
    public void execute(ActionListener<DeleteByQueryResponse> listener) {
        client.deleteByQuery(request, listener);
    }
}
