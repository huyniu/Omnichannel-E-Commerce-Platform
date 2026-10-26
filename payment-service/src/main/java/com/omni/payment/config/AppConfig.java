package com.omni.payment.config;

import com.omni.common.event.Events;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class AppConfig {

    @Bean
    public NewTopic paymentEventsTopic(@Value("${kafka.topic.partitions:3}") int partitions,
                                       @Value("${kafka.topic.replicas:1}") short replicas) {
        return TopicBuilder.name(Events.TOPIC_PAYMENT).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxElapsedTime(10_000);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Payment & Billing Service").version("v1"))
                .addServersItem(new Server().url("/"));
    }
}
