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
package org.caffinitas.prometheusmetrics;

import com.codahale.metrics.Gauge;
import io.prometheus.client.Prometheus;

import java.lang.management.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

class JvmMetrics {
    public static void register(Consumer<MetricsContainer> registration) {
        for (MemoryPoolMXBean memoryPoolMXBean : ManagementFactory.getMemoryPoolMXBeans()) {
            String name = "JVM_MemPool_" + memoryPoolMXBean.getName().replace(' ', '_');

            MetricsContainer container = newContainer(name)
                    .addMetric(name + "_CollectionUsage_Committed", new LongGauge(() -> memoryPoolMXBean.getCollectionUsage() != null ? memoryPoolMXBean.getCollectionUsage().getCommitted() : 0L), "type", "collectionUsage", "value", "committed")
                    .addMetric(name + "_CollectionUsage_Init", new LongGauge(() -> memoryPoolMXBean.getCollectionUsage() != null ? memoryPoolMXBean.getCollectionUsage().getInit() : 0L), "type", "collectionUsage", "value", "init")
                    .addMetric(name + "_CollectionUsage_Max", new LongGauge(() -> memoryPoolMXBean.getCollectionUsage() != null ? memoryPoolMXBean.getCollectionUsage().getMax() : 0L), "type", "collectionUsage", "value", "max")
                    .addMetric(name + "_CollectionUsage_Used", new LongGauge(() -> memoryPoolMXBean.getCollectionUsage() != null ? memoryPoolMXBean.getCollectionUsage().getUsed() : 0L), "type", "collectionUsage", "value", "used")
                    //
                    .addMetric(name + "_PeakUsage_Committed", new LongGauge(() -> memoryPoolMXBean.getPeakUsage() != null ? memoryPoolMXBean.getPeakUsage().getCommitted() : 0L), "type", "peakUsage", "value", "committed")
                    .addMetric(name + "_PeakUsage_Init", new LongGauge(() -> memoryPoolMXBean.getPeakUsage() != null ? memoryPoolMXBean.getPeakUsage().getInit() : 0L), "type", "peakUsage", "value", "init")
                    .addMetric(name + "_PeakUsage_Max", new LongGauge(() -> memoryPoolMXBean.getPeakUsage() != null ? memoryPoolMXBean.getPeakUsage().getMax() : 0L), "type", "peakUsage", "value", "max")
                    .addMetric(name + "_PeakUsage_Used", new LongGauge(() -> memoryPoolMXBean.getPeakUsage() != null ? memoryPoolMXBean.getPeakUsage().getUsed() : 0L), "type", "peakUsage", "value", "used")
                    //
                    .addMetric(name + "_Usage_Committed", new LongGauge(() -> memoryPoolMXBean.getUsage() != null ? memoryPoolMXBean.getUsage().getCommitted() : 0L), "type", "usage", "value", "committed")
                    .addMetric(name + "_Usage_Init", new LongGauge(() -> memoryPoolMXBean.getUsage() != null ? memoryPoolMXBean.getUsage().getInit() : 0L), "type", "usage", "value", "init")
                    .addMetric(name + "_Usage_Max", new LongGauge(() -> memoryPoolMXBean.getUsage() != null ? memoryPoolMXBean.getUsage().getMax() : 0L), "type", "usage", "value", "max")
                    .addMetric(name + "_Usage_Used", new LongGauge(() -> memoryPoolMXBean.getUsage() != null ? memoryPoolMXBean.getUsage().getUsed() : 0L), "type", "usage", "value", "used");
            if (memoryPoolMXBean.isCollectionUsageThresholdSupported()) {
                container
                        .addMetric(name + "_CollectionUsageThreshold", new LongGauge(memoryPoolMXBean::getCollectionUsageThreshold), "value", "collectionUsageThreshold")
                        .addMetric(name + "_CollectionUsageThresholdCount", new LongGauge(memoryPoolMXBean::getCollectionUsageThresholdCount), "value", "collectionUsageThresholdCount")
                        .addMetric(name + "_CollectionUsageThresholdExceeded", new LongGauge(() -> memoryPoolMXBean.isCollectionUsageThresholdExceeded() ? 1 : 0), "value", "collectionUsageThresholdExceeded");
            }
            registration.accept(container);
        }

        for (GarbageCollectorMXBean garbageCollectorMXBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            com.sun.management.GarbageCollectorMXBean sunBean = ((com.sun.management.GarbageCollectorMXBean ) garbageCollectorMXBean);

            String name = "JVM_GC_" + garbageCollectorMXBean.getName().replace(' ', '_');
            registration.accept(newContainer(name)
                    .addMetric(name + "_CollectionCount", new ReadoutLongGauge(garbageCollectorMXBean::getCollectionCount), "type", "count", "aggregation", "none")
                    .addMetric(name + "_CollectionTime", new ReadoutLongGauge(garbageCollectorMXBean::getCollectionTime), "type", "time", "aggregation", "none")
                    .addMetric(name + "_CollectionTotalCount", new LongGauge(garbageCollectorMXBean::getCollectionCount), "type", "count", "aggregation", "sum")
                    .addMetric(name + "_CollectionTotalTime", new LongGauge(garbageCollectorMXBean::getCollectionTime), "type", "time", "aggregation", "sum")
                    .addMetric(name + "_LastGc_Duration", new LongGauge(() -> sunBean.getLastGcInfo() != null ? sunBean.getLastGcInfo().getDuration() : 0L), "type", "last_gc_duration")
                    .addMetric(name + "_LastGc_EndTime", new LongGauge(() -> sunBean.getLastGcInfo() != null ? sunBean.getLastGcInfo().getEndTime() : 0L), "type", "last_gc_end")
                    .addMetric(name + "_LastGc_StartTime", new LongGauge(() -> sunBean.getLastGcInfo() != null ? sunBean.getLastGcInfo().getStartTime() : 0L), "type", "last_gc_start")
                    .addMetric(name + "_LastGc_Id", new LongGauge(() -> sunBean.getLastGcInfo() != null ? sunBean.getLastGcInfo().getId() : 0L), "type", "last_gc_id"));
        }

        registration.accept(new MetricsContainer("JVM_ThreadInfo", "all JVM threads", Prometheus.MetricType.GAUGE) {
            class ThreadMeta {
                long blockedCount;
                long blockedTime;
                long waitedCount;
                long waitedTime;
                long cpuTime;
                long userTime;
                long allocated;

                ThreadMeta(ThreadInfo ti, long cpuTime, long userTime, long allocated) {
                    this.blockedCount = ti.getBlockedCount();
                    this.blockedTime = ti.getBlockedTime();
                    this.waitedCount = ti.getWaitedCount();
                    this.waitedTime = ti.getWaitedTime();
                    this.cpuTime = cpuTime;
                    this.userTime = userTime;
                    this.allocated = allocated;
                }

                Gauge<Long> blockedCount(ThreadInfo ti) {
                    long c = ti.getBlockedCount();
                    FixedLongGauge r = new FixedLongGauge(c - blockedCount);
                    blockedCount = c;
                    return r;
                }

                Gauge<Long> blockedTime(ThreadInfo ti) {
                    long c = ti.getBlockedTime();
                    FixedLongGauge r = new FixedLongGauge(c - blockedTime);
                    blockedTime = c;
                    return r;
                }

                Gauge<Long> blocked(ThreadInfo ti) {
                    return new FixedLongGauge(ti.getLockName() != null ? 1 : 0);
                }

                Gauge<Long> waitedCount(ThreadInfo ti) {
                    long c = ti.getWaitedCount();
                    FixedLongGauge r = new FixedLongGauge(c - waitedCount);
                    waitedCount = c;
                    return r;
                }

                Gauge<Long> waitedTime(ThreadInfo ti) {
                    long c = ti.getWaitedTime();
                    FixedLongGauge r = new FixedLongGauge(c - waitedTime);
                    waitedTime = c;
                    return r;
                }

                Gauge<Long> cpuTime(long c) {
                    FixedLongGauge r = new FixedLongGauge(c - cpuTime);
                    cpuTime = c;
                    return r;
                }

                Gauge<Long> allocated(long c) {
                    FixedLongGauge r = new FixedLongGauge(c - allocated);
                    allocated = c;
                    return r;
                }

                Gauge<Long> userTime(long c) {
                    FixedLongGauge r = new FixedLongGauge(c - userTime);
                    userTime = c;
                    return r;
                }
            }

            private final Map<Long, ThreadMeta> thrInfoMap = new HashMap<>();

            @Override
            List<MetricInfo> getMetrics() {
                com.sun.management.ThreadMXBean tmx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
                List<Long> visited = new ArrayList<>();

                long[] allTids = tmx.getAllThreadIds();
                long[] allAllocated = tmx.getThreadAllocatedBytes(allTids);
                long[] allCpuTime = tmx.getThreadCpuTime(allTids);
                long[] allUserTime = tmx.getThreadUserTime(allTids);

                List<MetricInfo> metrics = new ArrayList<>(allTids.length * 7);

                for (int i = 0; i < allTids.length; i++) {
                    long tid = allTids[i];

                    visited.add(tid);

                    ThreadInfo ti = tmx.getThreadInfo(tid);
                    String threadName = ti.getThreadName();
                    String poolName = null;
                    String perPoolId = null;
                    try {
                        for (int ci = threadName.length() - 1; ci >= 0; ci--) {
                            char c = threadName.charAt(ci);
                            if (!Character.isDigit(c) && c != ':' && c != '-') {
                                c = threadName.charAt(ci + 1);
                                if (c == ':' || c == '-')
                                    perPoolId = threadName.substring(ci + 2);
                                else
                                    perPoolId = threadName.substring(ci + 1);
                                poolName = threadName.substring(0, ci + 1);
                                break;
                            }
                        }
                    }
                    catch (StringIndexOutOfBoundsException ignored) {
                    }
                    if (poolName == null) {
                        perPoolId = threadName;
                    }

                    long cpuTime = allCpuTime[i];
                    long userTime = allUserTime[i];
                    long allocated = allAllocated[i];

                    ThreadMeta tm = thrInfoMap.get(tid);
                    if (tm == null) {
                        thrInfoMap.put(tid, tm = new ThreadMeta(ti, cpuTime, userTime, allocated));
                    }

                    metrics.add(new MetricInfo<Gauge>("", tm.allocated(allocated),
                            labels("allocated", poolName, perPoolId)));
                    metrics.add(new MetricInfo<Gauge>("", tm.blockedCount(ti),
                            labels("blockedCount", poolName, perPoolId)));
                    metrics.add(new MetricInfo<Gauge>("", tm.blockedTime(ti),
                            labels("blockedTime", poolName, perPoolId)));
                    metrics.add(new MetricInfo<Gauge>("", tm.blocked(ti),
                            labels("blocked", poolName, perPoolId)));
                    metrics.add(new MetricInfo<Gauge>("", tm.waitedCount(ti),
                            labels("waitedCount", poolName, perPoolId)));
                    metrics.add(new MetricInfo<Gauge>("", tm.waitedTime(ti),
                            labels("waitedTime", poolName, perPoolId)));
                    metrics.add(new MetricInfo<Gauge>("", tm.cpuTime(cpuTime),
                            labels("cpuTime", poolName, perPoolId)));
                    metrics.add(new MetricInfo<Gauge>("", tm.userTime(userTime),
                            labels("userTime", poolName, perPoolId)));
                }

                thrInfoMap.keySet().retainAll(visited);

                return metrics;
            }

            private String[][] labels(String type, String poolName, String perPoolId) {
                if (poolName != null)
                    return new String[][] {
                            new String[]{"type", type},
                            new String[]{"pool", poolName},
                            new String[]{"thread", perPoolId}
                    };

                return new String[][] {
                        new String[]{"type", type},
                        new String[]{"thread", perPoolId}
                };
            }
        });

        registration.accept(newContainer("JVM_Threads")
                .addMetric("JVM_DaemonThreadCount", new LongGauge(() -> ManagementFactory.getThreadMXBean().getDaemonThreadCount()), "type", "daemon")
                .addMetric("JVM_PeakThreadCount", new LongGauge(() -> ManagementFactory.getThreadMXBean().getPeakThreadCount()), "type", "peak")
                .addMetric("JVM_ThreadCount", new LongGauge(() -> ManagementFactory.getThreadMXBean().getThreadCount()), "type", "count"));

        registration.accept(singleValue("JVM_ObjectPendingFinalizationCount",
                () -> ManagementFactory.getMemoryMXBean().getObjectPendingFinalizationCount()));
        registration.accept(newContainer("JVM_HeapMemoryUsage")
                .addMetric("JVM_HeapMemoryUsage_Committed", new LongGauge(() -> ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getCommitted()), "type", "committed")
                .addMetric("JVM_HeapMemoryUsage_Init", new LongGauge(() -> ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getInit()), "type", "init")
                .addMetric("JVM_HeapMemoryUsage_Max", new LongGauge(() -> ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax()), "type", "max")
                .addMetric("JVM_HeapMemoryUsage_Used", new LongGauge(() -> ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()), "type", "used"));
        registration.accept(newContainer("JVM_NonHeapMemoryUsage")
                .addMetric("JVM_NonHeapMemoryUsage_Committed", new LongGauge(() -> ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getCommitted()), "type", "committed")
                .addMetric("JVM_NonHeapMemoryUsage_Init", new LongGauge(() -> ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getInit()), "type", "init")
                .addMetric("JVM_NonHeapMemoryUsage_Max", new LongGauge(() -> ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getMax()), "type", "max")
                .addMetric("JVM_NonHeapMemoryUsage_Used", new LongGauge(() -> ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed()), "type", "used"));

        registration.accept(singleValue("JVM_TotalCompilationTime",
                () -> ManagementFactory.getCompilationMXBean().getTotalCompilationTime()));
        registration.accept(singleReadoutValue("JVM_CompilationTime",
                () -> ManagementFactory.getCompilationMXBean().getTotalCompilationTime()));

        registration.accept(singleValue("JVM_AvailableProcessors",
                () -> ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors()));
        registration.accept(singleValue("JVM_SystemLoadAverage",
                () -> ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage()));
        registration.accept(singleValue("JVM_OS_ProcessCpuLoad",
                () -> ((com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getProcessCpuLoad()));
        registration.accept(newContainer("JVM_OS_ProcessCpuTime")
                        .addMetric("JVM_OS_ProcessCpuTime", new ReadoutLongGauge(() -> ((com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime()), "aggregation", "none")
                        .addMetric("JVM_OS_ProcessCpuTime", new LongGauge(() -> ((com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime()), "aggregation", "sum"));
        registration.accept(singleValue("JVM_OS_TotalPhysicalMemorySize",
                () -> ((com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize()));
        registration.accept(singleValue("JVM_OS_TotalSwapSpaceSize",
                () -> ((com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getTotalSwapSpaceSize()));
        registration.accept(singleValue("JVM_OS_CommittedVirtualMemorySize",
                () -> ((com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getCommittedVirtualMemorySize()));
        registration.accept(singleValue("JVM_OS_FreePhysicalMemorySize",
                () -> ((com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getFreePhysicalMemorySize()));
        registration.accept(singleValue("JVM_OS_FreeSwapSpaceSize",
                () -> ((com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getFreeSwapSpaceSize()));
        registration.accept(singleValue("JVM_OS_SystemCpuLoad",
                () -> ((com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getSystemCpuLoad()));
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.UnixOperatingSystemMXBean) {
            registration.accept(singleValue("JVM_OS_MaxFileDescriptorCount",
                    () -> ((com.sun.management.UnixOperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getMaxFileDescriptorCount()));
            registration.accept(singleValue("JVM_OS_OpenFileDescriptorCount",
                    () -> ((com.sun.management.UnixOperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getOpenFileDescriptorCount()));
        }

        registration.accept(singleValue("JVM_LoadedClassCount",
                () -> ManagementFactory.getClassLoadingMXBean().getLoadedClassCount()));
        registration.accept(singleValue("JVM_TotalLoadedClassCount",
                () -> ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount()));
        registration.accept(singleValue("JVM_UnloadedClassCount",
                () -> ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount()));

        registration.accept(singleValue("JVM_StartTime",
                () -> ManagementFactory.getRuntimeMXBean().getStartTime()));
        registration.accept(singleValue("JVM_Uptime",
                () -> ManagementFactory.getRuntimeMXBean().getUptime()));
    }

    private static MetricsContainer newContainer(String name) {
        return new MetricsContainer(name, "from Java MXBeans", Prometheus.MetricType.GAUGE);
    }

    private static MetricsContainer singleValue(String name, DoubleSupplier supplier) {
        return newContainer(name).addMetric(name, new DoubleGauge(supplier));
    }

    private static MetricsContainer singleValue(String name, LongSupplier supplier) {
        return newContainer(name).addMetric(name, new LongGauge(supplier));
    }

    private static MetricsContainer singleReadoutValue(String name, LongSupplier supplier) {
        return newContainer(name).addMetric(name, new ReadoutLongGauge(supplier));
    }

    private static class DoubleGauge implements Gauge<Double> {
        private final DoubleSupplier supplier;

        public DoubleGauge(DoubleSupplier supplier) {
            this.supplier = supplier;
        }

        @Override
        public Double getValue() {
            return supplier.getAsDouble();
        }
    }

    private static class LongGauge implements Gauge<Long> {
        private final LongSupplier supplier;

        public LongGauge(LongSupplier supplier) {
            this.supplier = supplier;
        }

        @Override
        public Long getValue() {
            return supplier.getAsLong();
        }
    }

    private static class ReadoutLongGauge implements Gauge<Long> {
        private final LongSupplier supplier;
        private long last;

        public ReadoutLongGauge(LongSupplier supplier) {
            this.supplier = supplier;
        }

        @Override
        public Long getValue() {
            long v = supplier.getAsLong();
            long r = v - last;
            last = v;
            return r;
        }
    }

    private static class FixedLongGauge implements Gauge<Long> {
        private final long v;

        public FixedLongGauge(long v) {
            this.v = v;
        }

        @Override
        public Long getValue() {
            return v;
        }
    }
}
