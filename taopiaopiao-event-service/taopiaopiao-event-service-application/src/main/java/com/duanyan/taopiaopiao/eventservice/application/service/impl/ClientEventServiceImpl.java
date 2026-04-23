package com.duanyan.taopiaopiao.eventservice.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duanyan.taopiaopiao.common.exception.BusinessException;
import com.duanyan.taopiaopiao.common.response.Result;
import com.duanyan.taopiaopiao.eventservice.api.dto.EventPageResponse;
import com.duanyan.taopiaopiao.eventservice.api.dto.EventQueryRequest;
import com.duanyan.taopiaopiao.eventservice.api.dto.EventResponse;
import com.duanyan.taopiaopiao.eventservice.api.dto.SessionBriefPageResponse;
import com.duanyan.taopiaopiao.eventservice.api.dto.SessionBriefResponse;
import com.duanyan.taopiaopiao.eventservice.application.client.SessionClient;
import com.duanyan.taopiaopiao.eventservice.application.client.dto.SessionPageResponse;
import com.duanyan.taopiaopiao.eventservice.application.client.dto.SessionResponse;
import com.duanyan.taopiaopiao.eventservice.application.mapper.EventMapper;
import com.duanyan.taopiaopiao.eventservice.application.service.ClientEventService;
import com.duanyan.taopiaopiao.eventservice.domain.entity.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 演出客户端服务实现
 *
 * @author duanyan
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientEventServiceImpl implements ClientEventService {

    private final EventMapper eventMapper;
    private final SessionClient sessionClient;
    private final EventServiceImpl eventService; // 复用管理端的转换逻辑

    @Override
    public EventPageResponse getEventPage(EventQueryRequest request) {
        // 构建查询条件
        LambdaQueryWrapper<Event> queryWrapper = new LambdaQueryWrapper<>();

        // 关键词搜索（演出名称、艺人）
        if (StringUtils.hasText(request.getKeyword())) {
            queryWrapper.and(wrapper -> wrapper
                    .like(Event::getName, request.getKeyword())
                    .or()
                    .like(Event::getArtist, request.getKeyword()));
        }

        // 城市筛选
        if (StringUtils.hasText(request.getCity())) {
            queryWrapper.eq(Event::getCity, request.getCity());
        }

        // 类型筛选
        if (StringUtils.hasText(request.getType())) {
            queryWrapper.eq(Event::getType, request.getType());
        }

        // 状态筛选 - 客户端只显示有效状态
        if (StringUtils.hasText(request.getStatus())) {
            queryWrapper.eq(Event::getStatus, request.getStatus());
        } else {
            // 默认只显示非草稿、非取消的演出
            queryWrapper.notIn(Event::getStatus, "draft", "cancelled");
        }

        // 按创建时间倒序排序（新的在前）
        queryWrapper.orderByDesc(Event::getCreatedAt);

        // 分页查询
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Event> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        request.getPage(), request.getPageSize());
        com.baomidou.mybatisplus.core.metadata.IPage<Event> eventPage = eventMapper.selectPage(page, queryWrapper);

        // 转换为DTO（复用管理端的转换逻辑）
        java.util.List<EventResponse> eventResponseList = eventPage.getRecords().stream()
                .map(eventService::convertToResponse)
                .collect(java.util.stream.Collectors.toList());

        return EventPageResponse.builder()
                .list(eventResponseList)
                .total(eventPage.getTotal())
                .page(request.getPage())
                .pageSize(request.getPageSize())
                .build();
    }

    @Override
    public EventResponse getEventById(Long id) {
        Event event = eventMapper.selectById(id);
        if (event == null) {
            throw new BusinessException(404, "演出不存在");
        }

        // 检查演出状态（客户端不能查看草稿状态的演出）
        if ("draft".equals(event.getStatus())) {
            throw new BusinessException(404, "演出不存在");
        }

        return eventService.convertToResponse(event);
    }

    @Override
    public SessionBriefPageResponse getEventSessions(Long eventId, String status, Integer page, Integer pageSize) {
        // 先检查演出是否存在
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException(404, "演出不存在");
        }

        try {
            Result<SessionPageResponse> response = sessionClient.getSessionPage(null, eventId, status, page, pageSize);
            if (response != null && response.getData() != null) {
                return toSessionBriefPageResponse(response.getData());
            }
            throw new BusinessException(500, "查询场次失败");
        } catch (Exception e) {
            log.error("调用场次服务失败, eventId: {}, error: {}", eventId, e.getMessage());
            throw new BusinessException(500, "查询场次失败: " + e.getMessage());
        }
    }

    private SessionBriefPageResponse toSessionBriefPageResponse(SessionPageResponse response) {
        List<SessionBriefResponse> list = response.getList() == null
                ? List.of()
                : response.getList().stream().map(this::toSessionBriefResponse).toList();

        return SessionBriefPageResponse.builder()
                .list(list)
                .total(response.getTotal())
                .page(response.getPage())
                .pageSize(response.getPageSize())
                .totalPages(response.getTotalPages())
                .build();
    }

    private SessionBriefResponse toSessionBriefResponse(SessionResponse response) {
        return SessionBriefResponse.builder()
                .id(response.getId())
                .eventId(response.getEventId())
                .sessionName(response.getSessionName())
                .startTime(response.getStartTime())
                .endTime(response.getEndTime())
                .seatTemplateId(response.getSeatTemplateId())
                .address(response.getAddress())
                .availableSeats(response.getAvailableSeats())
                .soldSeats(response.getSoldSeats())
                .lockedSeats(response.getLockedSeats())
                .status(response.getStatus())
                .metadata(toSessionMetadata(response.getMetadata()))
                .createdAt(response.getCreatedAt())
                .updatedAt(response.getUpdatedAt())
                .build();
    }

    private SessionBriefResponse.SessionMetadata toSessionMetadata(SessionResponse.SessionMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        return SessionBriefResponse.SessionMetadata.builder()
                .duration(metadata.getDuration())
                .saleStartTime(metadata.getSaleStartTime())
                .saleEndTime(metadata.getSaleEndTime())
                .seatSelectionMode(metadata.getSeatSelectionMode())
                .requireRealName(metadata.getRequireRealName())
                .limitOnePerPerson(metadata.getLimitOnePerPerson())
                .noRefund(metadata.getNoRefund())
                .sortOrder(metadata.getSortOrder())
                .remark(metadata.getRemark())
                .build();
    }
}
