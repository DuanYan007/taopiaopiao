package com.duanyan.taopiaopiao.seckillservice.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanyan.taopiaopiao.seckillservice.application.model.SeatPositionRecord;
import com.duanyan.taopiaopiao.seckillservice.domain.entity.SeatLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeatLockMapper extends BaseMapper<SeatLock> {

    List<SeatPositionRecord> selectSeatPositions(@Param("sessionId") Long sessionId,
                                                 @Param("seatIds") List<String> seatIds);

    int batchInsert(@Param("seatLocks") List<SeatLock> seatLocks);

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
