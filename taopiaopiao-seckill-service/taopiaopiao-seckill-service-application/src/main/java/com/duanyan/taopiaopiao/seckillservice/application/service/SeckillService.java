package com.duanyan.taopiaopiao.seckillservice.application.service;

import com.duanyan.taopiaopiao.seckillservice.api.dto.LockSeatRequest;
import com.duanyan.taopiaopiao.seckillservice.api.dto.LockSeatResponse;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SessionInitRequest;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SessionInitResponse;
import com.duanyan.taopiaopiao.seckillservice.api.dto.SessionLayoutResponse;

public interface SeckillService {

    /**
     * 锁定座位
     */
    LockSeatResponse lockSeats(LockSeatRequest request, Long userId, String requestId);

    /**
     * 获取场次座位布局（含状态）
     *
     * @param sessionId 场次ID
     * @return 座位布局
     */
    SessionLayoutResponse getLayout(Long sessionId);

    /**
     * 初始化场次缓存
     *
     * @param request 初始化请求
     * @return 初始化结果
     */
    SessionInitResponse initSession(SessionInitRequest request);
}
