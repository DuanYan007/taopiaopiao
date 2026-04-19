package com.duanyan.taopiaopiao.seckillservice.application.consumer;

import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import com.duanyan.taopiaopiao.common.mq.message.OrderCreatedInternalMessage;
import com.duanyan.taopiaopiao.seckillservice.application.service.impl.SeckillServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.ORDER_TOPIC,
        selectorExpression = MqTopic.TAG_ORDER_CREATED_INTERNAL,
        consumerGroup = "seckill-service-order-created-consumer-group",
        messageModel = MessageModel.CLUSTERING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class OrderCreatedInternalConsumer implements RocketMQListener<OrderCreatedInternalMessage> {

    private final SeckillServiceImpl seckillService;

    @Override
    public void onMessage(OrderCreatedInternalMessage message) {
        try {
            boolean updated = seckillService.markLockOrderOrderCreated(message.getOrderNo());
            if (!updated) {
                log.warn("内部订单创建完成消息未命中锁单: orderNo={}, lockId={}",
                        message.getOrderNo(), message.getLockId());
                return;
            }
            log.info("锁单已更新为正式订单已创建: orderNo={}, lockId={}",
                    message.getOrderNo(), message.getLockId());
        } catch (Exception e) {
            log.error("处理内部订单创建完成消息异常: orderNo={}", message.getOrderNo(), e);
            throw e;
        }
    }
}
