package org.grails.plugins.metrics.groovy

import com.codahale.metrics.*
import com.codahale.metrics.graphite.Graphite
import com.codahale.metrics.graphite.GraphiteReporter
import com.codahale.metrics.jvm.BufferPoolMetricSet
import com.codahale.metrics.jvm.ClassLoadingGaugeSet
import com.codahale.metrics.jvm.FileDescriptorRatioGauge
import com.codahale.metrics.jvm.GarbageCollectorMetricSet
import com.codahale.metrics.jvm.MemoryUsageGaugeSet
import com.codahale.metrics.jvm.ThreadStatesGaugeSet
import com.codahale.metrics.servlets.MetricsServlet
import grails.util.Holders
import org.codehaus.groovy.reflection.ReflectionUtils

import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit

class Metrics {

    private static final String REGISTRY_NAME = 'org.grails.plugins.metrics'

    // ignore org.springsource.loaded.ri.ReflectiveInterceptor when not running as a war, and ignore this class for convenience
    static final List<String> extraIgnoredPackages = ["org.springsource.loaded.ri", "org.grails.plugins.metrics.groovy"]

    /**
     * Builds a complete metric name including the calling class name if desired and excluding
     * extraneous classes in between the here and the caller.
     */
    private static String buildMetricName(String metricName) {
        if (Holders?.config?.metrics?.core?.prependClassName == false) {
            return metricName
        }

        Class callingClass = ReflectionUtils.getCallingClass(0, extraIgnoredPackages)
        return MetricRegistry.name(callingClass, metricName)
    }

    /**
     * Return the metric with the supplied name if it exists, and create it if it doesn't
     *
     */
    static Metric getOrAdd(String name, Metric metricToAdd) {
        String metricName = buildMetricName(name)
        Metric metric = registry.getMetrics().get(metricName)
        if (!metric) {
            metric = registry.register(metricName, metricToAdd)
        }
        return metric
    }

    /**
     * Register and return the supplied Gauge metric with the supplied name.  This
     * is not quite a factory method since the gauge must be created externally, but
     * called newGauge for consistency.
     *
     */
    static Gauge newGauge(String name, Gauge gauge) {
        return getOrAdd(name, gauge) as Gauge
    }

    /**
     * Create and return a counter metric with the supplied name.
     */
    static Counter newCounter(String name) {
        String metricName = buildMetricName(name)
        return registry.counter(metricName)
    }

    /**
     * Create and return a histogram metric with the supplied name and the default
     * (exponentially decaying) reservoir.
     */
    static Histogram newHistogram(String name) {
        String metricName = buildMetricName(name)
        return registry.histogram(metricName)
    }

    /**
     * Create and return a histogram metric with the supplied name and reservoir.
     */
    static Histogram newHistogram(String name, Reservoir reservoir) {
        Histogram histogram = new Histogram(reservoir)
        return getOrAdd(name, histogram) as Histogram
    }

    /**
     * Create and return a new meter metric with the supplied name.
     */
    static Meter newMeter(String name) {
        String metricName = buildMetricName(name)
        return registry.meter(metricName)
    }

    /**
     * Create and return a new time metric with the supplied name.
     */
    static Timer newTimer(String name) {
        String metricName = buildMetricName(name)
        return registry.timer(metricName)
    }

    /**
     * Returns the plugin's metric registry.
     */
    static MetricRegistry getRegistry() {
        return SharedMetricRegistries.getOrCreate(REGISTRY_NAME)
    }

    static void initJVMMetrics() {
        MetricRegistry r = registry
        r.register("jvm.attribute", new JvmAttributeGaugeSet())
        r.register("jvm.buffers", new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer()))
        r.register("jvm.classloader", new ClassLoadingGaugeSet())
        r.register("jvm.filedescriptor", new FileDescriptorRatioGauge())
        r.register("jvm.gc", new GarbageCollectorMetricSet())
        r.register("jvm.memory", new MemoryUsageGaugeSet())
        r.register("jvm.threads", new ThreadStatesGaugeSet())
    }

    /**
     * Convenience method for starting the JMX reporter exposing the default registries.
     */
    static JmxReporter startJmxReporter(TimeUnit rateUnit = TimeUnit.SECONDS, TimeUnit durationUnit = TimeUnit.MILLISECONDS) {
        final JmxReporter reporter = JmxReporter
                .forRegistry(registry)
                .convertRatesTo(rateUnit)
                .convertDurationsTo(durationUnit)
                .build();
        reporter.start();
        return reporter
    }

    /**
     * Convenience method for starting a Graphite reporter exposing the default registries.
     */
    static GraphiteReporter startGraphiteReporter(String host, int port, String prefix, long frequency, TimeUnit frequencyUnit, TimeUnit rateUnit = TimeUnit.SECONDS, TimeUnit durationUnit = TimeUnit.MILLISECONDS) {
        final Graphite graphite = new Graphite(host, port)
        final GraphiteReporter reporter = GraphiteReporter
                .forRegistry(registry)
                .convertRatesTo(rateUnit)
                .convertDurationsTo(durationUnit)
                .prefixedWith(prefix)
                .build(graphite)

        reporter.start(frequency, frequencyUnit)

        return reporter
    }

    /**
     * Removes all metrics currently registered.
     */
    static removeAll() {
        registry.removeMatching(MetricFilter.ALL)
    }

    private Metrics() {}
}
