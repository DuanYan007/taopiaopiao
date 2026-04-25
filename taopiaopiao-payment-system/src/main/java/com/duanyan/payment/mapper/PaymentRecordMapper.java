package com.duanyan.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duanyan.payment.domain.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付记录 Mapper
 *
 * @author duanyan
 * @since 1.0.0
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
}
