package com.duanyan.taopiaopiao.sessionservice.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.duanyan.taopiaopiao.common.exception.BusinessException;
import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionCreateRequest;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionPageResponse;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionQueryRequest;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionResponse;
import com.duanyan.taopiaopiao.sessionservice.api.dto.SessionUpdateRequest;
import com.duanyan.taopiaopiao.sessionservice.application.client.EventClient;
import com.duanyan.taopiaopiao.sessionservice.application.client.SeatTemplateClient;
import com.duanyan.taopiaopiao.sessionservice.application.client.dto.EventResponse;
import com.duanyan.taopiaopiao.sessionservice.application.client.dto.SeatTemplateResponse;
import com.duanyan.taopiaopiao.sessionservice.application.mapper.SeatMapper;
import com.duanyan.taopiaopiao.sessionservice.application.mapper.SessionMapper;
import com.duanyan.taopiaopiao.sessionservice.application.service.SessionService;
import com.duanyan.taopiaopiao.sessionservice.domain.entity.Seat;
import com.duanyan.taopiaopiao.sessionservice.domain.entity.Session;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 场次服务实现
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final SessionMapper sessionMapper;
    private final SeatMapper seatMapper;
    private final ObjectMapper objectMapper;
    private final EventClient eventClient;
    private final SeatTemplateClient seatTemplateClient;

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

        // 状态筛选
        if (StringUtils.hasText(request.getStatus())) {
            queryWrapper.eq(Session::getStatus, request.getStatus());
        }

        // 按ID递增排序
        queryWrapper.orderByAsc(Session::getId);

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

        // 查询关联的演出信息
        Map<Long, EventResponse> eventMap = fetchEventsByIds(List.of(session.getEventId()));

        return convertToResponse(session, eventMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSession(SessionCreateRequest request) {
        // 获取座位模板信息
        if (request.getSeatTemplateId() == null) {
            throw new BusinessException(400, "座位模板ID不能为空");
        }

        Result<SeatTemplateResponse> templateResult = seatTemplateClient.getTemplateById(request.getSeatTemplateId());
        if (templateResult == null || templateResult.getData() == null) {
            throw new BusinessException(404, "座位模板不存在");
        }
        SeatTemplateResponse template = templateResult.getData();

        // 转换为实体
        Session session = new Session();
        BeanUtils.copyProperties(request, session, "metadata");

        // 处理metadata（扩展配置）
        if (request.getMetadata() != null) {
            try {
                session.setMetadata(objectMapper.writeValueAsString(request.getMetadata()));
            } catch (Exception e) {
                log.error("metadata转换失败", e);
                throw new BusinessException(500, "扩展数据格式错误");
            }
        }

        // 初始化座位数
        session.setSoldSeats(0);
        session.setLockedSeats(0);
        // 从座位模板获取总座位数作为可售座位数
        session.setAvailableSeats(template.getTotalSeats());

        // 保存场次
        sessionMapper.insert(session);

        // 根据座位模板生成具体座位记录
        generateSeatsFromTemplate(session.getId(), template);

        return session.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSession(Long id, SessionUpdateRequest request) {
        // 检查场次是否存在
        Session existingSession = sessionMapper.selectById(id);
        if (existingSession == null) {
            throw new BusinessException(404, "场次不存在");
        }

        // 更新字段
        BeanUtils.copyProperties(request, existingSession, "metadata");

        // 处理metadata
        if (request.getMetadata() != null) {
            try {
                existingSession.setMetadata(objectMapper.writeValueAsString(request.getMetadata()));
            } catch (Exception e) {
                log.error("metadata转换失败", e);
                throw new BusinessException(500, "扩展数据格式错误");
            }
        }

        // 清空 updatedAt，让 MyBatis-Plus 自动填充
        existingSession.setUpdatedAt(null);

        // 保存更新
        sessionMapper.updateById(existingSession);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long id) {
        // 检查场次是否存在
        Session session = sessionMapper.selectById(id);
        if (session == null) {
            throw new BusinessException(404, "场次不存在");
        }

        // 检查是否可以删除
        if (session.getSoldSeats() != null && session.getSoldSeats() > 0) {
            throw new BusinessException(400, "该场次已有订单，无法删除");
        }

        // 检查状态
        if ("ended".equals(session.getStatus())) {
            throw new BusinessException(400, "已结束的场次不允许删除");
        }

        // 删除
        sessionMapper.deleteById(id);
    }

    @Override
    public void updateSessionStatus(Long id, String status) {
        // 检查场次是否存在
        Session session = sessionMapper.selectById(id);
        if (session == null) {
            throw new BusinessException(404, "场次不存在");
        }

        // 更新状态
        session.setStatus(status);
        // 清空 updatedAt，让 MyBatis-Plus 自动填充
        session.setUpdatedAt(null);
        sessionMapper.updateById(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer markSeatsSold(Long sessionId, java.util.List<String> seatIds, String orderNo) {
        log.info("标记座位已售出, sessionId: {}, seatIds: {}, orderNo: {}", sessionId, seatIds, orderNo);

        // 调用 Mapper 更新座位状态
        int updated = seatMapper.markSeatsSold(sessionId, seatIds, orderNo);
        log.info("成功更新{}条座位记录为已售出", updated);

        return updated;
    }

    /**
     * 转换为响应DTO（带关联信息）
     */
    private SessionResponse convertToResponse(Session session,
                                               Map<Long, EventResponse> eventMap) {
        SessionResponse response = new SessionResponse();
        BeanUtils.copyProperties(session, response);

        // 填充演出名称
        if (session.getEventId() != null && eventMap != null) {
            EventResponse event = eventMap.get(session.getEventId());
            if (event != null) {
                response.setEventName(event.getName());
            }
        }

        // 处理metadata
        if (StringUtils.hasText(session.getMetadata())) {
            try {
                response.setMetadata(objectMapper.readValue(
                        session.getMetadata(),
                        SessionResponse.SessionMetadata.class
                ));
            } catch (Exception e) {
                log.error("metadata解析失败: {}", e.getMessage());
            }
        }

        return response;
    }

    /**
     * 转换为响应DTO（无关联信息，兼容其他调用）
     */
    private SessionResponse convertToResponse(Session session) {
        return convertToResponse(session, null);
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
                // 调用 Feign Client
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
     * 根据座位模板生成具体座位记录
     *
     * @param sessionId 场次ID
     * @param template 座位模板
     */
    private void generateSeatsFromTemplate(Long sessionId, SeatTemplateResponse template) {
        try {
            // 解析layoutData
            if (template.getLayoutData() == null || template.getLayoutData().isEmpty()) {
                log.warn("座位模板{}的layoutData为空，跳过座位生成", template.getId());
                return;
            }

            // 解析JSON格式的layoutData
            Map<String, Object> layoutData = objectMapper.readValue(template.getLayoutData(), new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> areas = (List<Map<String, Object>>) layoutData.get("areas");

            if (areas == null || areas.isEmpty()) {
                log.warn("座位模板{}的areas为空，跳过座位生成", template.getId());
                return;
            }

            List<Seat> seatsToInsert = new ArrayList<>();

            // 遍历所有区域
            for (Map<String, Object> area : areas) {
                String areaCode = (String) area.get("areaCode");
                String areaName = (String) area.get("areaName");
                Object priceObj = area.get("price");
                BigDecimal areaPrice = priceObj != null ? new BigDecimal(priceObj.toString()) : BigDecimal.ZERO;

                List<Map<String, Object>> rows = (List<Map<String, Object>>) area.get("rows");
                if (rows == null) continue;

                // 遍历所有行
                for (Map<String, Object> row : rows) {
                    Integer rowNum = (Integer) row.get("rowNum");
                    String rowLabel = (String) row.get("rowLabel");
                    Integer startSeat = (Integer) row.get("startSeat");
                    Integer endSeat = (Integer) row.get("endSeat");

                    // 为每个座位创建记录
                    for (int seatNum = startSeat; seatNum <= endSeat; seatNum++) {
                        Seat seat = new Seat();
                        seat.setSessionId(sessionId);
                        seat.setSeatTemplateId(template.getId());

                        // 生成模板座位ID：区域-行-座 (如 A-1-01)
                        String templateSeatId = String.format("%s-%d-%02d", areaCode, rowNum, seatNum);
                        seat.setTemplateSeatId(templateSeatId);

                        // 座位编号：行标签+座号 (如 1排01座)
                        seat.setSeatNumber(String.format("%s%02d座", rowLabel, seatNum));

                        seat.setSeatRow(rowLabel);
                        seat.setSeatColumn(String.format("%02d", seatNum));
                        seat.setArea(areaName);
                        seat.setPrice(areaPrice);
                        seat.setStatus("available"); // 初始状态为可售

                        seatsToInsert.add(seat);
                    }
                }
            }

            // 批量插入座位
            if (!seatsToInsert.isEmpty()) {
                // 使用批量插入，每批最多1000条
                int batchSize = 1000;
                for (int i = 0; i < seatsToInsert.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, seatsToInsert.size());
                    List<Seat> batch = seatsToInsert.subList(i, end);
                    for (Seat seat : batch) {
                        seatMapper.insert(seat);
                    }
                }
                log.info("场次{}生成座位记录{}条", sessionId, seatsToInsert.size());
            }

        } catch (Exception e) {
            log.error("生成座位记录失败, sessionId: {}, error: {}", sessionId, e.getMessage(), e);
            throw new BusinessException(500, "生成座位记录失败: " + e.getMessage());
        }
    }

    @Override
    public boolean isSeatsMarkedSold(String orderNo) {
        // 检查是否已存在该订单号的座位记录
        Long count = seatMapper.selectCount(
                new LambdaQueryWrapper<Seat>()
                        .eq(Seat::getOrderNo, orderNo)
                        .eq(Seat::getStatus, "sold")
        );
        return count != null && count > 0;
    }
}
