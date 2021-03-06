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
import com.codahale.metrics.Timer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.CharsetUtil;
import io.prometheus.client.Prometheus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.net.ssl.SSLException;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Prometheus metrics exporter - use {@link PrometheusMetricsExporterBuilder} to create an
 * instance of this class.
 */
public final class PrometheusMetricsExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrometheusMetricsExporter.class);

    private final MetricRegistry registry;
    private final MetricsListener metricsListener;
    private final ExporterConfig config;

    private Channel nettyChannel;

    private volatile Map<String, MetricsContainer> metrics = new HashMap<>();

    PrometheusMetricsExporter(MetricRegistry registry, ExporterConfig config) {
        this.registry = registry;
        this.config = config;

        for (ExporterConfig.Mapping mapping : config.mappings) {
            mapping.regex = Pattern.compile(mapping.pattern);
            LOGGER.info("Initializing Prometheus metrics mapping with regex '{}'", mapping.regex);
        }

        for (ExporterConfig.Exclusion exclusion : config.exclusions) {
            exclusion.regex = Pattern.compile(exclusion.pattern);
            LOGGER.info("Initializing Prometheus metrics exclusion with regex '{}'", exclusion.regex);
        }

        LOGGER.info("Setting up Prometheus metrics exporter on {} port {} and SSL {}", config.bindAddress, config.httpPort, config.ssl ? "enabled" : "disabled");

        metricsListener = new MetricsListener();
        registry.addListener(metricsListener);

        if (config.includeJvm)
            JvmMetrics.register(this::addMetricsContainer);

        try {
            setupNetty();
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup metrics exporter", e);
        }
    }

    synchronized void addMetricsContainer(MetricsContainer container) {
        Map<String, MetricsContainer> copy = new HashMap<>(metrics);
        copy.put(container.name, container);
        metrics = copy;
    }

    synchronized void removeMetricsContainer(String name) {
        Map<String, MetricsContainer> copy = new HashMap<>(metrics);
        copy.remove(name);
        metrics = copy;
    }

    /**
     * Programmatic helper method to debug metric mappings.
     * Pass in a <em>codahale</em> metric name and get the <em>mapped</em>
     * name back.
     *
     * @param codahaleName codahale metric name
     * @return mapped name or {@code null}, if not mapped
     */
    public String debugGetMappedName(String codahaleName) {
        for (ExporterConfig.Exclusion exclusion : config.exclusions) {
            if (exclusion.regex.matcher(codahaleName).matches()) {
                return null;
            }
        }
        for (ExporterConfig.Mapping mapping : config.mappings) {
            Matcher matcher = mapping.regex.matcher(codahaleName);
            if (matcher.matches()) {
                return matcher.replaceAll(mapping.name);
            }
        }
        return null;
    }

    /**
     * Programmatic helper method to debug metric mappings.
     * Pass in a <em>codahale</em> metric name and get the <em>mapped</em>
     * labels back.
     *
     * @param codahaleName codahale metric name
     * @return mapped labels or {@code null}, if not mapped
     */
    public Map<String, String> debugGetMappedLabels(String codahaleName) {
        for (ExporterConfig.Exclusion exclusion : config.exclusions) {
            if (exclusion.regex.matcher(codahaleName).matches()) {
                return null;
            }
        }
        for (ExporterConfig.Mapping mapping : config.mappings) {
            Matcher matcher = mapping.regex.matcher(codahaleName);
            if (matcher.matches()) {
                Map<String, String> labels = new HashMap<>();
                for (ExporterConfig.Label label : mapping.labels) {
                    labels.put(
                            matcher.replaceAll(label.label),
                            matcher.replaceAll(label.value));
                }
                return labels;
            }
        }
        return null;
    }

    /**
     * Stop the metrics exporter.
     */
    public void stop() {
        LOGGER.info("Stopping Prometheus metrics exporter");

        registry.removeListener(metricsListener);

        nettyChannel.close();
        nettyChannel.closeFuture().syncUninterruptibly();
    }

    private void sendMetrics(ResponseFormat responseFormat, OutputStream output) throws IOException {
        BufferedOutputStream buffered = new BufferedOutputStream(output);
        Object out = responseFormat.createOutput(buffered);
        for (MetricsContainer metricsContainer : metrics.values()) {
            try {
                if (LOGGER.isTraceEnabled()) {
                    MetricsContainer container = metricsContainer;
                    LOGGER.trace(".. sending container {} of type {}", container.name, container.type);
                    for (MetricInfo metric : container.getMetrics()) {
                        LOGGER.trace(".... metric {} ({})", metric.sourceName, metric.metric.getClass().getName());
                        for (String[] label : metric.labels) {
                            LOGGER.trace("....   label: {}={} ", label[0], label[1]);
                        }
                    }
                }
                responseFormat.writeMetric(metricsContainer, out);
            } catch (Exception ex) {
                LOGGER.error("Not including metrics for '" + metricsContainer.name + "' due to failure constructing these metrics", ex);
            }
        }
        LOGGER.trace(".. metrics sent");
        responseFormat.finish(out);
        buffered.flush();
    }

    private void setupNetty() throws CertificateException, SSLException {
        final SslContext sslCtx;
        if (config.ssl) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            LOGGER.info("Setting up SSL context for certificate subject DN {} valid until {}", ssc.cert().getSubjectDN(), ssc.cert().getNotAfter());
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } else {
            sslCtx = null;
        }

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        this.nettyChannel = new ServerBootstrap()
                .option(ChannelOption.SO_BACKLOG, 1024)
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ServerInitializer(sslCtx))
                .bind(config.bindAddress, config.httpPort).syncUninterruptibly().channel();

        nettyChannel.closeFuture().addListener(f -> {
            LOGGER.info("Shutting down listener");
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        });
    }

    private class ServerInitializer extends ChannelInitializer<SocketChannel> {

        private final SslContext sslCtx;

        private ServerInitializer(SslContext sslCtx) {
            this.sslCtx = sslCtx;
        }

        @Override
        public void initChannel(SocketChannel ch) {
            ChannelPipeline p = ch.pipeline();
            if (sslCtx != null) {
                p.addLast(sslCtx.newHandler(ch.alloc()));
            }
            p.addLast("decoder", new HttpRequestDecoder());
            p.addLast("encoder", new HttpResponseEncoder());
            p.addLast("compressor", new HttpContentCompressor());
            p.addLast("handler", new ServerHandler());
        }
    }

    private class ServerHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof HttpRequest) {
                HttpRequest req = (HttpRequest) msg;

                LOGGER.debug("HTTP request {}", req);

                if (!req.getDecoderResult().isSuccess()) {
                    sendError(ctx, BAD_REQUEST);
                    return;
                }

                if (req.getMethod() != GET) {
                    sendError(ctx, METHOD_NOT_ALLOWED);
                    return;
                }

                if (HttpHeaders.is100ContinueExpected(req)) {
                    ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
                }

                ResponseFormat responseFormat = responseFormat(req);

                boolean keepAlive = HttpHeaders.isKeepAlive(req);
                HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
                HttpHeaders.setHeader(response, CONTENT_TYPE, responseFormat.contentType());
                HttpHeaders.setDateHeader(response, DATE, new Date());
                HttpHeaders.setTransferEncodingChunked(response);

                if (keepAlive) {
                    response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
                }

                ctx.write(response);

                LOGGER.debug("Sending response as {}", responseFormat.contentType());

                try {
                    sendMetrics(responseFormat, new OutputStream() {
                        @Override
                        public void write(byte[] b, int off, int len) throws IOException {
                            ctx.write(new DefaultHttpContent(Unpooled.wrappedBuffer(b, off, len)));
                        }

                        @Override
                        public void write(int b) throws IOException {
                            throw new UnsupportedOperationException();
                        }
                    });
                } catch (Throwable e) {
                    LOGGER.info("Error during response processing", e);
                    sendError(ctx, INTERNAL_SERVER_ERROR);
                }

                ChannelFuture lastContentFuture = ctx.write(LastHttpContent.EMPTY_LAST_CONTENT);
                if (!keepAlive) {
                    lastContentFuture.addListener(ChannelFutureListener.CLOSE);
                }

                ctx.flush();
            }
        }

        private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
            response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

            // Close the connection as soon as the error message is sent.
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        private ResponseFormat responseFormat(HttpRequest req) {
            ResponseFormat responseFormat = ResponseFormat.TEXT;
            String accept = HttpHeaders.getHeader(req, "Accept");
            if (accept != null) {
                double qText = 0.0d;
                double qProtobuf = 0.0d;
                for (StringTokenizer st = new StringTokenizer(accept, ","); st.hasMoreTokens(); ) {
                    try {
                        MimeType mimeType = new MimeType(st.nextToken());
                        switch (mimeType.getPrimaryType()) {
                            case "text":
                                switch (mimeType.getSubType()) {
                                    case "*":
                                    case "plain":
                                        qText = qEval(qText, mimeType);
                                        break;
                                }
                                break;
                            case "application":
                                switch (mimeType.getSubType()) {
                                    case "vnd.google.protobuf":
                                    case "octet-stream":
                                        if ("delimited".equals(mimeType.getParameter("encoding")) &&
                                                "io.prometheus.client.MetricFamily".equals(mimeType.getParameter("proto")))
                                            qProtobuf = qEval(qProtobuf, mimeType);
                                        break;
                                }
                                break;
                        }
                    } catch (MimeTypeParseException e) {
                        // just ignore this
                    }
                }
                if (qProtobuf > qText)
                    responseFormat = ResponseFormat.PROTOBUF;
            }
            LOGGER.trace("Chosen response format {} for HTTP Accept:{}", responseFormat.contentType(), accept);
            return responseFormat;
        }

        private double qEval(double currentQ, MimeType mimeType) {
            String sq = mimeType.getParameter("q");
            double q = sq != null ? Double.parseDouble(sq.trim()) : 0.01d;
            return Math.max(q, currentQ);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }

    private class MetricsListener implements MetricRegistryListener {
        private final Pattern VALIDATION_PATTERN = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*");

        private synchronized void unregisterMetric(String codahaleName) {
            for (ExporterConfig.Exclusion exclusion : config.exclusions) {
                if (exclusion.regex.matcher(codahaleName).matches()) {
                    return;
                }
            }

            for (ExporterConfig.Mapping mapping : config.mappings) {
                Matcher matcher = mapping.regex.matcher(codahaleName);
                if (matcher.matches()) {
                    String name = matcher.replaceAll(mapping.name);

                    MetricsContainer container = metrics.get(name);
                    if (container.removeMetric(codahaleName)) {
                        return;
                    }
                }
            }

            String name = convert(codahaleName);
            removeMetricsContainer(name);
        }

        private synchronized void registerMetric(String codahaleName, Metric metric) {
            for (ExporterConfig.Exclusion exclusion : config.exclusions) {
                if (exclusion.regex.matcher(codahaleName).matches()) {
                    return;
                }
            }

            Prometheus.MetricType type;
            if (metric instanceof Gauge) {
                type = Prometheus.MetricType.GAUGE;
            } else if ((metric instanceof Counter) || (metric instanceof Meter)) {
                type = Prometheus.MetricType.COUNTER;
            } else if ((metric instanceof Histogram) || (metric instanceof Timer)) {
                type = Prometheus.MetricType.SUMMARY;
            } else {
                throw new UnsupportedOperationException("Unknown metric of type " + metric.getClass().getName());
            }

            for (ExporterConfig.Mapping mapping : config.mappings) {
                Matcher matcher = mapping.regex.matcher(codahaleName);
                if (matcher.matches()) {
                    LOGGER.debug("{} matches {}", codahaleName, mapping.pattern);
                    String name = matcher.replaceAll(mapping.name);

                    List<String[]> labels = new ArrayList<>();
                    for (ExporterConfig.Label label : mapping.labels) {
                        labels.add(new String[]{
                                matcher.replaceAll(label.label),
                                matcher.replaceAll(label.value)
                        });
                    }

                    if (!VALIDATION_PATTERN.matcher(name).matches())
                        LOGGER.warn("Invalid Prometheus metric name '{}' (from '{}')", name, codahaleName);
                    else {
                        MetricsContainer container = metrics.get(name);
                        if (container == null) {
                            addMetricsContainer(container = new MetricsContainer(name, "from codahale", type));
                        } else {
                            if (container.type != type) {
                                LOGGER.error("Existing metrics with name '{}' are of type '{}' but metric to be registered '{}' is of type '{}'",
                                        container.name, container.type, codahaleName, type);
                                return;
                            }
                        }
                        container.addMetric(codahaleName, metric, labels.toArray(new String[labels.size()][]));
                    }

                    return;
                }
            }

            LOGGER.info("No matching metric mapping for '{}'", codahaleName);
            String name = convert(codahaleName);
            MetricsContainer container = new MetricsContainer(name, "from codahale", type);
            container.addMetric(codahaleName, metric);
            addMetricsContainer(container);
        }

        private String convert(String s) {
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '.':
                        c = '_';
                        break;
                    case '-':
                        c = '_';
                        break;
                }
                sb.append(c);
            }
            s = sb.toString();
            if (!VALIDATION_PATTERN.matcher(s).matches())
                LOGGER.warn("Metric name {} does not validate", s);
            return s;
        }

        @Override
        public void onGaugeAdded(String s, Gauge<?> gauge) {
            registerMetric(s, gauge);
        }

        @Override
        public void onGaugeRemoved(String s) {
            unregisterMetric(s);
        }

        @Override
        public void onCounterAdded(String s, Counter counter) {
            registerMetric(s, counter);
        }

        @Override
        public void onCounterRemoved(String s) {
            unregisterMetric(s);
        }

        @Override
        public void onHistogramAdded(String s, Histogram histogram) {
            registerMetric(s, histogram);
        }

        @Override
        public void onHistogramRemoved(String s) {
            unregisterMetric(s);
        }

        @Override
        public void onMeterAdded(String s, Meter meter) {
            registerMetric(s, meter);
        }

        @Override
        public void onMeterRemoved(String s) {
            unregisterMetric(s);
        }

        @Override
        public void onTimerAdded(String s, Timer timer) {
            registerMetric(s, timer);
        }

        @Override
        public void onTimerRemoved(String s) {
            unregisterMetric(s);
        }
    }
}
