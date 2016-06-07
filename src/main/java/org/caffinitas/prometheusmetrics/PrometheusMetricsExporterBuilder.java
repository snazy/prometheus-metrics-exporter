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

import com.codahale.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Builder to configure {@link PrometheusMetricsExporter} via YAML file, programmatically and via system properties.
 * <ul>
 *     <li>The YAML file allows configuration of all options, including metrics mappings.</li>
 *     <li>Programmatic accessors only allows the configuration of standard options, which take precedence over the YAML file.</li>
 *     <li>System properties allow overriding standard options but has no influence to metrics mappings.
 *     System properties start with <code>{@value #SYSTEM_PROPERTY_PREFIX}</code> before the option name
 *     like <code>{@link #bindAddress(String) bindAddress}</code></li>
 * </ul>
 */
public final class PrometheusMetricsExporterBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrometheusMetricsExporterBuilder.class);

    public static final String SYSTEM_PROPERTY_PREFIX = "org.caffinitas.prometheus.";

    private MetricRegistry registry;
    private URL configUrl;

    private Boolean ssl;
    private String bindAddress;
    private Integer httpPort;
    private Boolean includeJvm;

    public static PrometheusMetricsExporterBuilder newBuilder() {
        return new PrometheusMetricsExporterBuilder();
    }

    private PrometheusMetricsExporterBuilder() {
    }

    private static int fromSystemProperties(String name, int defaultValue) {
        try {
            return Integer.parseInt(System.getProperty(SYSTEM_PROPERTY_PREFIX + name, Integer.toString(defaultValue)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse system property " + SYSTEM_PROPERTY_PREFIX + name, e);
        }
    }

    private static boolean fromSystemProperties(String name, boolean defaultValue) {
        try {
            return Boolean.parseBoolean(System.getProperty(SYSTEM_PROPERTY_PREFIX + name, Boolean.toString(defaultValue)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse system property " + SYSTEM_PROPERTY_PREFIX + name, e);
        }
    }

    private static URL fromSystemProperties(String name, URL defaultValue) {
        try {
            String u = fromSystemProperties(name, (String) null);
            if (u == null)
                return defaultValue;

            URL url;
            try
            {
                url = new URL(u);
                url.openStream().close(); // catches well-formed but bogus URLs
                return url;
            }
            catch (Exception e)
            {
                ClassLoader loader = PrometheusMetricsExporterBuilder.class.getClassLoader();
                url = loader.getResource(u);
                if (url == null)
                {
                    String required = "file:" + File.separator + File.separator;
                    if (!u.startsWith(required))
                        throw new RuntimeException("Cannot load configuration from URL " + name);
                    throw new RuntimeException("Cannot load configuration from URL " + name);
                }
                return url;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse system property " + SYSTEM_PROPERTY_PREFIX + name, e);
        }
    }

    private static String fromSystemProperties(String name, String defaultValue) {
        return System.getProperty(SYSTEM_PROPERTY_PREFIX + name, defaultValue);
    }

    public PrometheusMetricsExporterBuilder registry(MetricRegistry registry) {
        this.registry = registry;
        return this;
    }

    public PrometheusMetricsExporterBuilder bindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
        return this;
    }

    public PrometheusMetricsExporterBuilder httpPort(int httpPort) {
        this.httpPort = httpPort;
        return this;
    }

    public PrometheusMetricsExporterBuilder ssl(boolean ssl) {
        this.ssl = ssl;
        return this;
    }

    public PrometheusMetricsExporterBuilder includeJvm(boolean includeJvm) {
        this.includeJvm = includeJvm;
        return this;
    }

    public PrometheusMetricsExporterBuilder config(URL configUrl) {
        this.configUrl = configUrl;
        return this;
    }

    public PrometheusMetricsExporter build() {
        configUrl = fromSystemProperties("config", configUrl);
        ExporterConfig config;
        if (configUrl != null) {
            LOGGER.info("Loading configuration from URL {}", configUrl);
            try (InputStream is = configUrl.openStream())
            {
                Constructor constructor = new Constructor(ExporterConfig.class);

                TypeDescription desc = new TypeDescription(ExporterConfig.class);
                desc.putListPropertyType("mappings", ExporterConfig.Mapping.class);
                constructor.addTypeDescription(desc);

                desc = new TypeDescription(ExporterConfig.Mapping.class);
                desc.putListPropertyType("labels", ExporterConfig.Label.class);
                constructor.addTypeDescription(desc);

                Yaml yaml = new Yaml(constructor);
                config = yaml.loadAs(is, ExporterConfig.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load configuration " + configUrl, e);
            }
        }
        else {
            config = new ExporterConfig();
        }
        if (bindAddress != null) {
            config.bindAddress = bindAddress;
        }
        if (httpPort != null) {
            config.httpPort = httpPort;
        }
        if (ssl != null) {
            config.ssl = ssl;
        }
        if (includeJvm != null) {
            config.includeJvm = includeJvm;
        }

        config.bindAddress = fromSystemProperties("bindAddress", config.bindAddress);
        config.httpPort = fromSystemProperties("httpPort", config.httpPort);
        config.ssl = fromSystemProperties("ssl", config.ssl);
        config.includeJvm = fromSystemProperties("includeJvm", config.includeJvm);

        return new PrometheusMetricsExporter(registry, config);
    }
}
