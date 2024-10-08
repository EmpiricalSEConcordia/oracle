package org.apache.solr.cloud;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest.Create;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.ClusterStateUtil;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.Replica;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.core.ConfigSolr;
import org.apache.solr.update.UpdateShardHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;


// TODO: how to tmp exclude nodes?

// TODO: more fine grained failover rules?

// TODO: test with lots of collections

// TODO: add config for only failover if replicas is < N

// TODO: general support for non shared filesystems
// this is specialized for a shared file system, but it should
// not be much work to generalize

// NOTE: using replication can slow down failover if a whole
// shard is lost.

/**
 *
 * In this simple initial implementation we are limited in how quickly we detect
 * a failure by a worst case of roughly zk session timeout + WAIT_AFTER_EXPIRATION_SECONDS + WORK_LOOP_DELAY_MS
 * and best case of roughly zk session timeout + WAIT_AFTER_EXPIRATION_SECONDS. Also, consider the time to
 * create the SolrCore, do any recovery necessary, and warm up the readers.
 * 
 * NOTE: this will only work with collections created via the collections api because they will have defined
 * replicationFactor and maxShardsPerNode.
 * 
 * @lucene.experimental
 */
public class OverseerAutoReplicaFailoverThread implements Runnable, Closeable {
  
  private static Logger log = LoggerFactory.getLogger(OverseerAutoReplicaFailoverThread.class);

  private Integer lastClusterStateVersion;
  
  private final ExecutorService updateExecutor;
  private volatile boolean isClosed;
  private ZkStateReader zkStateReader;
  private final Cache<String,Long> baseUrlForBadNodes;

  private final int workLoopDelay;
  private final int waitAfterExpiration;
  
  public OverseerAutoReplicaFailoverThread(ConfigSolr config, ZkStateReader zkStateReader,
      UpdateShardHandler updateShardHandler) {
    this.zkStateReader = zkStateReader;
    
    this.workLoopDelay = config.getAutoReplicaFailoverWorkLoopDelay();
    this.waitAfterExpiration = config.getAutoReplicaFailoverWaitAfterExpiration();
    int badNodeExpiration = config.getAutoReplicaFailoverBadNodeExpiration();
    
    log.info(
        "Starting "
            + this.getClass().getSimpleName()
            + " autoReplicaFailoverWorkLoopDelay={} autoReplicaFailoverWaitAfterExpiration={} autoReplicaFailoverBadNodeExpiration={}",
        workLoopDelay, waitAfterExpiration, badNodeExpiration);

    baseUrlForBadNodes = CacheBuilder.newBuilder()
        .concurrencyLevel(1).expireAfterWrite(badNodeExpiration, TimeUnit.MILLISECONDS).build();
    
    // TODO: Speed up our work loop when live_nodes changes??

    updateExecutor = updateShardHandler.getUpdateExecutor();

    
    // TODO: perhaps do a health ping periodically to each node (scaryish)
    // And/OR work on JIRA issue around self health checks (SOLR-5805)
  }
  
