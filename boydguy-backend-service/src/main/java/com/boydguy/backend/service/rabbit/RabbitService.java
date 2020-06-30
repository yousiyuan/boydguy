package com.boydguy.backend.service.rabbit;

import com.boydguy.backend.dao.base.BaseDao;
import com.boydguy.backend.pojo.Customer;
import com.boydguy.generate.utils.ComUtils;
import com.boydguy.generate.utils.JsonUtils;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class RabbitService {

    private RabbitTemplate rabbitTemplate;
    private String exchange;
    private BaseDao<Customer> customerBaseDao;

    @Autowired
    public RabbitService(RabbitTemplate rabbitTemplate,
                         FanoutExchange fanoutExchangeBoydguy,
                         BaseDao<Customer> customerBaseDao) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = fanoutExchangeBoydguy.getName();
        this.customerBaseDao = customerBaseDao;
    }

    public Object produce(String message) {
        MessageProperties msgProp = new MessageProperties();
        msgProp.setContentType(MessageProperties.CONTENT_TYPE_JSON);//设置请求头
        msgProp.setExpiration("10000");

        Object result = rabbitTemplate.convertSendAndReceive(exchange, "*",
                new Message(message.getBytes(StandardCharsets.UTF_8), msgProp));
        return ComUtils.str(result);
    }

    @RabbitHandler
    @RabbitListener(queues = {"#{queueForFanoutExchange.getName()}"})
    private String consumer(Message message, Channel channel) {
        String result = null;
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            String msg = new String(message.getBody(), StandardCharsets.UTF_8);
            result = JsonUtils.to(customerBaseDao.selectByIds(String.format("'%s'", msg)));

            basicAck(deliveryTag, channel, 0);
            log.info("消费者-已手动签收");
        } catch (Exception ex) {
            log.error(ComUtils.printException(ex));
            basicNack(deliveryTag, channel, 0);
        }
        return result;
    }

    // 正确处理消息后，手动签收消息
    private void basicAck(Long deliveryTag, Channel channel, Integer times) {
        if (times >= 3)
            return;
        try {
            Thread.sleep(1000 * times);
            channel.basicQos(1);
            // 处理完毕之后发送签收回执，multiple设置false表示不批量签收
            channel.basicAck(deliveryTag, false);
        } catch (Exception ignored) {
            basicAck(deliveryTag, channel, ++times);
        }
    }

    // 发生异常时，拒绝消息
    private void basicNack(Long deliveryTag, Channel channel, Integer times) {
        if (times >= 3)
            return;
        try {
            Thread.sleep(1000 * times);
            // 拒绝消息
            // 第三个参数说明：true - 表示重回队列，false - 表示进入死信队列
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception ignored) {
            basicNack(deliveryTag, channel, ++times);
        }
    }

}
