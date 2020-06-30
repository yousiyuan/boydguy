package com.boydguy.backend.api.configuration;

import com.boydguy.generate.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@EnableRabbit
public class RabbitConfig {

    @Autowired
    public RabbitConfig(SimpleRabbitListenerContainerFactory simpleRabbitListenerContainerFactory) {
        simpleRabbitListenerContainerFactory.setMessageConverter(new SimpleMessageConverter());
        simpleRabbitListenerContainerFactory.setConsumerTagStrategy((String s) -> {
            String consumerTag = String.format("%s_%s", "consumer", String.valueOf(System.currentTimeMillis()));
            log.info("Consumer Tag ：{}", consumerTag);
            return consumerTag;
        });
    }

    @Bean
    public RabbitAdmin rabbitAdmin(CachingConnectionFactory connectionFactory) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.setAutoStartup(true);
        return rabbitAdmin;
    }

    /**
     * 因为要设置回调类，所以应是prototype类型，如果是singleton类型，多次设置回调类会报错
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        // 使用单独的发送连接，避免生产者由于各种原因阻塞而导致消费者同样阻塞
        rabbitTemplate.setUsePublisherConnection(true);
        /*
         * 当mandatory标志位设置为true时
         * 如果exchange根据自身类型和消息routingKey无法找到一个合适的queue存储消息
         * 那么broker会调用basic.return方法将消息返还给生产者
         * 当mandatory设置为false时，出现上述情况broker会直接将消息丢弃
         */
        rabbitTemplate.setMandatory(true);

        // ConfirmCallback接口用于实现消息发送到RabbitMQ交换器后接收ack回调
        rabbitTemplate.setConfirmCallback((CorrelationData correlationData, boolean ack, String cause) -> {
            Map<String, Object> confirmCallbackMap = new HashMap<>();
            confirmCallbackMap.put("correlationData", correlationData);
            confirmCallbackMap.put("ack", ack);
            confirmCallbackMap.put("cause", cause);
            log.info("消息发送发送结果：{}", JsonUtils.to(confirmCallbackMap));
        });

        // ReturnCallback接口用于实现消息发送到RabbitMQ交换器，但无相应队列与交换器绑定时的回调
        rabbitTemplate.setReturnCallback((Message message, int replyCode, String replyText, String exchange, String routingKey) -> {
            Map<String, Object> returnCallbackMap = new HashMap<>();
            returnCallbackMap.put("message", new String(message.getBody(), StandardCharsets.UTF_8));
            returnCallbackMap.put("replyCode", replyCode);
            returnCallbackMap.put("replyText", replyText);
            returnCallbackMap.put("exchange", exchange);
            returnCallbackMap.put("routingKey", routingKey);
            log.error(JsonUtils.to(returnCallbackMap));
        });

        return rabbitTemplate;
    }

    //region 声明交换机
//    @Bean
//    public DirectExchange directExchangeBoydguy() {
//        //Map<String, Object> exchangeArguments = new HashMap<>();
//        //exchangeArguments.put("alternate-exchange", "boydguy-ae");
//        //exchangeArguments.put("internal", false);
//        return new DirectExchange("direct-exchange-boydguy", true, false, new HashMap<>());
//    }

    @Bean
    public FanoutExchange fanoutExchangeBoydguy() {
        return new FanoutExchange("fanout-exchange-boydguy", true, false, new HashMap<>());
        //return ExchangeBuilder.fanoutExchange("fanout-exchange-boydguy").durable(true).build();
    }

//    @Bean
//    public TopicExchange topicExchangeBoydguy() {
//        return new TopicExchange("topic-exchange-boydguy", true, false, new HashMap<>());
//    }
    //endregion

    //region 声明消息队列
//    @Bean
//    public Queue queueForDirectExchange() {
//        //Map<String, Object> queueArguments = new HashMap<>();
//        //queueArguments.put("x-dead-letter-exchange", "boydguy_dlx_exchange_fanout");
//        //queueArguments.put("x-message-ttl", 3600000);
//        //queueArguments.put("x-max-length", 99999999);
//        return new Queue("queue-boydguy-direct", true, false, false, new HashMap<>());
//    }

    @Bean
    public Queue queueForFanoutExchange() {
        //return new Queue("queue-boydguy-fanout", true, false, false, new HashMap<>());
        return QueueBuilder.durable("queue-boydguy-fanout").build();
    }

//    @Bean
//    public Queue queueForTopicExchange() {
//        return new Queue("queue-boydguy-topic", true, false, false, new HashMap<>());
//    }
    //endregion

    //region 声明交换机与消息队列的绑定关系
//    @Bean
//    public Binding bindingForQueueWithDirectExchange(Queue queueForDirectExchange,
//                                                     DirectExchange directExchangeBoydguy) {
//        return new Binding(queueForDirectExchange.getName(), Binding.DestinationType.QUEUE,
//                directExchangeBoydguy.getName(), "list",
//                new HashMap<>());
//    }

    @Bean
    public Binding bindingForQueueWithFanoutExchange(Queue queueForFanoutExchange,
                                                     FanoutExchange fanoutExchangeBoydguy) {
//        return new Binding(queueForFanoutExchange.getName(), Binding.DestinationType.QUEUE,
//                fanoutExchangeBoydguy.getName(), "list",
//                new HashMap<>());
        return BindingBuilder.bind(queueForFanoutExchange).to(fanoutExchangeBoydguy);
    }

//    @Bean
//    public Binding bindingForQueueWithTopicExchange(Queue queueForTopicExchange,
//                                                    TopicExchange topicExchangeBoydguy) {
//        return new Binding(queueForTopicExchange.getName(), Binding.DestinationType.QUEUE,
//                topicExchangeBoydguy.getName(), "list",
//                new HashMap<>());
//    }
    //endregion

}
