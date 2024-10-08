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

package org.elasticsearch.cluster.routing;

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.carrotsearch.hppc.cursors.ObjectCursor;
import org.apache.lucene.util.CollectionUtil;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlocks;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Randomness;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.shard.ShardId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * {@link RoutingNodes} represents a copy the routing information contained in
 * the {@link ClusterState cluster state}.
 */
public class RoutingNodes implements Iterable<RoutingNode> {

    private final MetaData metaData;

    private final ClusterBlocks blocks;

    private final RoutingTable routingTable;

    private final Map<String, RoutingNode> nodesToShards = new HashMap<>();

    private final UnassignedShards unassignedShards = new UnassignedShards(this);

    private final Map<ShardId, List<ShardRouting>> assignedShards = new HashMap<>();

    private final ImmutableOpenMap<String, ClusterState.Custom> customs;

    private final boolean readOnly;

    private int inactivePrimaryCount = 0;

    private int inactiveShardCount = 0;

    private int relocatingShards = 0;

    private final Map<String, ObjectIntHashMap<String>> nodesPerAttributeNames = new HashMap<>();
    private final Map<String, Recoveries> recoveryiesPerNode = new HashMap<>();

    public RoutingNodes(ClusterState clusterState) {
        this(clusterState, true);
    }

    public RoutingNodes(ClusterState clusterState, boolean readOnly) {
        this.readOnly = readOnly;
        this.metaData = clusterState.metaData();
        this.blocks = clusterState.blocks();
        this.routingTable = clusterState.routingTable();
        this.customs = clusterState.customs();

        Map<String, List<ShardRouting>> nodesToShards = new HashMap<>();
        // fill in the nodeToShards with the "live" nodes
        for (ObjectCursor<DiscoveryNode> cursor : clusterState.nodes().dataNodes().values()) {
            nodesToShards.put(cursor.value.getId(), new ArrayList<>());
        }

        // fill in the inverse of node -> shards allocated
        // also fill replicaSet information
        for (ObjectCursor<IndexRoutingTable> indexRoutingTable : routingTable.indicesRouting().values()) {
            for (IndexShardRoutingTable indexShard : indexRoutingTable.value) {
                assert indexShard.primary != null;
                for (ShardRouting shard : indexShard) {
                    // to get all the shards belonging to an index, including the replicas,
                    // we define a replica set and keep track of it. A replica set is identified
                    // by the ShardId, as this is common for primary and replicas.
                    // A replica Set might have one (and not more) replicas with the state of RELOCATING.
                    if (shard.assignedToNode()) {
                        List<ShardRouting> entries = nodesToShards.computeIfAbsent(shard.currentNodeId(), k -> new ArrayList<>());
                        final ShardRouting sr = getRouting(shard, readOnly);
                        entries.add(sr);
                        assignedShardsAdd(sr);
                        if (shard.relocating()) {
                            relocatingShards++;
                            entries = nodesToShards.computeIfAbsent(shard.relocatingNodeId(), k -> new ArrayList<>());
                            // add the counterpart shard with relocatingNodeId reflecting the source from which
                            // it's relocating from.
                            ShardRouting targetShardRouting = shard.buildTargetRelocatingShard();
                            addInitialRecovery(targetShardRouting);
                            if (readOnly) {
                                targetShardRouting.freeze();
                            }
                            entries.add(targetShardRouting);
                            assignedShardsAdd(targetShardRouting);
                        } else if (shard.active() == false) { // shards that are initializing without being relocated
                            if (shard.primary()) {
                                inactivePrimaryCount++;
                            }
                            inactiveShardCount++;
                            addInitialRecovery(shard);
                        }
                    } else {
                        final ShardRouting sr = getRouting(shard, readOnly);
                        assignedShardsAdd(sr);
                        unassignedShards.add(sr);
                    }
                }
            }
        }
        for (Map.Entry<String, List<ShardRouting>> entry : nodesToShards.entrySet()) {
            String nodeId = entry.getKey();
            this.nodesToShards.put(nodeId, new RoutingNode(nodeId, clusterState.nodes().get(nodeId), entry.getValue()));
        }
    }

