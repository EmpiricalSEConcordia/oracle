/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
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

package org.elasticsearch.rest.action.admin.indices.validate.query;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.validate.query.QueryExplanation;
import org.elasticsearch.action.admin.indices.validate.query.ValidateQueryRequest;
import org.elasticsearch.action.admin.indices.validate.query.ValidateQueryResponse;
import org.elasticsearch.action.support.IgnoreIndices;
import org.elasticsearch.action.support.QuerySourceBuilder;
import org.elasticsearch.action.support.broadcast.BroadcastOperationThreading;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.*;
import org.elasticsearch.rest.action.support.RestActions;
import org.elasticsearch.rest.action.support.RestXContentBuilder;

import java.io.IOException;

import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestStatus.BAD_REQUEST;
import static org.elasticsearch.rest.RestStatus.OK;
import static org.elasticsearch.rest.action.support.RestActions.buildBroadcastShardsHeader;

/**
 *
 */
public class RestValidateQueryAction extends BaseRestHandler {

    @Inject
    public RestValidateQueryAction(Settings settings, Client client, RestController controller) {
        super(settings, client);
        controller.registerHandler(GET, "/_validate/query", this);
        controller.registerHandler(POST, "/_validate/query", this);
        controller.registerHandler(GET, "/{index}/_validate/query", this);
        controller.registerHandler(POST, "/{index}/_validate/query", this);
        controller.registerHandler(GET, "/{index}/{type}/_validate/query", this);
        controller.registerHandler(POST, "/{index}/{type}/_validate/query", this);
    }

    @Override
    public void handleRequest(final RestRequest request, final RestChannel channel) {
        ValidateQueryRequest validateQueryRequest = new ValidateQueryRequest(Strings.splitStringByCommaToArray(request.param("index")));
        validateQueryRequest.listenerThreaded(false);
        if (request.hasParam("ignore_indices")) {
            validateQueryRequest.ignoreIndices(IgnoreIndices.fromString(request.param("ignore_indices")));
        }
        try {
            BroadcastOperationThreading operationThreading = BroadcastOperationThreading.fromString(request.param("operation_threading"), BroadcastOperationThreading.SINGLE_THREAD);
            if (operationThreading == BroadcastOperationThreading.NO_THREADS) {
                // since we don't spawn, don't allow no_threads, but change it to a single thread
                operationThreading = BroadcastOperationThreading.SINGLE_THREAD;
            }
            validateQueryRequest.operationThreading(operationThreading);
            if (request.hasContent()) {
                validateQueryRequest.source(request.content(), request.contentUnsafe());
            } else {
                String source = request.param("source");
                if (source != null) {
                    validateQueryRequest.source(source);
                } else {
                    QuerySourceBuilder querySourceBuilder = RestActions.parseQuerySource(request);
                    if (querySourceBuilder != null) {
                        validateQueryRequest.source(querySourceBuilder);
                    }
                }
            }
            validateQueryRequest.types(Strings.splitStringByCommaToArray(request.param("type")));
            if (request.paramAsBoolean("explain", false)) {
                validateQueryRequest.explain(true);
            } else {
                validateQueryRequest.explain(false);
            }
        } catch (Exception e) {
            try {
                XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
                channel.sendResponse(new XContentRestResponse(request, BAD_REQUEST, builder.startObject().field("error", e.getMessage()).endObject()));
            } catch (IOException e1) {
                logger.error("Failed to send failure response", e1);
            }
            return;
        }

        client.admin().indices().validateQuery(validateQueryRequest, new ActionListener<ValidateQueryResponse>() {
            @Override
            public void onResponse(ValidateQueryResponse response) {
                try {
                    XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
                    builder.startObject();
                    builder.field("valid", response.isValid());

                    buildBroadcastShardsHeader(builder, response);

                    if (response.getQueryExplanation() != null && !response.getQueryExplanation().isEmpty()) {
                        builder.startArray("explanations");
                        for (QueryExplanation explanation : response.getQueryExplanation()) {
                            builder.startObject();
                            if (explanation.getIndex() != null) {
                                builder.field("index", explanation.getIndex(), XContentBuilder.FieldCaseConversion.NONE);
                            }
                            builder.field("valid", explanation.isValid());
                            if (explanation.getError() != null) {
                                builder.field("error", explanation.getError());
                            }
                            if (explanation.getExplanation() != null) {
                                builder.field("explanation", explanation.getExplanation());
                            }
                            builder.endObject();
                        }
                        builder.endArray();
                    }
                    builder.endObject();
                    channel.sendResponse(new XContentRestResponse(request, OK, builder));
                } catch (Throwable e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Throwable e) {
                try {
                    channel.sendResponse(new XContentThrowableRestResponse(request, e));
                } catch (IOException e1) {
                    logger.error("Failed to send failure response", e1);
                }
            }
        });
    }
}
