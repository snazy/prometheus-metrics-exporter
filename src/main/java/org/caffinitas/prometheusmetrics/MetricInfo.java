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

import com.codahale.metrics.*;

class MetricInfo<M extends Metric> {

    final String sourceName;
    final M metric;
    final String[][] labels;

    MetricInfo(String sourceName, M metric, String... labels) {
        this.sourceName = sourceName;
        this.metric = metric;

        String[][] pairs = new String[labels.length / 2][];
        for (int i = 0; i < labels.length / 2; i++) {
            pairs[i] = new String[]{labels[i * 2], labels[i * 2 + 1]};
        }
        this.labels = pairs;
    }

    MetricInfo(String sourceName, M metric, String[][] labels) {
        this.sourceName = sourceName;
        this.metric = metric;
        this.labels = labels;
    }
}
