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

import com.beust.jcommander.internal.Maps;
import com.codahale.metrics.*;
import io.prometheus.client.Prometheus;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

public class PrometheusMetricsExporterTest {
    @Test
    public void testHttp() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        Gauge<Long> gauge1 = () -> 1L;
        Gauge<Long> gauge2 = () -> 2L;
        registry.register("gauge1", gauge1);
        registry.register("gauge2", gauge2);
        Histogram hist1 = new Histogram(new ExponentiallyDecayingReservoir());
        Histogram hist2 = new Histogram(new ExponentiallyDecayingReservoir());
        hist1.update(1);
        hist2.update(2);
        registry.register("hist1", hist1);
        registry.register("hist2", hist2);
        Timer timer1 = new Timer();
        Timer timer2 = new Timer();
        timer1.update(1, TimeUnit.MILLISECONDS);
        timer2.update(2, TimeUnit.MILLISECONDS);
        registry.register("timer1", timer1);
        registry.register("timer2", timer2);
        Counter counter1 = new Counter();
        Counter counter2 = new Counter();
        counter1.inc();
        counter2.inc(2);
        registry.register("counter1", counter1);
        registry.register("counter2", counter2);
        Meter meter1 = new Meter();
        Meter meter2 = new Meter();
        meter1.mark();
        meter2.mark();
        meter2.mark();
        registry.register("meter1", meter1);
        registry.register("meter2", meter2);

        PrometheusMetricsExporter exporter = PrometheusMetricsExporterBuilder.newBuilder()
                .registry(registry)
                .httpPort(8899)
                .includeJvm(true)
                .config(new File("mappings/cassandra-mappings.yaml").toURI().toURL())
                .build();

        assertEquals(exporter.debugGetMappedName("org.apache.cassandra.metrics.keyspace.CasProposeLatency.workloads"), "keyspace_CasProposeLatency");
        assertEquals(exporter.debugGetMappedLabels("org.apache.cassandra.metrics.keyspace.CasProposeLatency.workloads"), Maps.newHashMap("keyspace", "workloads"));

        try {
            URL url = new URL("http://127.0.0.1:8899/metrics");
            URLConnection urlConn = url.openConnection();
            urlConn.addRequestProperty("Accept", "text/plain;version=0.0.4;q=0.3,application/json;schema=\"prometheus/telemetry\";version=0.0.2;q=0.2,*/*;q=0.1");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream is = urlConn.getInputStream()) {
                assertTrue(urlConn.getHeaderField("Content-Type").startsWith("text/plain"));
                byte[] buf = new byte[4096];
                int rd;
                while ((rd = is.read(buf)) >= 0)
                    out.write(buf, 0, rd);
            }

            System.out.println(out.toString());

            out.reset();
            urlConn = url.openConnection();
            urlConn.addRequestProperty("Accept", "application/vnd.google.protobuf;proto=io.prometheus.client.MetricFamily;encoding=delimited");
            try (InputStream is = urlConn.getInputStream()) {
                assertTrue(urlConn.getHeaderField("Content-Type").startsWith("application/vnd.google.protobuf"));
                int n = 0;
                for (;true; n++) {
                    Prometheus.MetricFamily metricFamily = Prometheus.MetricFamily.parseDelimitedFrom(is);
                    if (metricFamily == null)
                        break;
                    assertNotEquals(metricFamily.getMetricCount(), 0);
                    System.out.println(metricFamily);
                    System.out.println("--- EOF");
                }
                assertTrue(n >= 4);
            }

            urlConn = url.openConnection();
            urlConn.addRequestProperty("Accept", "application/vnd.google.protobuf;proto=io.prometheus.client.MetricFamily;encoding=delimited;q=0.7,text/plain;version=0.0.4;q=0.3,application/json;schema=\"prometheus/telemetry\";version=0.0.2;q=0.2,*/*;q=0.1");
            try (InputStream is = urlConn.getInputStream()) {
                assertTrue(urlConn.getHeaderField("Content-Type").startsWith("application/vnd.google.protobuf"));
            }

            urlConn = url.openConnection();
            urlConn.addRequestProperty("Accept", "text/plain;version=0.0.4;q=0.3,application/json;schema=\"prometheus/telemetry\";version=0.0.2;q=0.2,*/*;q=0.1");
            try (InputStream is = urlConn.getInputStream()) {
                assertTrue(urlConn.getHeaderField("Content-Type").startsWith("text/plain"));
            }
        } finally {
            exporter.stop();
        }
    }
}
