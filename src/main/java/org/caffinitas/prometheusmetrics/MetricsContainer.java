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

import com.codahale.metrics.Metric;
import io.prometheus.client.Prometheus;

import java.util.ArrayList;
import java.util.List;

class MetricsContainer {
    final String name;
    final String help;
    final Prometheus.MetricType type;
    final String typeName;
    private volatile List<MetricInfo> metrics = new ArrayList<>();

    MetricsContainer(String name, String help, Prometheus.MetricType type) {
        this.name = name;
        this.help = help;
        this.type = type;
        this.typeName = type.name().toLowerCase();
    }

    List<MetricInfo> getMetrics() {
        return metrics;
    }

    MetricsContainer addMetric(String s, Metric metric, String... labels) {
        List<MetricInfo> copy = new ArrayList<>(metrics);
        copy.add(new MetricInfo(s, metric, labels));
        metrics = copy;
        return this;
    }

    MetricsContainer addMetric(String s, Metric metric, String[][] labels) {
        List<MetricInfo> copy = new ArrayList<>(metrics);
        copy.add(new MetricInfo(s, metric, labels));
        metrics = copy;
        return this;
    }

    boolean removeMetric(String codahaleName) {
        for (int i = 0; i < metrics.size(); i++) {
            if (metrics.get(i).sourceName.equals(codahaleName)) {
                List<MetricInfo> copy = new ArrayList<>(metrics);
                copy.remove(i);
                metrics = copy;
                return true;
            }
        }
        return false;
    }
}
