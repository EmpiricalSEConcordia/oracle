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

package org.elasticsearch.index.shard;

import org.elasticsearch.index.shard.recovery.RecoveryAction;
import org.elasticsearch.index.shard.service.IndexShard;
import org.elasticsearch.index.shard.service.InternalIndexShard;
import org.elasticsearch.util.inject.AbstractModule;

/**
 * @author kimchy (shay.banon)
 */
public class IndexShardModule extends AbstractModule {

    private final ShardId shardId;

    public IndexShardModule(ShardId shardId) {
        this.shardId = shardId;
    }

    @Override protected void configure() {
        bind(ShardId.class).toInstance(shardId);
        bind(IndexShard.class).to(InternalIndexShard.class).asEagerSingleton();
        bind(IndexShardManagement.class).asEagerSingleton();

        bind(RecoveryAction.class).asEagerSingleton();
    }
}