  @Override
  public void run() {
    
    while (!this.isClosed) {
      // work loop
      log.debug("do " + this.getClass().getSimpleName() + " work loop");

      // every n, look at state and make add / remove calls

      try {
        doWork();
      } catch (Exception e) {
        SolrException.log(log, this.getClass().getSimpleName()
            + " had an error in its thread work loop.", e);
      }
      
      if (!this.isClosed) {
        try {
          Thread.sleep(workLoopDelay);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }
  
  private void doWork() {
    
    // TODO: extract to configurable strategy class ??
    ClusterState clusterState = zkStateReader.getClusterState();
    if (clusterState != null) {
      if (lastClusterStateVersion == clusterState.getZkClusterStateVersion() && baseUrlForBadNodes.size() == 0) {
        // nothing has changed, no work to do
        return;
      }
      
      lastClusterStateVersion = clusterState.getZkClusterStateVersion();
      Set<String> collections = clusterState.getCollections();
      for (final String collection : collections) {
        DocCollection docCollection = clusterState.getCollection(collection);
        if (!docCollection.getAutoAddReplicas()) {
          continue;
        }
        if (docCollection.getReplicationFactor() == null) {
          log.debug("Skipping collection because it has no defined replicationFactor, name={}", docCollection.getName());
          continue;
        }
        log.debug("Found collection, name={} replicationFactor=", collection, docCollection.getReplicationFactor());
        
        Collection<Slice> slices = docCollection.getSlices();
        for (Slice slice : slices) {
          if (slice.getState().equals(Slice.ACTIVE)) {
            
            final Collection<DownReplica> downReplicas = new ArrayList<DownReplica>();
            
            int goodReplicas = findDownReplicasInSlice(clusterState, docCollection, slice, downReplicas);
            
            log.debug("replicationFactor={} goodReplicaCount={}", docCollection.getReplicationFactor(), goodReplicas);
            
            if (downReplicas.size() > 0 && goodReplicas < docCollection.getReplicationFactor()) {
              // badReplicaMap.put(collection, badReplicas);
              processBadReplicas(collection, downReplicas);
            } else if (goodReplicas > docCollection.getReplicationFactor()) {
              log.debug("There are too many replicas");
            }
          }
        }
      }
     
    }
  }

  private void processBadReplicas(final String collection, final Collection<DownReplica> badReplicas) {
    for (DownReplica badReplica : badReplicas) {
      log.debug("process down replica {}", badReplica.replica.getName());
      String baseUrl = badReplica.replica.getStr(ZkStateReader.BASE_URL_PROP);
      Long wentBadAtNS = baseUrlForBadNodes.getIfPresent(baseUrl);
      if (wentBadAtNS == null) {
        log.warn("Replica {} may need to failover.",
            badReplica.replica.getName());
        baseUrlForBadNodes.put(baseUrl, System.nanoTime());
        
      } else {
        
        long elasped = System.nanoTime() - wentBadAtNS;
        if (elasped < TimeUnit.NANOSECONDS.convert(waitAfterExpiration, TimeUnit.MILLISECONDS)) {
          // protect against ZK 'flapping', startup and shutdown
          log.debug("Looks troublesome...continue. Elapsed={}", elasped + "ns");
        } else {
          log.debug("We need to add a replica. Elapsed={}", elasped + "ns");
          
          if (addReplica(collection, badReplica)) {
            baseUrlForBadNodes.invalidate(baseUrl);
          }
        }
      }
    }
  }

  private boolean addReplica(final String collection, DownReplica badReplica) {
    // first find best home - first strategy, sort by number of cores
    // hosted where maxCoresPerNode is not violated
    final String createUrl = getBestCreateUrl(zkStateReader, badReplica);
    if (createUrl == null) {
      log.warn("Could not find a node to create new replica on.");
      return false;
    }
    
    // NOTE: we send the absolute path, which will slightly change
    // behavior of these cores as they won't respond to changes
    // in the solr.hdfs.home sys prop as they would have.
    final String dataDir = badReplica.replica.getStr("dataDir");
    final String ulogDir = badReplica.replica.getStr("ulogDir");
    final String coreNodeName = badReplica.replica.getName();
    if (dataDir != null) {
      // need an async request - full shard goes down leader election
      final String coreName = badReplica.replica.getStr(ZkStateReader.CORE_NAME_PROP);
      log.debug("submit call to {}", createUrl);
      updateExecutor.submit(new Callable<Boolean>() {
        
        @Override
        public Boolean call() {
          return createSolrCore(collection, createUrl, dataDir, ulogDir, coreNodeName, coreName);
        }
      });
      
      // wait to see state for core we just created
      boolean success = ClusterStateUtil.waitToSeeLive(zkStateReader, collection, coreNodeName, createUrl, 30000);
      if (!success) {
        log.error("Creating new replica appears to have failed, timed out waiting to see created SolrCore register in the clusterstate.");
        return false;
      }
      return true;
    }
    
    log.warn("Could not find dataDir or ulogDir in cluster state.");
    
    return false;
  }

  private static int findDownReplicasInSlice(ClusterState clusterState, DocCollection collection, Slice slice, final Collection<DownReplica> badReplicas) {
    int goodReplicas = 0;
    Collection<Replica> replicas = slice.getReplicas();
    if (replicas != null) {
      for (Replica replica : replicas) {
        // on a live node?
        boolean live = clusterState.liveNodesContain(replica.getNodeName());
        String state = replica.getStr(ZkStateReader.STATE_PROP);
        
        boolean okayState = (state.equals(ZkStateReader.DOWN)
            || state.equals(ZkStateReader.RECOVERING) || state
            .equals(ZkStateReader.ACTIVE));
        
        log.debug("Process replica name={} live={} state={}", replica.getName(), live, state);
        
        if (live && okayState) {
          goodReplicas++;
        } else {
          DownReplica badReplica = new DownReplica();
          badReplica.replica = replica;
          badReplica.slice = slice;
          badReplica.collection = collection;
          badReplicas.add(badReplica);
        }
      }
    }
    log.debug("bad replicas for slice {}", badReplicas);
    return goodReplicas;
  }
  
  /**
   * 
   * @return the best node to replace the badReplica on or null if there is no
   *         such node
   */
  static String getBestCreateUrl(ZkStateReader zkStateReader, DownReplica badReplica) {
    assert badReplica != null;
    assert badReplica.collection != null;
    assert badReplica.slice != null;
    Map<String,Counts> counts = new HashMap<>();
    ValueComparator vc = new ValueComparator(counts);
    
    Set<String> liveNodes = new HashSet<>(zkStateReader.getClusterState().getLiveNodes());
    
    ClusterState clusterState = zkStateReader.getClusterState();
    if (clusterState != null) {
      Set<String> collections = clusterState.getCollections();
      for (String collection : collections) {
        log.debug("look at collection {} as possible create candidate", collection); 
        DocCollection docCollection = clusterState.getCollection(collection);
        // TODO - only operate on collections with sharedfs failover = true ??
        Collection<Slice> slices = docCollection.getSlices();
        for (Slice slice : slices) {
          // only look at active shards
          if (slice.getState().equals(Slice.ACTIVE)) {
            log.debug("look at slice {} as possible create candidate", slice.getName()); 
            Collection<Replica> replicas = slice.getReplicas();

            for (Replica replica : replicas) {
              liveNodes.remove(replica.getNodeName());
              if (replica.getStr(ZkStateReader.BASE_URL_PROP).equals(
                  badReplica.replica.getStr(ZkStateReader.BASE_URL_PROP))) {
                continue;
              }
              String baseUrl = replica.getStr(ZkStateReader.BASE_URL_PROP);
              // on a live node?
              log.debug("nodename={} livenodes={}", replica.getNodeName(), clusterState.getLiveNodes());
              boolean live = clusterState.liveNodesContain(replica.getNodeName());
              log.debug("look at replica {} as possible create candidate, live={}", replica.getName(), live); 
              if (live) {
                Counts cnt = counts.get(baseUrl);
                if (cnt == null) {
                  cnt = new Counts();
                }
                if (badReplica.collection.getName().equals(collection)) {
                  cnt.negRankingWeight += 3;
                  cnt.collectionShardsOnNode += 1;
                } else {
                  cnt.negRankingWeight += 1;
                }
                if (badReplica.collection.getName().equals(collection) && badReplica.slice.getName().equals(slice.getName())) {
                  cnt.ourReplicas++;
                }
                
                // TODO: this is collection wide and we want to take into
                // account cluster wide - use new cluster sys prop
                int maxShardsPerNode = docCollection.getMaxShardsPerNode();
                log.debug("max shards per node={} good replicas={}", maxShardsPerNode, cnt);
                
                Collection<Replica> badSliceReplicas = null;
                DocCollection c = clusterState.getCollection(badReplica.collection.getName());
                if (c != null) {
                  Slice s = c.getSlice(badReplica.slice.getName());
                  if (s != null) {
                    badSliceReplicas = s.getReplicas();
                  }
                }
                boolean alreadyExistsOnNode = replicaAlreadyExistsOnNode(zkStateReader.getClusterState(), badSliceReplicas, badReplica, baseUrl);
                if (alreadyExistsOnNode || cnt.collectionShardsOnNode >= maxShardsPerNode) {
                  counts.remove(replica.getStr(ZkStateReader.BASE_URL_PROP));
                } else {
                  counts.put(replica.getStr(ZkStateReader.BASE_URL_PROP), cnt);
                }
              }
            }
          }
        }
      }
    }
    
    for (String node : liveNodes) {
      counts.put(zkStateReader.getBaseUrlForNodeName(node), new Counts(0, 0));
    }
    
    if (counts.size() == 0) {
      return null;
    }
    
    Map<String,Counts> sortedCounts = new TreeMap<>(vc);
    sortedCounts.putAll(counts);
    
    log.debug("empty nodes={}", liveNodes);
    log.debug("sorted hosts={}", sortedCounts);
    
    return sortedCounts.keySet().iterator().next();
  }
  
  private static boolean replicaAlreadyExistsOnNode(ClusterState clusterState, Collection<Replica> replicas, DownReplica badReplica, String baseUrl) {
    if (replicas != null) {
      log.debug("check if replica already exists on node using replicas {}", getNames(replicas));
      for (Replica replica : replicas) {
        if (!replica.getName().equals(badReplica.replica.getName()) && replica.getStr(ZkStateReader.BASE_URL_PROP).equals(baseUrl)
            && clusterState.liveNodesContain(replica.getNodeName())
            && (replica.getStr(ZkStateReader.STATE_PROP).equals(
                ZkStateReader.ACTIVE)
                || replica.getStr(ZkStateReader.STATE_PROP).equals(
                    ZkStateReader.DOWN) || replica.getStr(
                ZkStateReader.STATE_PROP).equals(ZkStateReader.RECOVERING))) {
          log.debug("replica already exists on node, bad replica={}, existing replica={}, node name={}", badReplica.replica.getName(), replica.getName(), replica.getNodeName());
          return true;
        }
      }
    }
    log.debug("replica does not yet exist on node: {}", baseUrl);
    return false;
  }
  
  private static Object getNames(Collection<Replica> replicas) {
    Set<String> names = new HashSet<>(replicas.size());
    for (Replica replica : replicas) {
      names.add(replica.getName());
    }
    return names;
  }

  private boolean createSolrCore(final String collection,
      final String createUrl, final String dataDir, final String ulogDir,
      final String coreNodeName, final String coreName) {
    HttpSolrServer server = null;
    try {
      log.debug("create url={}", createUrl);
      server = new HttpSolrServer(createUrl);
      server.setConnectionTimeout(30000);
      server.setSoTimeout(60000);
      Create createCmd = new Create();
      createCmd.setCollection(collection);
      createCmd.setCoreNodeName(coreNodeName);
      // TODO: how do we ensure unique coreName
      // for now, the collections API will use unique names
      createCmd.setCoreName(coreName);
      createCmd.setDataDir(dataDir);
      createCmd.setUlogDir(ulogDir);
      server.request(createCmd);
    } catch (Exception e) {
      SolrException.log(log, "Exception trying to create new replica on " + createUrl, e);
      return false;
    } finally {
      if (server != null) {
        server.shutdown();
      }
    }
    return true;
  }
  
  private static class ValueComparator implements Comparator<String> {
    Map<String,Counts> map;
    
    public ValueComparator(Map<String,Counts> map) {
      this.map = map;
    }
    
    public int compare(String a, String b) {
      if (map.get(a).negRankingWeight >= map.get(b).negRankingWeight) {
        return 1;
      } else {
        return -1;
      }
    }
  }
  
  @Override
  public void close() {
    isClosed = true;
  }
  
  public boolean isClosed() {
    return isClosed;
  }
  
  
  private static class Counts {
    int collectionShardsOnNode = 0;
    int negRankingWeight = 0;
    int ourReplicas = 0;
    
    private Counts() {
      
    }
    
    private Counts(int totalReplicas, int ourReplicas) {
      this.negRankingWeight = totalReplicas;
      this.ourReplicas = ourReplicas;
    }
    
    @Override
    public String toString() {
      return "Counts [negRankingWeight=" + negRankingWeight + ", sameSliceCount="
          + ourReplicas + "]";
    }
  }
  
  static class DownReplica {
    Replica replica;
    Slice slice;
    DocCollection collection;
    
    @Override
    public String toString() {
      return "DownReplica [replica=" + replica.getName() + ", slice="
          + slice.getName() + ", collection=" + collection.getName() + "]";
    }
  }
  
}
