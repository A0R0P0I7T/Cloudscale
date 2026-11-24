package com.cloudscale.evaluation.events;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class EvaluationKafkaConfiguration {
    private static final Logger log = LoggerFactory.getLogger(EvaluationKafkaConfiguration.class);

    @Bean
    CommonErrorHandler evaluationCommonErrorHandler(MeterRegistry meterRegistry) {
        Counter retryCounter = Counter.builder("cloudscale.evaluation.configuration.events.retries")
                .register(meterRegistry);
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new FixedBackOff(1_000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
                                       Exception exception,
                                       int deliveryAttempt) {
                retryCounter.increment();
                log.warn("Retrying configuration event topic={} partition={} offset={} attempt={} errorType={}",
                        record.topic(), record.partition(), record.offset(), deliveryAttempt,
                        exception.getClass().getSimpleName());
            }
        });
        return handler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            CommonErrorHandler evaluationCommonErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(evaluationCommonErrorHandler);
        return factory;
    }
}
