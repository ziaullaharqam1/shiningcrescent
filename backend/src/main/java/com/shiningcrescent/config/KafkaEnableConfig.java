package com.shiningcrescent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
@Import(KafkaAutoConfiguration.class)
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaEnableConfig {
}
