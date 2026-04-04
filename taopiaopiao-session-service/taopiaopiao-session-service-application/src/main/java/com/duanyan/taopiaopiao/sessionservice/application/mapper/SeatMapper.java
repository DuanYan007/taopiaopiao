package com.duanyan.taopiaopiao.sessionservice.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanyan.taopiaopiao.sessionservice.domain.entity.Seat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 座位Mapper
 */
@Mapper
public interface SeatMapper extends BaseMapper<Seat> {

    /**
     * 标记座位已售出
     *
     * @param sessionId 场次ID
     * @param seatIds 座位ID列表
     * @param orderNo 订单号
     * @return 更新数量
     */
    int markSeatsSold(@Param("sessionId") Long sessionId,
                       @Param("seatIds") List<Long> seatIds,
                       @Param("orderNo") String orderNo);
}
