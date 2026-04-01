package com.duanyan.taopiaopiao.orderservice.application.client;

import com.duanyan.taopiaopiao.common.response.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 秒杀服务客户端（内部调用）
 */
@FeignClient(name = "seckill-service", path = "/internal/seats")
public interface SeckillInternalClient {

    @PostMapping("/mark-paid")
    Result<Integer> markSeatLocksPaid(@RequestParam("orderNo") String orderNo,
                                      @RequestParam("sessionId") Long sessionId,
                                      @RequestParam("userId") Long userId,
                                      @RequestParam("seatIds") List<String> seatIds);

    @PostMapping("/release")
    Result<String> releaseSeats(@RequestParam("sessionId") Long sessionId,
                                @RequestParam("userId") Long userId,
                                @RequestParam("seatIds") List<String> seatIds);
}
