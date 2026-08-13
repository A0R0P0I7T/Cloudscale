package com.cloudscale.evaluation.cache;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EvaluationCacheProperties.class)
public class EvaluationCacheConfiguration {
}
