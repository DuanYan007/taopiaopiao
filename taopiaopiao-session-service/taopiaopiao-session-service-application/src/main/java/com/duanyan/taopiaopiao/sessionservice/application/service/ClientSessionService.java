package com.duanyan.taopiaopiao.sessionservice.application.service;

import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionPageResponse;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionQueryRequest;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionResponse;

/**
 * 场次客户端服务接口
 *
 * @author duanyan
 * @since 1.0.0
 */
public interface ClientSessionService {

    /**
     * 分页查询场次列表（客户端）
     *
     * @param request 查询请求
     * @return 分页响应
     */
    SessionPageResponse getSessionPage(SessionQueryRequest request);

    /**
     * 根据ID查询场次详情（客户端）
     *
     * @param id 场次ID
     * @return 场次详情
     */
    SessionResponse getSessionById(Long id);

    /**
     * 获取场次座位列表
     *
     * @param sessionId 场次ID
     * @return 座位列表
     */
    com.duanyan.taopiaopiao.sessionservice.api.dto.SessionSeatsResponse getSessionSeats(Long sessionId);

    /**
     * 标记座位已售出（内部接口，供订单服务调用）
     *
     * @param sessionId 场次ID
     * @param seatIds 座位ID列表
     * @param orderNo 订单号
     * @return 更新数量
     */
    Integer markSeatsSold(Long sessionId, java.util.List<String> seatIds, String orderNo);

    /**
     * 查询单个座位价格（内部接口，供秒杀服务调用）
     *
     * @param sessionId 场次ID
     * @param seatNumber 座位号
     * @return 座位价格
     */
    java.math.BigDecimal getSeatPrice(Long sessionId, String seatNumber);
}