    private void addRecovery(ShardRouting routing) {
        addRecovery(routing, true, false);
    }

    private void removeRecovery(ShardRouting routing) {
        addRecovery(routing, false, false);
    }

    public void addInitialRecovery(ShardRouting routing) {
        addRecovery(routing,true, true);
    }

    private void addRecovery(final ShardRouting routing, final boolean increment, final boolean initializing) {
        final int howMany = increment ? 1 : -1;
        assert routing.initializing() : "routing must be initializing: " + routing;
        Recoveries.getOrAdd(recoveryiesPerNode, routing.currentNodeId()).addIncoming(howMany);
        final String sourceNodeId;
        if (routing.relocatingNodeId() != null) { // this is a relocation-target
            sourceNodeId = routing.relocatingNodeId();
            if (routing.primary() && increment == false) { // primary is done relocating
                int numRecoveringReplicas = 0;
                for (ShardRouting assigned : assignedShards(routing)) {
                    if (assigned.primary() == false && assigned.initializing() && assigned.relocatingNodeId() == null) {
                        numRecoveringReplicas++;
                    }
                }
                // we transfer the recoveries to the relocated primary
                recoveryiesPerNode.get(sourceNodeId).addOutgoing(-numRecoveringReplicas);
                recoveryiesPerNode.get(routing.currentNodeId()).addOutgoing(numRecoveringReplicas);
            }
        } else if (routing.primary() == false) { // primary without relocationID is initial recovery
            ShardRouting primary = findPrimary(routing);
            if (primary == null && initializing) {
                primary = routingTable.index(routing.index().getName()).shard(routing.shardId().id()).primary;
            } else if (primary == null) {
                throw new IllegalStateException("replica is initializing but primary is unassigned");
            }
            sourceNodeId = primary.currentNodeId();
        } else {
            sourceNodeId = null;
        }
        if (sourceNodeId != null) {
            Recoveries.getOrAdd(recoveryiesPerNode, sourceNodeId).addOutgoing(howMany);
        }
    }

    public int getIncomingRecoveries(String nodeId) {
        return recoveryiesPerNode.getOrDefault(nodeId, Recoveries.EMPTY).getIncoming();
    }

    public int getOutgoingRecoveries(String nodeId) {
        return recoveryiesPerNode.getOrDefault(nodeId, Recoveries.EMPTY).getOutgoing();
    }

    private ShardRouting findPrimary(ShardRouting routing) {
        List<ShardRouting> shardRoutings = assignedShards.get(routing.shardId());
        ShardRouting primary = null;
        if (shardRoutings != null) {
            for (ShardRouting shardRouting : shardRoutings) {
                if (shardRouting.primary()) {
                    if (shardRouting.active()) {
                        return shardRouting;
                    } else if (primary == null) {
                        primary = shardRouting;
                    } else if (primary.relocatingNodeId() != null) {
                        primary = shardRouting;
                    }
                }
            }
        }
        return primary;
    }

    private static ShardRouting getRouting(ShardRouting src, boolean readOnly) {
        if (readOnly) {
            src.freeze(); // we just freeze and reuse this instance if we are read only
        } else {
            src = new ShardRouting(src);
        }
        return src;
    }

    @Override
    public Iterator<RoutingNode> iterator() {
        return Collections.unmodifiableCollection(nodesToShards.values()).iterator();
    }

    public RoutingTable routingTable() {
        return routingTable;
    }

    public RoutingTable getRoutingTable() {
        return routingTable();
    }

    public MetaData metaData() {
        return this.metaData;
    }

    public MetaData getMetaData() {
        return metaData();
    }

    public ClusterBlocks blocks() {
        return this.blocks;
    }

    public ClusterBlocks getBlocks() {
        return this.blocks;
    }

    public ImmutableOpenMap<String, ClusterState.Custom> customs() {
        return this.customs;
    }

