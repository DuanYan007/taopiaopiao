package com.duanyan.taopiaopiao.seckillservice.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanyan.taopiaopiao.seckillservice.domain.entity.SeatLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SeatLockMapper extends BaseMapper<SeatLock> {

    int updateStatus(@Param("sessionId") Long sessionId, @Param("userId") Long userId,
                      @Param("seatId") String seatId, @Param("status") Integer status);

    int updateOrderNo(@Param("sessionId") Long sessionId, @Param("userId") Long userId,
                      @Param("seatId") String seatId, @Param("lockId") String lockId,
                      @Param("orderNo") String orderNo);

    int markAsPaid(@Param("sessionId") Long sessionId, @Param("userId") Long userId,
                   @Param("seatId") String seatId, @Param("lockId") String lockId,
                   @Param("orderNo") String orderNo);

    int updateStatusByLock(@Param("sessionId") Long sessionId, @Param("userId") Long userId,
                           @Param("seatId") String seatId, @Param("lockId") String lockId,
                           @Param("fromStatus") Integer fromStatus, @Param("toStatus") Integer toStatus);
}
