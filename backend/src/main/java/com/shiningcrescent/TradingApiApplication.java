package com.shiningcrescent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication(exclude = {KafkaAutoConfiguration.class, MailSenderAutoConfiguration.class})
public class TradingApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradingApiApplication.class, args);
    }
}
