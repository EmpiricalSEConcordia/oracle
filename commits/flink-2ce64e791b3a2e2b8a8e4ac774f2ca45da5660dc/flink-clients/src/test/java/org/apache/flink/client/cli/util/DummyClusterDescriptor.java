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

package org.apache.flink.client.cli.util;

import org.apache.flink.client.deployment.ClusterDescriptor;
import org.apache.flink.client.deployment.ClusterSpecification;
import org.apache.flink.client.program.ClusterClient;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.util.FlinkException;
import org.apache.flink.util.Preconditions;

/**
 * Dummy {@link ClusterDescriptor} implementation for testing purposes.
 */
public class DummyClusterDescriptor implements ClusterDescriptor {

	private final ClusterClient clusterClient;

	public DummyClusterDescriptor(ClusterClient clusterClient) {
		this.clusterClient = Preconditions.checkNotNull(clusterClient);
	}

	@Override
	public String getClusterDescription() {
		return "DummyClusterDescriptor";
	}

	@Override
	public ClusterClient retrieve(String applicationID) throws UnsupportedOperationException {
		return clusterClient;
	}

	@Override
	public ClusterClient deploySessionCluster(ClusterSpecification clusterSpecification) throws UnsupportedOperationException {
		return clusterClient;
	}

	@Override
	public ClusterClient deployJobCluster(ClusterSpecification clusterSpecification, JobGraph jobGraph) {
		return clusterClient;
	}

	@Override
	public void terminateCluster(String clusterId) throws FlinkException {
		throw new UnsupportedOperationException("DummyClusterDescriptor does not support cluster termination.");
	}

	@Override
	public void close() {
		// nothing to do
	}
}
