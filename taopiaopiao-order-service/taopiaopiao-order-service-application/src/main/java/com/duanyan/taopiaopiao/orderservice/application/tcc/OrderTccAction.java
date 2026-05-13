package com.duanyan.taopiaopiao.orderservice.application.tcc;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@LocalTCC
public interface OrderTccAction {

    @TwoPhaseBusinessAction(
            name = "orderTccAction",
            commitMethod = "confirmPrepareOrder",
            rollbackMethod = "cancelPrepareOrder"
    )
    boolean tryPrepareOrder(
            BusinessActionContext actionContext,
            @BusinessActionContextParameter(paramName = "orderNo") String orderNo,
            @BusinessActionContextParameter(paramName = "userId") Long userId,
            @BusinessActionContextParameter(paramName = "sessionId") Long sessionId,
            @BusinessActionContextParameter(paramName = "eventId") Long eventId,
            @BusinessActionContextParameter(paramName = "seatIds") List<String> seatIds,
            @BusinessActionContextParameter(paramName = "seatCount") Integer seatCount,
            @BusinessActionContextParameter(paramName = "unitPrice") BigDecimal unitPrice,
            @BusinessActionContextParameter(paramName = "totalAmount") BigDecimal totalAmount,
            @BusinessActionContextParameter(paramName = "expireTime") LocalDateTime expireTime
    );

    boolean confirmPrepareOrder(BusinessActionContext actionContext);

    boolean cancelPrepareOrder(BusinessActionContext actionContext);
}
