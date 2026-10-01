package com.logmonitoring.engine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    public static final String LOG_TOPIC_EXCHANGE = "log.topic.exchange";
    public static final String CRITICAL_ALERTS_QUEUE = "critical.alerts.queue";
    public static final String INFO_STORAGE_QUEUE = "info.storage.queue";
    public static final String CRITICAL_ROUTING_PATTERN = "*.critical";
    public static final String INFO_ROUTING_PATTERN = "*.info";

    public static final String DEAD_LETTER_EXCHANGE = "log.dead-letter.exchange";
    public static final String DEAD_LETTER_QUEUE = "log.dead-letter.queue";
    public static final String DEAD_LETTER_ROUTING_KEY = "dead-letter";

    @Bean
    public TopicExchange logTopicExchange() {
        return ExchangeBuilder.topicExchange(LOG_TOPIC_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue criticalAlertsQueue() {
        return QueueBuilder.durable(CRITICAL_ALERTS_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue infoStorageQueue() {
        return QueueBuilder.durable(INFO_STORAGE_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding criticalAlertsBinding(Queue criticalAlertsQueue, TopicExchange logTopicExchange) {
        return BindingBuilder.bind(criticalAlertsQueue).to(logTopicExchange).with(CRITICAL_ROUTING_PATTERN);
    }

    @Bean
    public Binding infoStorageBinding(Queue infoStorageQueue, TopicExchange logTopicExchange) {
        return BindingBuilder.bind(infoStorageQueue).to(logTopicExchange).with(INFO_ROUTING_PATTERN);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DEAD_LETTER_ROUTING_KEY);
    }

    /**
     * JSON payloads are converted to the listener's declared parameter type rather than
     * a class named in the message headers, so producers cannot steer deserialization.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public RabbitTemplateCustomizer publisherReliabilityCustomizer() {
        return template -> {
            template.setConfirmCallback((correlation, ack, cause) -> {
                if (!ack) {
                    log.error("Broker NACKed log event {}: {}",
                            correlation != null ? correlation.getId() : "unknown", cause);
                }
            });
            template.setReturnsCallback(returned -> log.error(
                    "Unroutable log event returned by broker: exchange={} routingKey={} replyText={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        };
    }

    @Bean
    public SimpleRabbitListenerContainerFactory criticalListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Value("${log-engine.consumers.critical.concurrency}") int concurrency,
            @Value("${log-engine.consumers.critical.max-concurrency}") int maxConcurrency,
            @Value("${log-engine.consumers.critical.prefetch}") int prefetch) {
        return listenerFactory(configurer, connectionFactory, concurrency, maxConcurrency, prefetch);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory storageListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Value("${log-engine.consumers.storage.concurrency}") int concurrency,
            @Value("${log-engine.consumers.storage.max-concurrency}") int maxConcurrency,
            @Value("${log-engine.consumers.storage.prefetch}") int prefetch) {
        return listenerFactory(configurer, connectionFactory, concurrency, maxConcurrency, prefetch);
    }

    private static SimpleRabbitListenerContainerFactory listenerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            int concurrency,
            int maxConcurrency,
            int prefetch) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(maxConcurrency);
        factory.setPrefetchCount(prefetch);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
