package com.duanyan.taopiaopiao.common.mq.config;

import com.duanyan.taopiaopiao.common.mq.constant.MqTopic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RocketMQTemplate.class)
@ConditionalOnProperty(prefix = "tpp.rocketmq.topic-bootstrap", name = "enabled", havingValue = "true")
public class RocketMqTopicBootstrap implements ApplicationRunner {

    private static final List<String> TOPICS = List.of(
            MqTopic.ORDER_TOPIC,
            MqTopic.PAYMENT_TOPIC
    );

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${spring.application.name:unknown-service}")
    private String applicationName;

    @Override
    public void run(ApplicationArguments args) {
        DefaultMQProducer producer = rocketMQTemplate.getProducer();
        if (producer == null) {
            throw new IllegalStateException("RocketMQ producer 未初始化，无法自举 Topic");
        }

        for (String topic : TOPICS) {
            ensureTopic(producer, topic);
        }
    }

    private void ensureTopic(DefaultMQProducer producer, String topic) {
        if (hasRoute(producer, topic)) {
            log.info("RocketMQ Topic 已就绪: service={}, topic={}", applicationName, topic);
            return;
        }

        try {
            producer.createTopic(
                    producer.getCreateTopicKey(),
                    topic,
                    producer.getDefaultTopicQueueNums(),
                    Map.of()
            );
            log.info("RocketMQ Topic 创建完成: service={}, topic={}, queueNum={}",
                    applicationName, topic, producer.getDefaultTopicQueueNums());
        } catch (MQClientException e) {
            if (hasRoute(producer, topic)) {
                log.info("RocketMQ Topic 已由其他实例创建: service={}, topic={}", applicationName, topic);
                return;
            }
            throw new IllegalStateException("RocketMQ Topic 初始化失败: " + topic, e);
        }
    }

    private boolean hasRoute(DefaultMQProducer producer, String topic) {
        try {
            return !producer.fetchPublishMessageQueues(topic).isEmpty();
        } catch (MQClientException e) {
            return false;
        }
    }
}
