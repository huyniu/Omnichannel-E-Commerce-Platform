package com.omni.order.config;

import com.omni.common.event.Events;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.apache.kafka.clients.admin.NewTopic;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
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

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(@Value("${spring.data.redis.host:localhost}") String host,
                                         @Value("${spring.data.redis.port:6379}") int port) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectionMinimumIdleSize(8)
                .setConnectionPoolSize(64)
                .setTimeout(1000)
                .setRetryAttempts(1);
        return Redisson.create(config);
    }

    @Bean
    public NewTopic orderEventsTopic(@Value("${kafka.topic.partitions:3}") int partitions,
                                     @Value("${kafka.topic.replicas:1}") short replicas) {
        return TopicBuilder.name(Events.TOPIC_ORDER).partitions(partitions).replicas(replicas).build();
    }

    /**
     * Retries a failing record with exponential back-off, then parks it on {@code <topic>.DLT}
     * instead of blocking the partition forever.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxElapsedTime(10_000);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Order & Inventory Service").version("v1"))
                .addServersItem(new Server().url("/"));
    }
}