    public <T extends ClusterState.Custom> T custom(String type) { return (T) customs.get(type); }

    public UnassignedShards unassigned() {
        return this.unassignedShards;
    }

    public RoutingNodesIterator nodes() {
        return new RoutingNodesIterator(nodesToShards.values().iterator());
    }

    public RoutingNode node(String nodeId) {
        return nodesToShards.get(nodeId);
    }

    public ObjectIntHashMap<String> nodesPerAttributesCounts(String attributeName) {
        ObjectIntHashMap<String> nodesPerAttributesCounts = nodesPerAttributeNames.get(attributeName);
        if (nodesPerAttributesCounts != null) {
            return nodesPerAttributesCounts;
        }
        nodesPerAttributesCounts = new ObjectIntHashMap<>();
        for (RoutingNode routingNode : this) {
            String attrValue = routingNode.node().getAttributes().get(attributeName);
            nodesPerAttributesCounts.addTo(attrValue, 1);
        }
        nodesPerAttributeNames.put(attributeName, nodesPerAttributesCounts);
        return nodesPerAttributesCounts;
    }

    /**
     * Returns <code>true</code> iff this {@link RoutingNodes} instance has any unassigned primaries even if the
     * primaries are marked as temporarily ignored.
     */
    public boolean hasUnassignedPrimaries() {
        return unassignedShards.getNumPrimaries() + unassignedShards.getNumIgnoredPrimaries() > 0;
    }

    /**
     * Returns <code>true</code> iff this {@link RoutingNodes} instance has any unassigned shards even if the
     * shards are marked as temporarily ignored.
     * @see UnassignedShards#isEmpty()
     * @see UnassignedShards#isIgnoredEmpty()
     */
    public boolean hasUnassignedShards() {
        return unassignedShards.isEmpty() == false || unassignedShards.isIgnoredEmpty() == false;
    }

    public boolean hasInactivePrimaries() {
        return inactivePrimaryCount > 0;
    }

    public boolean hasInactiveShards() {
        return inactiveShardCount > 0;
    }

    public int getRelocatingShardCount() {
        return relocatingShards;
    }

    /**
     * Returns the active primary shard for the given ShardRouting or <code>null</code> if
     * no primary is found or the primary is not active.
     */
    public ShardRouting activePrimary(ShardRouting shard) {
        for (ShardRouting shardRouting : assignedShards(shard.shardId())) {
            if (shardRouting.primary() && shardRouting.active()) {
                return shardRouting;
            }
        }
        return null;
    }

    /**
     * Returns one active replica shard for the given ShardRouting shard ID or <code>null</code> if
     * no active replica is found.
     */
    public ShardRouting activeReplica(ShardRouting shard) {
        for (ShardRouting shardRouting : assignedShards(shard.shardId())) {
            if (!shardRouting.primary() && shardRouting.active()) {
                return shardRouting;
            }
        }
        return null;
    }

    /**
     * Returns all shards that are not in the state UNASSIGNED with the same shard
     * ID as the given shard.
     */
    public Iterable<ShardRouting> assignedShards(ShardRouting shard) {
        return assignedShards(shard.shardId());
    }

    /**
     * Returns <code>true</code> iff all replicas are active for the given shard routing. Otherwise <code>false</code>
     */
    public boolean allReplicasActive(ShardRouting shardRouting) {
        final List<ShardRouting> shards = assignedShards(shardRouting.shardId());
        if (shards.isEmpty() || shards.size() < this.routingTable.index(shardRouting.index().getName()).shard(shardRouting.id()).size()) {
            return false; // if we are empty nothing is active if we have less than total at least one is unassigned
        }
        for (ShardRouting shard : shards) {
            if (!shard.active()) {
                return false;
            }
        }
        return true;
    }

