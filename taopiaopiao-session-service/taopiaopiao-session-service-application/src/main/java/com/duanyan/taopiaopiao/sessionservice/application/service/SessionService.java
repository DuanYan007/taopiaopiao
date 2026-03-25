package com.duanyan.taopiaopiao.sessionservice.application.service;

import com.duanyan.taopiaopiao.common.mq.message.PaymentSuccessMessage;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionCreateRequest;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionPageResponse;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionQueryRequest;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionResponse;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionUpdateRequest;

/**
 * 场次服务接口
 *
 * @author duanyan
 * @since 1.0.0
 */
public interface SessionService {

    /**
     * 分页查询场次列表
     */
    SessionPageResponse getSessionPage(SessionQueryRequest request);

    /**
     * 根据ID查询场次详情
     */
    SessionResponse getSessionById(Long id);

    /**
     * 创建场次
     */
    Long createSession(SessionCreateRequest request);

    /**
     * 更新场次
     */
    void updateSession(Long id, SessionUpdateRequest request);

    /**
     * 删除场次
     */
    void deleteSession(Long id);

    /**
     * 更新场次状态
     */
    void updateSessionStatus(Long id, String status);

    /**
     * 标记座位已售出（内部接口，供订单服务调用）
     */
    Integer markSeatsSold(Long sessionId, java.util.List<String> seatIds, String orderNo);

    /**
     * 检查座位是否已标记为 sold（幂等性校验）
     *
     * @param orderNo 订单号
     * @return true=已处理，false=未处理
     */
    boolean isSeatsMarkedSold(String orderNo);

    /**
     * 标记座位为已售出（处理支付成功消息）
     *
     * @param message 支付成功消息
     */
    void markSeatsSold(PaymentSuccessMessage message);
}
