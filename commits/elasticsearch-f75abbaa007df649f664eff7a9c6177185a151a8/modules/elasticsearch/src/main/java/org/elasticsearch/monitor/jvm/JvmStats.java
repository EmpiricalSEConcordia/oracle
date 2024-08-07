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

package org.elasticsearch.monitor.jvm;

import org.elasticsearch.common.collect.Iterators;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.unit.SizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.builder.XContentBuilder;

import java.io.IOException;
import java.io.Serializable;
import java.lang.management.*;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author kimchy (shay.banon)
 */
public class JvmStats implements Streamable, Serializable, ToXContent {

    private static RuntimeMXBean runtimeMXBean;
    private static MemoryMXBean memoryMXBean;
    private static ThreadMXBean threadMXBean;

    static {
        runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        threadMXBean = ManagementFactory.getThreadMXBean();
    }

    public static JvmStats jvmStats() {
        JvmStats stats = new JvmStats(System.currentTimeMillis(), runtimeMXBean.getUptime());
        stats.mem = new Mem();
        MemoryUsage memUsage = memoryMXBean.getHeapMemoryUsage();
        stats.mem.heapUsed = memUsage.getUsed();
        stats.mem.heapCommitted = memUsage.getCommitted();
        memUsage = memoryMXBean.getNonHeapMemoryUsage();
        stats.mem.nonHeapUsed = memUsage.getUsed();
        stats.mem.nonHeapCommitted = memUsage.getCommitted();

        stats.threads = new Threads();
        stats.threads.count = threadMXBean.getThreadCount();
        stats.threads.peakCount = threadMXBean.getPeakThreadCount();

        List<GarbageCollectorMXBean> gcMxBeans = ManagementFactory.getGarbageCollectorMXBeans();
        stats.gc = new GarbageCollectors();
        stats.gc.collectors = new GarbageCollector[gcMxBeans.size()];
        for (int i = 0; i < stats.gc.collectors.length; i++) {
            GarbageCollectorMXBean gcMxBean = gcMxBeans.get(i);
            stats.gc.collectors[i] = new GarbageCollector();
            stats.gc.collectors[i].name = gcMxBean.getName();
            stats.gc.collectors[i].collectionCount = gcMxBean.getCollectionCount();
            stats.gc.collectors[i].collectionTime = gcMxBean.getCollectionTime();
        }

        return stats;
    }

    long timestamp = -1;

    long uptime;

    Mem mem;

    Threads threads;

    GarbageCollectors gc;

    private JvmStats() {
    }

    public JvmStats(long timestamp, long uptime) {
        this.timestamp = timestamp;
        this.uptime = uptime;
    }

    public long timestamp() {
        return timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public TimeValue uptime() {
        return new TimeValue(uptime);
    }

    public TimeValue getUptime() {
        return uptime();
    }

    public Mem mem() {
        return this.mem;
    }

    public Mem getMem() {
        return mem();
    }

    public Threads threads() {
        return threads;
    }

    public Threads getThreads() {
        return threads();
    }

    public GarbageCollectors gc() {
        return gc;
    }

    public GarbageCollectors getGc() {
        return gc();
    }

    @Override public void toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject("jvm");
        builder.field("timestamp", timestamp);
        builder.field("uptime", uptime().format());
        builder.field("uptime_in_millis", uptime().millis());
        if (mem != null) {
            builder.startObject("mem");
            builder.field("heap_used", mem.heapUsed().toString());
            builder.field("heap_used_in_bytes", mem.heapUsed().bytes());
            builder.field("heap_committed", mem.heapCommitted().toString());
            builder.field("heap_committed_in_bytes", mem.heapCommitted().bytes());

            builder.field("non_heap_used", mem.nonHeapUsed().toString());
            builder.field("non_heap_used_in_bytes", mem.nonHeapUsed().bytes());
            builder.field("non_heap_committed", mem.nonHeapCommitted().toString());
            builder.field("non_heap_committed_in_bytes", mem.nonHeapCommitted().bytes());
            builder.endObject();
        }
        if (threads != null) {
            builder.startObject("threads");
            builder.field("count", threads.count());
            builder.field("peak_count", threads.peakCount());
            builder.endObject();
        }
        if (gc != null) {
            builder.startObject("gc");
            builder.field("collection_count", gc.collectionCount());
            builder.field("collection_time", gc.collectionTime().format());
            builder.field("collection_time_in_millis", gc.collectionTime().millis());

            builder.startObject("collectors");
            for (GarbageCollector collector : gc) {
                builder.startObject(collector.name());
                builder.field("collection_count", collector.collectionCount());
                builder.field("collection_time", collector.collectionTime().format());
                builder.field("collection_time_in_millis", collector.collectionTime().millis());
                builder.endObject();
            }
            builder.endObject();

            builder.endObject();
        }
        builder.endObject();
    }

    public static JvmStats readJvmStats(StreamInput in) throws IOException {
        JvmStats jvmStats = new JvmStats();
        jvmStats.readFrom(in);
        return jvmStats;
    }