    public List<ShardRouting> shards(Predicate<ShardRouting> predicate) {
        List<ShardRouting> shards = new ArrayList<>();
        for (RoutingNode routingNode : this) {
            for (ShardRouting shardRouting : routingNode) {
                if (predicate.test(shardRouting)) {
                    shards.add(shardRouting);
                }
            }
        }
        return shards;
    }

    public List<ShardRouting> shardsWithState(ShardRoutingState... state) {
        // TODO these are used on tests only - move into utils class
        List<ShardRouting> shards = new ArrayList<>();
        for (RoutingNode routingNode : this) {
            shards.addAll(routingNode.shardsWithState(state));
        }
        for (ShardRoutingState s : state) {
            if (s == ShardRoutingState.UNASSIGNED) {
                unassigned().forEach(shards::add);
                break;
            }
        }
        return shards;
    }

    public List<ShardRouting> shardsWithState(String index, ShardRoutingState... state) {
        // TODO these are used on tests only - move into utils class
        List<ShardRouting> shards = new ArrayList<>();
        for (RoutingNode routingNode : this) {
            shards.addAll(routingNode.shardsWithState(index, state));
        }
        for (ShardRoutingState s : state) {
            if (s == ShardRoutingState.UNASSIGNED) {
                for (ShardRouting unassignedShard : unassignedShards) {
                    if (unassignedShard.index().equals(index)) {
                        shards.add(unassignedShard);
                    }
                }
                break;
            }
        }
        return shards;
    }

