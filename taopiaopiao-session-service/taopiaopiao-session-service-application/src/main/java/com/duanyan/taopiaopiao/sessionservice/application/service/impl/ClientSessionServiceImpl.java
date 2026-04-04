package com.duanyan.taopiaopiao.sessionservice.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duanyan.taopiaopiao.common.exception.BusinessException;
import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionPageResponse;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionQueryRequest;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionResponse;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionSeatsResponse;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SeatResponse;
import com.duanyan.taopiaopiao.sessionservice.application.client.EventClient;
import com.duanyan.taopiaopiao.sessionservice.application.client.dto.EventResponse;
import com.duanyan.taopiaopiao.sessionservice.application.mapper.SeatMapper;
import com.duanyan.taopiaopiao.sessionservice.application.mapper.SessionMapper;
import com.duanyan.taopiaopiao.sessionservice.application.service.ClientSessionService;
import com.duanyan.taopiaopiao.sessionservice.domain.entity.Session;
import com.duanyan.taopiaopiao.sessionservice.domain.entity.Seat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 场次客户端服务实现
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSessionServiceImpl implements ClientSessionService {

    private final SessionMapper sessionMapper;
    private final EventClient eventClient;
    private final SessionServiceImpl sessionService; // 复用管理端的转换逻辑
    private final SeatMapper seatMapper;

    @Override
    public SessionPageResponse getSessionPage(SessionQueryRequest request) {
        // 构建查询条件
        LambdaQueryWrapper<Session> queryWrapper = new LambdaQueryWrapper<>();

        // 关键词搜索（场次名称）
        if (StringUtils.hasText(request.getKeyword())) {
            queryWrapper.like(Session::getSessionName, request.getKeyword());
        }

        // 演出筛选
        if (request.getEventId() != null) {
            queryWrapper.eq(Session::getEventId, request.getEventId());
        }

        // 状态筛选 - 客户端只显示有效状态
        if (StringUtils.hasText(request.getStatus())) {
            queryWrapper.eq(Session::getStatus, request.getStatus());
        } else {
            // 默认只显示可售/即将开始的场次，不显示已结束/已取消的
            queryWrapper.notIn(Session::getStatus, "ended", "cancelled");
        }

        // 按开始时间升序排序（最近的场次在前）
        queryWrapper.orderByAsc(Session::getStartTime);

        // 分页查询
        Page<Session> page = new Page<>(request.getPage(), request.getPageSize());
        IPage<Session> sessionPage = sessionMapper.selectPage(page, queryWrapper);

        // 收集需要查询的 eventId
        List<Long> eventIds = sessionPage.getRecords().stream()
                .map(Session::getEventId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        // 批量查询关联信息
        Map<Long, EventResponse> eventMap = fetchEventsByIds(eventIds);

        // 转换为DTO并填充关联信息
        List<SessionResponse> sessionResponseList = sessionPage.getRecords().stream()
                .map(session -> convertToResponse(session, eventMap))
                .collect(Collectors.toList());

        // 计算总页数
        int totalPages = (int) Math.ceil((double) sessionPage.getTotal() / request.getPageSize());

        return SessionPageResponse.builder()
                .list(sessionResponseList)
                .total(sessionPage.getTotal())
                .page(request.getPage())
                .pageSize(request.getPageSize())
                .totalPages(totalPages)
                .build();
    }

    @Override
    public SessionResponse getSessionById(Long id) {
        Session session = sessionMapper.selectById(id);
        if (session == null) {
            throw new BusinessException(404, "场次不存在");
        }

        // 检查场次状态（客户端不能查看已取消的场次）
        if ("cancelled".equals(session.getStatus())) {
            throw new BusinessException(404, "场次不存在");
        }

        // 查询关联的演出信息
        Map<Long, EventResponse> eventMap = fetchEventsByIds(List.of(session.getEventId()));

        return convertToResponse(session, eventMap);
    }

    @Override
    public SessionSeatsResponse getSessionSeats(Long sessionId) {
        // 检查场次是否存在
        Session session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "场次不存在");
        }

        // 查询座位列表，按 id 升序排序
        List<Seat> seats = seatMapper.selectList(
                new LambdaQueryWrapper<Seat>()
                        .eq(Seat::getSessionId, sessionId)
                        .orderByAsc(Seat::getId)
        );

        // 转换为 DTO
        List<SeatResponse> seatResponses = seats.stream()
                .map(this::convertToSeatResponse)
                .collect(Collectors.toList());

        return SessionSeatsResponse.builder()
                .sessionId(sessionId)
                .seats(seatResponses)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer markSeatsSold(Long sessionId, java.util.List<String> seatIds, String orderNo) {
        log.info("标记座位已售出, sessionId: {}, seatIds: {}, orderNo: {}", sessionId, seatIds, orderNo);

        java.util.List<Long> seatIdList = seatIds.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        // 调用 Mapper 更新座位状态
        int updated = seatMapper.markSeatsSold(sessionId, seatIdList, orderNo);
        log.info("成功更新{}条座位记录为已售出", updated);

        return updated;
    }

    @Override
    public java.math.BigDecimal getSeatPrice(Long sessionId, String seatNumber) {
        log.info("查询单个座位价格: sessionId={}, seatNumber={}", sessionId, seatNumber);

        // 查询座位价格
        Seat seat = seatMapper.selectOne(
                new LambdaQueryWrapper<Seat>()
                        .eq(Seat::getSessionId, sessionId)
                        .eq(Seat::getId, seatNumber)
        );

        if (seat == null) {
            throw new BusinessException(404, "座位不存在: " + seatNumber);
        }

        if (!"available".equals(seat.getStatus())) {
            throw new BusinessException(400, "座位不可售: " + seatNumber);
        }

        log.info("查询座位价格成功: seatNumber={}, price={}", seatNumber, seat.getPrice());
        return seat.getPrice();
    }

    /**
     * 转换为座位响应DTO
     */
    private SeatResponse convertToSeatResponse(Seat seat) {
        return SeatResponse.builder()
                .id(seat.getId())
                .seatRow(seat.getSeatRow())
                .seatColumn(seat.getSeatColumn())
                .seatNumber(seat.getSeatNumber())
                .area(seat.getArea())
                .price(seat.getPrice())
                .status(seat.getStatus())
                .build();
    }

    /**
     * 批量获取演出信息
     */
    private Map<Long, EventResponse> fetchEventsByIds(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, EventResponse> result = new HashMap<>();
        for (Long id : eventIds) {
            try {
                Result<EventResponse> resp = eventClient.getEventById(id);
                if (resp != null && resp.getData() != null) {
                    result.put(id, resp.getData());
                }
            } catch (Exception e) {
                log.error("查询演出信息失败, eventId: {}, error: {}", id, e.getMessage());
            }
        }
        return result;
    }

    /**
     * 转换为响应DTO（带关联信息）
     */
    private SessionResponse convertToResponse(Session session,
                                               Map<Long, EventResponse> eventMap) {
        // 直接复用管理端服务的查询方法
        return sessionService.getSessionById(session.getId());
    }
}
