package com.duanyan.taopiaopiao.seckillservice.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanyan.taopiaopiao.seckillservice.domain.entity.LockOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LockOrderMapper extends BaseMapper<LockOrder> {

    int markOrderCreated(@Param("orderNo") String orderNo,
                         @Param("fromStatuses") List<Integer> fromStatuses,
                         @Param("toStatus") Integer toStatus);

    int updateStatus(@Param("orderNo") String orderNo,
                     @Param("fromStatuses") List<Integer> fromStatuses,
                     @Param("toStatus") Integer toStatus,
                     @Param("failReason") String failReason);

    List<LockOrder> selectRecoverableBatch(@Param("statuses") List<Integer> statuses,
                                           @Param("olderThan") LocalDateTime olderThan,
                                           @Param("limit") Integer limit);
}