    public String prettyPrint() {
        StringBuilder sb = new StringBuilder("routing_nodes:\n");
        for (RoutingNode routingNode : this) {
            sb.append(routingNode.prettyPrint());
        }
        sb.append("---- unassigned\n");
        for (ShardRouting shardEntry : unassignedShards) {
            sb.append("--------").append(shardEntry.shortSummary()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Moves a shard from unassigned to initialize state
     *
     * @param existingAllocationId allocation id to use. If null, a fresh allocation id is generated.
     */
    public void initialize(ShardRouting shard, String nodeId, @Nullable String existingAllocationId, long expectedSize) {
        ensureMutable();
        assert shard.unassigned() : shard;
        shard.initialize(nodeId, existingAllocationId, expectedSize);
        node(nodeId).add(shard);
        inactiveShardCount++;
        if (shard.primary()) {
            inactivePrimaryCount++;
        }
        addRecovery(shard);
        assignedShardsAdd(shard);
    }

    /**
     * Relocate a shard to another node, adding the target initializing
     * shard as well as assigning it. And returning the target initializing
     * shard.
     */
    public ShardRouting relocate(ShardRouting shard, String nodeId, long expectedShardSize) {
        ensureMutable();
        relocatingShards++;
        shard.relocate(nodeId, expectedShardSize);
        ShardRouting target = shard.buildTargetRelocatingShard();
        node(target.currentNodeId()).add(target);
        assignedShardsAdd(target);
        addRecovery(target);
        return target;
    }

    /**
     * Mark a shard as started and adjusts internal statistics.
     */
    public void started(ShardRouting shard) {
        ensureMutable();
        assert !shard.active() : "expected an initializing shard " + shard;
        if (shard.relocatingNodeId() == null) {
            // if this is not a target shard for relocation, we need to update statistics
            inactiveShardCount--;
            if (shard.primary()) {
                inactivePrimaryCount--;
            }
        }
        removeRecovery(shard);
        shard.moveToStarted();
    }



    /**
     * Cancels a relocation of a shard that shard must relocating.
     */
    public void cancelRelocation(ShardRouting shard) {
        ensureMutable();
        relocatingShards--;
        shard.cancelRelocation();
    }

    /**
     * swaps the status of a shard, making replicas primary and vice versa.
     *
     * @param shards the shard to have its primary status swapped.
     */
    public void swapPrimaryFlag(ShardRouting... shards) {
        ensureMutable();
        for (ShardRouting shard : shards) {
            if (shard.primary()) {
                shard.moveFromPrimary();
                if (shard.unassigned()) {
                    unassignedShards.primaries--;
                }
            } else {
                shard.moveToPrimary();
                if (shard.unassigned()) {
                    unassignedShards.primaries++;
                }
            }
        }
    }

    private static final List<ShardRouting> EMPTY = Collections.emptyList();

    private List<ShardRouting> assignedShards(ShardId shardId) {
        final List<ShardRouting> replicaSet = assignedShards.get(shardId);
        return replicaSet == null ? EMPTY : Collections.unmodifiableList(replicaSet);
    }

    /**
     * Cancels the give shard from the Routing nodes internal statistics and cancels
     * the relocation if the shard is relocating.
     */
    private void remove(ShardRouting shard) {
        ensureMutable();
        if (!shard.active() && shard.relocatingNodeId() == null) {
            inactiveShardCount--;
            assert inactiveShardCount >= 0;
            if (shard.primary()) {
                inactivePrimaryCount--;
            }
        } else if (shard.relocating()) {
            cancelRelocation(shard);
        }
        assignedShardsRemove(shard);
        if (shard.initializing()) {
            removeRecovery(shard);
        }
    }

    private void assignedShardsAdd(ShardRouting shard) {
        if (shard.unassigned()) {
            // no unassigned
            return;
        }
        List<ShardRouting> shards = assignedShards.computeIfAbsent(shard.shardId(), k -> new ArrayList<>());
        assert assertInstanceNotInList(shard, shards);
        shards.add(shard);
    }

    private boolean assertInstanceNotInList(ShardRouting shard, List<ShardRouting> shards) {
        for (ShardRouting s : shards) {
            assert s != shard;
        }
        return true;
    }

    private void assignedShardsRemove(ShardRouting shard) {
        ensureMutable();
        final List<ShardRouting> replicaSet = assignedShards.get(shard.shardId());
        if (replicaSet != null) {
            final Iterator<ShardRouting> iterator = replicaSet.iterator();
            while(iterator.hasNext()) {
                // yes we check identity here
                if (shard == iterator.next()) {
                    iterator.remove();
                    return;
                }
            }
            assert false : "Illegal state";
        }
    }

    public boolean isKnown(DiscoveryNode node) {
        return nodesToShards.containsKey(node.getId());
    }

    public void addNode(DiscoveryNode node) {
        ensureMutable();
        RoutingNode routingNode = new RoutingNode(node.getId(), node);
        nodesToShards.put(routingNode.nodeId(), routingNode);
    }

    public RoutingNodeIterator routingNodeIter(String nodeId) {
        final RoutingNode routingNode = nodesToShards.get(nodeId);
        if (routingNode == null) {
            return null;
        }
        return new RoutingNodeIterator(routingNode);
    }

    public RoutingNode[] toArray() {
        return nodesToShards.values().toArray(new RoutingNode[nodesToShards.size()]);
    }

    public void reinitShadowPrimary(ShardRouting candidate) {
        ensureMutable();
        if (candidate.relocating()) {
            cancelRelocation(candidate);
        }
        candidate.reinitializeShard();
        inactivePrimaryCount++;
        inactiveShardCount++;

    }

    /**
     * Returns the number of routing nodes
     */
    public int size() {
        return nodesToShards.size();
    }

    public static final class UnassignedShards implements Iterable<ShardRouting>  {

        private final RoutingNodes nodes;
        private final List<ShardRouting> unassigned;
        private final List<ShardRouting> ignored;

        private int primaries = 0;
        private int ignoredPrimaries = 0;

        public UnassignedShards(RoutingNodes nodes) {
            this.nodes = nodes;
            unassigned = new ArrayList<>();
            ignored = new ArrayList<>();
        }

        public void add(ShardRouting shardRouting) {
            if(shardRouting.primary()) {
                primaries++;
            }
            unassigned.add(shardRouting);
        }

        public void sort(Comparator<ShardRouting> comparator) {
            CollectionUtil.timSort(unassigned, comparator);
        }

        /**
         * Returns the size of the non-ignored unassigned shards
         */
        public int size() { return unassigned.size(); }

        /**
         * Returns the size of the temporarily marked as ignored unassigned shards
         */
        public int ignoredSize() { return ignored.size(); }

        /**
         * Returns the number of non-ignored unassigned primaries
         */
        public int getNumPrimaries() {
            return primaries;
        }

        /**
         * Returns the number of temporarily marked as ignored unassigned primaries
         */
        public int getNumIgnoredPrimaries() { return ignoredPrimaries; }

        @Override
        public UnassignedIterator iterator() {
            return new UnassignedIterator();
        }

        /**
         * The list of ignored unassigned shards (read only). The ignored unassigned shards
         * are not part of the formal unassigned list, but are kept around and used to build
         * back the list of unassigned shards as part of the routing table.
         */
        public List<ShardRouting> ignored() {
            return Collections.unmodifiableList(ignored);
        }

        /**
         * Marks a shard as temporarily ignored and adds it to the ignore unassigned list.
         * Should be used with caution, typically,
         * the correct usage is to removeAndIgnore from the iterator.
         * @see #ignored()
         * @see UnassignedIterator#removeAndIgnore()
         * @see #isIgnoredEmpty()
         */
        public void ignoreShard(ShardRouting shard) {
            if (shard.primary()) {
                ignoredPrimaries++;
            }
            ignored.add(shard);
        }

        public class UnassignedIterator implements Iterator<ShardRouting> {

            private final Iterator<ShardRouting> iterator;
            private ShardRouting current;

            public UnassignedIterator() {
                this.iterator = unassigned.iterator();
            }

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public ShardRouting next() {
                return current = iterator.next();
            }

            /**
             * Initializes the current unassigned shard and moves it from the unassigned list.
             *
             * @param existingAllocationId allocation id to use. If null, a fresh allocation id is generated.
             */
            public void initialize(String nodeId, @Nullable String existingAllocationId, long expectedShardSize) {
                innerRemove();
                nodes.initialize(new ShardRouting(current), nodeId, existingAllocationId, expectedShardSize);
            }

            /**
             * Removes and ignores the unassigned shard (will be ignored for this run, but
             * will be added back to unassigned once the metadata is constructed again).
             * Typically this is used when an allocation decision prevents a shard from being allocated such
             * that subsequent consumers of this API won't try to allocate this shard again.
             */
            public void removeAndIgnore() {
                innerRemove();
                ignoreShard(current);
            }

            /**
             * Unsupported operation, just there for the interface. Use {@link #removeAndIgnore()} or
             * {@link #initialize(String, String, long)}.
             */
            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove is not supported in unassigned iterator, use removeAndIgnore or initialize");
            }

            private void innerRemove() {
                nodes.ensureMutable();
                iterator.remove();
                if (current.primary()) {
                    primaries--;
                }
            }
        }

        /**
         * Returns <code>true</code> iff this collection contains one or more non-ignored unassigned shards.
         */
        public boolean isEmpty() {
            return unassigned.isEmpty();
        }

        /**
         * Returns <code>true</code> iff any unassigned shards are marked as temporarily ignored.
         * @see UnassignedShards#ignoreShard(ShardRouting)
         * @see UnassignedIterator#removeAndIgnore()
         */
        public boolean isIgnoredEmpty() {
            return ignored.isEmpty();
        }

        public void shuffle() {
            Randomness.shuffle(unassigned);
        }

        /**
         * Drains all unassigned shards and returns it.
         * This method will not drain ignored shards.
         */
        public ShardRouting[] drain() {
            ShardRouting[] mutableShardRoutings = unassigned.toArray(new ShardRouting[unassigned.size()]);
            unassigned.clear();
            primaries = 0;
            return mutableShardRoutings;
        }
    }


    /**
     * Calculates RoutingNodes statistics by iterating over all {@link ShardRouting}s
     * in the cluster to ensure the book-keeping is correct.
     * For performance reasons, this should only be called from asserts
     *
     * @return this method always returns <code>true</code> or throws an assertion error. If assertion are not enabled
     *         this method does nothing.
     */
    public static boolean assertShardStats(RoutingNodes routingNodes) {
        boolean run = false;
        assert (run = true); // only run if assertions are enabled!
        if (!run) {
            return true;
        }
        int unassignedPrimaryCount = 0;
        int unassignedIgnoredPrimaryCount = 0;
        int inactivePrimaryCount = 0;
        int inactiveShardCount = 0;
        int relocating = 0;
        Map<Index, Integer> indicesAndShards = new HashMap<>();
        for (RoutingNode node : routingNodes) {
            for (ShardRouting shard : node) {
                if (!shard.active() && shard.relocatingNodeId() == null) {
                    if (!shard.relocating()) {
                        inactiveShardCount++;
                        if (shard.primary()) {
                            inactivePrimaryCount++;
                        }
                    }
                }
                if (shard.relocating()) {
                    relocating++;
                }
                Integer i = indicesAndShards.get(shard.index());
                if (i == null) {
                    i = shard.id();
                }
                indicesAndShards.put(shard.index(), Math.max(i, shard.id()));
            }
        }
        // Assert that the active shard routing are identical.
        Set<Map.Entry<Index, Integer>> entries = indicesAndShards.entrySet();
        final List<ShardRouting> shards = new ArrayList<>();
        for (Map.Entry<Index, Integer> e : entries) {
            Index index = e.getKey();
            for (int i = 0; i < e.getValue(); i++) {
                for (RoutingNode routingNode : routingNodes) {
                    for (ShardRouting shardRouting : routingNode) {
                        if (shardRouting.index().equals(index) && shardRouting.id() == i) {
                            shards.add(shardRouting);
                        }
                    }
                }
                List<ShardRouting> mutableShardRoutings = routingNodes.assignedShards(new ShardId(index, i));
                assert mutableShardRoutings.size() == shards.size();
                for (ShardRouting r : mutableShardRoutings) {
                    assert shards.contains(r);
                    shards.remove(r);
                }
                assert shards.isEmpty();
            }
        }

        for (ShardRouting shard : routingNodes.unassigned()) {
            if (shard.primary()) {
                unassignedPrimaryCount++;
            }
        }

        for (ShardRouting shard : routingNodes.unassigned().ignored()) {
            if (shard.primary()) {
                unassignedIgnoredPrimaryCount++;
            }
        }

        for (Map.Entry<String, Recoveries> recoveries : routingNodes.recoveryiesPerNode.entrySet()) {
            String node = recoveries.getKey();
            final Recoveries value = recoveries.getValue();
            int incoming = 0;
            int outgoing = 0;
            RoutingNode routingNode = routingNodes.nodesToShards.get(node);
            if (routingNode != null) { // node might have dropped out of the cluster
                for (ShardRouting routing : routingNode) {
                    if (routing.initializing()) {
                        incoming++;
                    } else if (routing.relocating()) {
                        outgoing++;
                    }
                    if (routing.primary() && (routing.initializing() && routing.relocatingNodeId() != null) == false) { // we don't count the initialization end of the primary relocation
                        List<ShardRouting> shardRoutings = routingNodes.assignedShards.get(routing.shardId());
                        for (ShardRouting assigned : shardRoutings) {
                            if (assigned.primary() == false && assigned.initializing() && assigned.relocatingNodeId() == null) {
                                outgoing++;
                            }
                        }
                    }
                }
            }
            assert incoming == value.incoming : incoming + " != " + value.incoming;
            assert outgoing == value.outgoing : outgoing + " != " + value.outgoing + " node: " + routingNode;
        }


        assert unassignedPrimaryCount == routingNodes.unassignedShards.getNumPrimaries() :
                "Unassigned primaries is [" + unassignedPrimaryCount + "] but RoutingNodes returned unassigned primaries [" + routingNodes.unassigned().getNumPrimaries() + "]";
        assert unassignedIgnoredPrimaryCount == routingNodes.unassignedShards.getNumIgnoredPrimaries() :
                "Unassigned ignored primaries is [" + unassignedIgnoredPrimaryCount + "] but RoutingNodes returned unassigned ignored primaries [" + routingNodes.unassigned().getNumIgnoredPrimaries() + "]";
        assert inactivePrimaryCount == routingNodes.inactivePrimaryCount :
                "Inactive Primary count [" + inactivePrimaryCount + "] but RoutingNodes returned inactive primaries [" + routingNodes.inactivePrimaryCount + "]";
        assert inactiveShardCount == routingNodes.inactiveShardCount :
                "Inactive Shard count [" + inactiveShardCount + "] but RoutingNodes returned inactive shards [" + routingNodes.inactiveShardCount + "]";
        assert routingNodes.getRelocatingShardCount() == relocating : "Relocating shards mismatch [" + routingNodes.getRelocatingShardCount() + "] but expected [" + relocating + "]";
        return true;
    }


    public class RoutingNodesIterator implements Iterator<RoutingNode>, Iterable<ShardRouting> {
        private RoutingNode current;
        private final Iterator<RoutingNode> delegate;

        public RoutingNodesIterator(Iterator<RoutingNode> iterator) {
            delegate = iterator;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public RoutingNode next() {
            return current = delegate.next();
        }

        public RoutingNodeIterator nodeShards() {
            return new RoutingNodeIterator(current);
        }

        @Override
        public void remove() {
           delegate.remove();
        }

        @Override
        public Iterator<ShardRouting> iterator() {
            return nodeShards();
        }
    }

    public final class RoutingNodeIterator implements Iterator<ShardRouting>, Iterable<ShardRouting> {
        private final RoutingNode iterable;
        private ShardRouting shard;
        private final Iterator<ShardRouting> delegate;
        private boolean removed = false;

        public RoutingNodeIterator(RoutingNode iterable) {
            this.delegate = iterable.mutableIterator();
            this.iterable = iterable;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public ShardRouting next() {
            removed = false;
            return shard = delegate.next();
        }

        @Override
        public void remove() {
            ensureMutable();
            delegate.remove();
            RoutingNodes.this.remove(shard);
            removed = true;
        }


        /** returns true if {@link #remove()} or {@link #moveToUnassigned(UnassignedInfo)} were called on the current shard */
        public boolean isRemoved() {
            return removed;
        }

        @Override
        public Iterator<ShardRouting> iterator() {
            return iterable.iterator();
        }

        public void moveToUnassigned(UnassignedInfo unassignedInfo) {
            ensureMutable();
            if (isRemoved() == false) {
                remove();
            }
            ShardRouting unassigned = new ShardRouting(shard); // protective copy of the mutable shard
            unassigned.moveToUnassigned(unassignedInfo);
            unassigned().add(unassigned);
        }

        public ShardRouting current() {
            return shard;
        }
    }

    private void ensureMutable() {
        if (readOnly) {
            throw new IllegalStateException("can't modify RoutingNodes - readonly");
        }
    }

    private static final class Recoveries {
        private static final Recoveries EMPTY = new Recoveries();
        private int incoming = 0;
        private int outgoing = 0;

        int getTotal() {
            return incoming + outgoing;
        }

        void addOutgoing(int howMany) {
            assert outgoing + howMany >= 0 : outgoing + howMany+ " must be >= 0";
            outgoing += howMany;
        }

        void addIncoming(int howMany) {
            assert incoming + howMany >= 0 : incoming + howMany+ " must be >= 0";
            incoming += howMany;
        }

        int getOutgoing() {
            return outgoing;
        }

        int getIncoming() {
            return incoming;
        }

        public static Recoveries getOrAdd(Map<String, Recoveries> map, String key) {
            Recoveries recoveries = map.get(key);
            if (recoveries == null) {
                recoveries = new Recoveries();
                map.put(key, recoveries);
            }
            return recoveries;
        }
     }
}
