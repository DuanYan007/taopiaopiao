package com.duanyan.taopiaopiao.seckillservice.application.tcc;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

import java.util.List;

@LocalTCC
public interface SeatTccAction {

    @TwoPhaseBusinessAction(
            name = "seatTccAction",
            commitMethod = "confirmReserve",
            rollbackMethod = "cancelReserve"
    )
    boolean tryReserve(
            BusinessActionContext actionContext,
            @BusinessActionContextParameter(paramName = "orderNo") String orderNo,
            @BusinessActionContextParameter(paramName = "userId") Long userId,
            @BusinessActionContextParameter(paramName = "sessionId") Long sessionId,
            @BusinessActionContextParameter(paramName = "eventId") Long eventId,
            @BusinessActionContextParameter(paramName = "seatIds") List<String> seatIds,
            @BusinessActionContextParameter(paramName = "expireSeconds") Integer expireSeconds
    );

    boolean confirmReserve(BusinessActionContext actionContext);

    boolean cancelReserve(BusinessActionContext actionContext);
}
