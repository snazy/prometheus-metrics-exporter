Prometheus.io exporter for codahale/dropwizard metrics
======================================================

Integration into Apache Cassandra
---------------------------------

Requirements:
* Apache Cassandra newer than 3.XXXX
* `prometheus-metrics-exporter-0.1-SNAPSHOT.jar`
* `simpleclient-0.0.14.jar` - Maven group: `io.prometheus`
* `protobuf-java-2.5.0.jar` - Maven group: `com.google.protobuf`

Copy the three jar files into the `lib` directory of your Apache Cassandra nodes and start Cassandra using `-Dcassandra.metricsExporter=org.caffinitas.prometheusmetrics.PrometheusMetricsInitializer`.

Configuration:
* Specify an alternate HTTP listen port: pass `-Dorg.caffinitas.prometheus.httpPort=8088`
* Specify an alternate HTTP listen address: pass `-Dorg.caffinitas.prometheus.bindAddress=127.0.0.1`
* Use a (self signed) SSL certificate: pass `-Dorg.caffinitas.prometheus.ssl=true`

Hint: you can find all dependencies in the folder `target/dependencies` when you build the project from source.

Example command line: `bin/cassandra -Dcassandra.metricsExporter=org.caffinitas.prometheusmetrics.PrometheusMetricsInitializer -f`