    @Override public void readFrom(StreamInput in) throws IOException {
        timestamp = in.readVLong();
        uptime = in.readVLong();

        mem = Mem.readMem(in);
        threads = Threads.readThreads(in);
        gc = GarbageCollectors.readGarbageCollectors(in);
    }

    @Override public void writeTo(StreamOutput out) throws IOException {
        out.writeVLong(timestamp);
        out.writeVLong(uptime);

        mem.writeTo(out);
        threads.writeTo(out);
        gc.writeTo(out);
    }

    public static class GarbageCollectors implements Streamable, Serializable, Iterable<GarbageCollector> {

        private GarbageCollector[] collectors;

        GarbageCollectors() {
        }

        public static GarbageCollectors readGarbageCollectors(StreamInput in) throws IOException {
            GarbageCollectors collectors = new GarbageCollectors();
            collectors.readFrom(in);
            return collectors;
        }

        @Override public void readFrom(StreamInput in) throws IOException {
            collectors = new GarbageCollector[in.readVInt()];
            for (int i = 0; i < collectors.length; i++) {
                collectors[i] = GarbageCollector.readGarbageCollector(in);
            }
        }

        @Override public void writeTo(StreamOutput out) throws IOException {
            out.writeVInt(collectors.length);
            for (GarbageCollector gc : collectors) {
                gc.writeTo(out);
            }
        }

        @Override public Iterator iterator() {
            return Iterators.forArray(collectors);
        }

        public long collectionCount() {
            long collectionCount = 0;
            for (GarbageCollector gc : collectors) {
                collectionCount += gc.collectionCount();
            }
            return collectionCount;
        }

        public TimeValue collectionTime() {
            long collectionTime = 0;
            for (GarbageCollector gc : collectors) {
                collectionTime += gc.collectionTime;
            }
            return new TimeValue(collectionTime, TimeUnit.MILLISECONDS);
        }
    }

    public static class GarbageCollector implements Streamable, Serializable {

        String name;
        long collectionCount;
        long collectionTime;

        GarbageCollector() {
        }

        public static GarbageCollector readGarbageCollector(StreamInput in) throws IOException {
            GarbageCollector gc = new GarbageCollector();
            gc.readFrom(in);
            return gc;
        }

        @Override public void readFrom(StreamInput in) throws IOException {
            name = in.readUTF();
            collectionCount = in.readVLong();
            collectionTime = in.readVLong();
        }

        @Override public void writeTo(StreamOutput out) throws IOException {
            out.writeUTF(name);
            out.writeVLong(collectionCount);
            out.writeVLong(collectionTime);
        }

        public String name() {
            return name;
        }

        public String getName() {
            return name();
        }

        public long collectionCount() {
            return collectionCount;
        }

        public long getCollectionCount() {
            return collectionCount();
        }

        public TimeValue collectionTime() {
            return new TimeValue(collectionTime, TimeUnit.MILLISECONDS);
        }

        public TimeValue getCollectionTime() {
            return collectionTime();
        }
    }

    public static class Threads implements Streamable, Serializable {

        int count;
        int peakCount;

        Threads() {
        }

        public int count() {
            return count;
        }

        public int getCount() {
            return count();
        }

        public int peakCount() {
            return peakCount;
        }

        public int getPeakCount() {
            return peakCount();
        }

        public static Threads readThreads(StreamInput in) throws IOException {
            Threads threads = new Threads();
            threads.readFrom(in);
            return threads;
        }

        @Override public void readFrom(StreamInput in) throws IOException {
            count = in.readVInt();
            peakCount = in.readVInt();
        }

        @Override public void writeTo(StreamOutput out) throws IOException {
            out.writeVInt(count);
            out.writeVInt(peakCount);
        }
    }

    public static class Mem implements Streamable, Serializable {

        long heapCommitted;
        long heapUsed;
        long nonHeapCommitted;
        long nonHeapUsed;

        Mem() {
        }

        public static Mem readMem(StreamInput in) throws IOException {
            Mem mem = new Mem();
            mem.readFrom(in);
            return mem;
        }

        @Override public void readFrom(StreamInput in) throws IOException {
            heapCommitted = in.readVLong();
            heapUsed = in.readVLong();
            nonHeapCommitted = in.readVLong();
            nonHeapUsed = in.readVLong();
        }

        @Override public void writeTo(StreamOutput out) throws IOException {
            out.writeVLong(heapCommitted);
            out.writeVLong(heapUsed);
            out.writeVLong(nonHeapCommitted);
            out.writeVLong(nonHeapUsed);
        }

        public SizeValue heapCommitted() {
            return new SizeValue(heapCommitted);
        }

        public SizeValue getHeapCommitted() {
            return heapCommitted();
        }

        public SizeValue heapUsed() {
            return new SizeValue(heapUsed);
        }

        public SizeValue getHeapUsed() {
            return heapUsed();
        }

        public SizeValue nonHeapCommitted() {
            return new SizeValue(nonHeapCommitted);
        }

        public SizeValue getNonHeapCommitted() {
            return nonHeapCommitted();
        }

        public SizeValue nonHeapUsed() {
            return new SizeValue(nonHeapUsed);
        }

        public SizeValue getNonHeapUsed() {
            return nonHeapUsed();
        }
    }
}
