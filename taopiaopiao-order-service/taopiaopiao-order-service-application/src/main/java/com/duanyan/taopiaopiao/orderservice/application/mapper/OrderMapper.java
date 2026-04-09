package com.duanyan.taopiaopiao.orderservice.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanyan.taopiaopiao.orderservice.domain.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    int markPaidIfUnpaid(@Param("orderNo") String orderNo, @Param("paidStatus") Integer paidStatus);

    int markTimeoutIfUnpaid(@Param("orderNo") String orderNo, @Param("timeoutStatus") Integer timeoutStatus);
}
