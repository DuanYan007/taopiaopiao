package com.duanyan.taopiaopiao.orderservice.application.producer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.orderservice.application.mapper.OrderMapper;
import com.duanyan.taopiaopiao.orderservice.domain.entity.Order;
import com.duanyan.taopiaopiao.orderservice.domain.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;

/**
 * 支付事务监听器
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@RocketMQTransactionListener(rocketMQTemplateBeanName = "rocketMQTemplate")
@RequiredArgsConstructor
public class PaymentTransactionListener implements RocketMQLocalTransactionListener {

    private final OrderMapper orderMapper;

    /**
     * 执行本地事务
     *
     * @param msg 消息
     * @param arg 参数（订单号）
     * @return 事务状态
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String orderNo = (String) arg;
        try {
            log.info("执行本地事务回查: orderNo={}", orderNo);

            Order order = orderMapper.selectOne(
                    new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo)
            );

            if (order == null) {
                log.warn("订单不存在: orderNo={}", orderNo);
                return RocketMQLocalTransactionState.ROLLBACK;
            }

            if (OrderStatus.PAID.getCode().equals(order.getStatus())) {
                log.info("订单已支付，提交事务: orderNo={}", orderNo);
                return RocketMQLocalTransactionState.COMMIT;
            }

            log.warn("订单未支付，回滚事务: orderNo={}, status={}", orderNo, order.getStatus());
            return RocketMQLocalTransactionState.ROLLBACK;

        } catch (Exception e) {
            log.error("执行本地事务异常: orderNo={}", orderNo, e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    /**
     * 事务回查
     *
     * @param msg 消息
     * @return 事务状态
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        // 从消息头获取订单号
        String orderNo = (String) msg.getHeaders().get("orderId");
        if (orderNo == null) {
            log.warn("无法从消息头获取订单号");
            return RocketMQLocalTransactionState.ROLLBACK;
        }

        log.info("事务回查: orderNo={}", orderNo);
        try {
            Order order = orderMapper.selectOne(
                    new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo)
            );

            if (order != null && OrderStatus.PAID.getCode().equals(order.getStatus())) {
                log.info("回查发现订单已支付，提交事务: orderNo={}", orderNo);
                return RocketMQLocalTransactionState.COMMIT;
            }

            log.info("回查发现订单未支付，回滚事务: orderNo={}", orderNo);
            return RocketMQLocalTransactionState.ROLLBACK;

        } catch (Exception e) {
            log.error("事务回查异常: orderNo={}", orderNo, e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }
}
