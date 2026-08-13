package com.cloudscale.evaluation.metrics;

import com.cloudscale.evaluation.api.EvaluationReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class EvaluationMetrics {
    private final MeterRegistry registry;

    public EvaluationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void cacheHit() {
        counter("cloudscale.evaluation.cache.hits").increment();
    }

    public void cacheMiss() {
        counter("cloudscale.evaluation.cache.misses").increment();
    }

    public void cacheReadError() {
        counter("cloudscale.evaluation.cache.read.errors").increment();
    }

    public void cacheWriteError() {
        counter("cloudscale.evaluation.cache.write.errors").increment();
    }

    public void configurationFetch(String result) {
        Counter.builder("cloudscale.evaluation.configuration.fetches")
                .tag("result", result)
                .register(registry)
                .increment();
    }

    public void configurationFetchError() {
        counter("cloudscale.evaluation.configuration.fetch.errors").increment();
    }

    public void evaluationDuration(Duration duration, EvaluationReason reason, String source) {
        Timer.builder("cloudscale.evaluation.duration")
                .tag("reason", reason.name())
                .tag("source", source)
                .register(registry)
                .record(duration);
    }

    private Counter counter(String name) {
        return Counter.builder(name).register(registry);
    }
}
