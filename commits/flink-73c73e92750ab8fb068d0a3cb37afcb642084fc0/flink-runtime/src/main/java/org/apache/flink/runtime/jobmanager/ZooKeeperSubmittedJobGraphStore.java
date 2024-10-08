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

package org.apache.flink.runtime.jobmanager;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.utils.ZKPaths;
import org.apache.flink.api.common.JobID;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.runtime.state.StateHandle;
import org.apache.flink.runtime.state.StateHandleProvider;
import org.apache.flink.runtime.zookeeper.ZooKeeperStateHandleStore;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * {@link SubmittedJobGraph} instances for JobManagers running in {@link RecoveryMode#ZOOKEEPER}.
 *
 * <p>Each job graph creates ZNode:
 * <pre>
 * +----O /flink/jobgraphs/&lt;job-id&gt; 1 [persistent]
 * .
 * .
 * .
 * +----O /flink/jobgraphs/&lt;job-id&gt; N [persistent]
 * </pre>
 *
 * <p>The root path is watched to detect concurrent modifications in corner situations where
 * multiple instances operate concurrently. The job manager acts as a {@link SubmittedJobGraphListener}
 * to react to such situations.
 */
public class ZooKeeperSubmittedJobGraphStore implements SubmittedJobGraphStore {

	private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperSubmittedJobGraphStore.class);

	/** Lock to synchronize with the {@link SubmittedJobGraphListener}. */
	private final Object cacheLock = new Object();

	/** Client (not a namespace facade) */
	private final CuratorFramework client;

	/** The set of IDs of all added job graphs. */
	private final Set<JobID> addedJobGraphs = new HashSet<>();

	/** Completed checkpoints in ZooKeeper */
	private final ZooKeeperStateHandleStore<SubmittedJobGraph> jobGraphsInZooKeeper;

	/**
	 * Cache to monitor all children. This is used to detect races with other instances working
	 * on the same state.
	 */
	private final PathChildrenCache pathCache;

	/** The external listener to be notified on races. */
	private SubmittedJobGraphListener jobGraphListener;

	/** Flag indicating whether this instance is running. */
	private boolean isRunning;

	public ZooKeeperSubmittedJobGraphStore(
			CuratorFramework client,
			String currentJobsPath,
			StateHandleProvider<SubmittedJobGraph> stateHandleProvider) throws Exception {

		checkNotNull(currentJobsPath, "Current jobs path");
		checkNotNull(stateHandleProvider, "State handle provider");

		// Keep a reference to the original client and not the namespace facade. The namespace
		// facade cannot be closed.
		this.client = checkNotNull(client, "Curator client");

		// Ensure that the job graphs path exists
		client.newNamespaceAwareEnsurePath(currentJobsPath)
				.ensure(client.getZookeeperClient());

		// All operations will have the path as root
		client = client.usingNamespace(client.getNamespace() + currentJobsPath);

		this.jobGraphsInZooKeeper = new ZooKeeperStateHandleStore<>(client, stateHandleProvider);

		this.pathCache = new PathChildrenCache(client, "/", false);
		pathCache.getListenable().addListener(new SubmittedJobGraphsPathCacheListener());
	}

	@Override
	public void start(SubmittedJobGraphListener jobGraphListener) throws Exception {
		synchronized (cacheLock) {
			if (!isRunning) {
				this.jobGraphListener = jobGraphListener;

				pathCache.start();

				isRunning = true;
			}
		}
	}

	@Override
	public void stop() throws Exception {
		synchronized (cacheLock) {
			if (isRunning) {
				jobGraphListener = null;

				pathCache.close();

				client.close();

				isRunning = false;
			}
		}
	}

	@Override
	public List<SubmittedJobGraph> recoverJobGraphs() throws Exception {
		synchronized (cacheLock) {
			verifyIsRunning();

			List<Tuple2<StateHandle<SubmittedJobGraph>, String>> submitted;

			while (true) {
				try {
					submitted = jobGraphsInZooKeeper.getAll();
					break;
				}
				catch (ConcurrentModificationException e) {
					LOG.warn("Concurrent modification while reading from ZooKeeper. Retrying.");
				}
			}

			if (submitted.size() != 0) {
				List<SubmittedJobGraph> jobGraphs = new ArrayList<>(submitted.size());

				for (Tuple2<StateHandle<SubmittedJobGraph>, String> jobStateHandle : submitted) {
					SubmittedJobGraph jobGraph = jobStateHandle
							.f0.getState(ClassLoader.getSystemClassLoader());

					addedJobGraphs.add(jobGraph.getJobId());

					jobGraphs.add(jobGraph);
				}

				LOG.info("Recovered {} job graphs: {}.", jobGraphs.size(), jobGraphs);
				return jobGraphs;
			}
			else {
				LOG.info("No job graph to recover.");
				return Collections.emptyList();
			}
		}
	}

	@Override
	public Option<SubmittedJobGraph> recoverJobGraph(JobID jobId) throws Exception {
		checkNotNull(jobId, "Job ID");
		String path = getPathForJob(jobId);

		synchronized (cacheLock) {
			verifyIsRunning();

			try {
				StateHandle<SubmittedJobGraph> jobStateHandle = jobGraphsInZooKeeper.get(path);

				SubmittedJobGraph jobGraph = jobStateHandle
						.getState(ClassLoader.getSystemClassLoader());

				addedJobGraphs.add(jobGraph.getJobId());

				LOG.info("Recovered {}.", jobGraph);

				return Option.apply(jobGraph);
			}
			catch (KeeperException.NoNodeException ignored) {
				return Option.empty();
			}
		}
	}

	@Override
	public void putJobGraph(SubmittedJobGraph jobGraph) throws Exception {
		checkNotNull(jobGraph, "Job graph");
		String path = getPathForJob(jobGraph.getJobId());

		boolean success = false;

		while (!success) {
			synchronized (cacheLock) {
				verifyIsRunning();

				int currentVersion = jobGraphsInZooKeeper.exists(path);

				if (currentVersion == -1) {
					try {
						jobGraphsInZooKeeper.add(path, jobGraph);

						addedJobGraphs.add(jobGraph.getJobId());

						LOG.info("Added {} to ZooKeeper.", jobGraph);

						success = true;
					}
					catch (KeeperException.NodeExistsException ignored) {
					}
				}
				else if (addedJobGraphs.contains(jobGraph.getJobId())) {
					try {
						jobGraphsInZooKeeper.replace(path, currentVersion, jobGraph);
						LOG.info("Updated {} in ZooKeeper.", jobGraph);

						success = true;
					}
					catch (KeeperException.NoNodeException ignored) {
					}
				}
				else {
					throw new IllegalStateException("Oh, no. Trying to update a graph you didn't " +
							"#getAllSubmittedJobGraphs() or #putJobGraph() yourself before.");
				}
			}
		}
	}

	@Override
	public void removeJobGraph(JobID jobId) throws Exception {
		checkNotNull(jobId, "Job ID");
		String path = getPathForJob(jobId);

		synchronized (cacheLock) {
			if (addedJobGraphs.contains(jobId)) {
				jobGraphsInZooKeeper.removeAndDiscardState(path);

				addedJobGraphs.remove(jobId);
				LOG.info("Removed job graph {} from ZooKeeper.", jobId);
			}
		}
	}

	/**
	 * Monitors ZooKeeper for changes.
	 *
	 * <p>Detects modifications from other job managers in corner situations. The event
	 * notifications fire for changes from this job manager as well.
	 */
	private final class SubmittedJobGraphsPathCacheListener implements PathChildrenCacheListener {

		@Override
		public void childEvent(CuratorFramework client, PathChildrenCacheEvent event)
				throws Exception {

			if (LOG.isDebugEnabled()) {
				if (event.getData() != null) {
					LOG.debug("Received {} event (path: {})", event.getType(), event.getData().getPath());
				}
				else {
					LOG.debug("Received {} event", event.getType());
				}
			}

			switch (event.getType()) {
				case CHILD_ADDED:
					synchronized (cacheLock) {
						try {
							JobID jobId = fromEvent(event);
							if (jobGraphListener != null && !addedJobGraphs.contains(jobId)) {
								try {
									// Whoa! This has been added by someone else. Or we were fast
									// to remove it (false positive).
									jobGraphListener.onAddedJobGraph(jobId);
								}
								catch (Throwable t) {
									LOG.error("Error in callback", t);
								}
							}
						}
						catch (Exception e) {
							LOG.error("Error in SubmittedJobGraphsPathCacheListener", e);
						}
					}

					break;

				case CHILD_UPDATED:
					// Nothing to do
					break;

				case CHILD_REMOVED:
					synchronized (cacheLock) {
						try {
							JobID jobId = fromEvent(event);
							if (jobGraphListener != null && addedJobGraphs.contains(jobId)) {
								try {
									// Oh oh. Someone else removed one of our job graphs. Mean!
									jobGraphListener.onRemovedJobGraph(jobId);
								}
								catch (Throwable t) {
									LOG.error("Error in callback", t);
								}
							}

							break;
						}
						catch (Exception e) {
							LOG.error("Error in SubmittedJobGraphsPathCacheListener", e);
						}
					}
					break;

				case CONNECTION_SUSPENDED:
					LOG.warn("ZooKeeper connection SUSPENDED. Changes to the submitted job " +
							"graphs are not monitored (temporarily).");

				case CONNECTION_LOST:
					LOG.warn("ZooKeeper connection LOST. Changes to the submitted job " +
							"graphs are not monitored (permanently).");
					break;

				case CONNECTION_RECONNECTED:
					LOG.info("ZooKeeper connection RECONNECTED. Changes to the submitted job " +
							"graphs are monitored again.");

				case INITIALIZED:
					LOG.info("SubmittedJobGraphsPathCacheListener initialized");
					break;
			}
		}

		/**
		 * Returns a JobID for the event's path.
		 */
		private JobID fromEvent(PathChildrenCacheEvent event) {
			return JobID.fromHexString(ZKPaths.getNodeFromPath(event.getData().getPath()));
		}
	}

	/**
	 * Verifies that the state is running.
	 */
	private void verifyIsRunning() {
		checkState(isRunning, "Not running. Forgot to call start()?");
	}

	/**
	 * Returns the JobID as a String (with leading slash).
	 */
	public static String getPathForJob(JobID jobId) {
		checkNotNull(jobId, "Job ID");
		return String.format("/%s", jobId);
	}

}
