-- 支付系统独立数据库
CREATE DATABASE IF NOT EXISTS payment_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE payment_db;

-- 支付记录表
CREATE TABLE IF NOT EXISTS payment_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    order_no        VARCHAR(64) NOT NULL COMMENT '业务订单号（来自订单服务）',
    payment_no      VARCHAR(64) NOT NULL COMMENT '支付流水号（支付系统生成）',
    amount          DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待支付, SUCCESS-成功, FAILED-失败, CANCELLED-已取消',
    transaction_id  VARCHAR(128) COMMENT '第三方交易号（模拟支付宝/微信）',
    pay_method      VARCHAR(20) COMMENT '支付方式：ALIPAY, WECHAT, MOCK',
    client_ip       VARCHAR(50) COMMENT '客户端IP',
    return_url      VARCHAR(255) COMMENT '支付完成跳转地址',
    notify_url      VARCHAR(255) COMMENT '异步通知地址',
    body            VARCHAR(255) COMMENT '商品描述',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    paid_at         DATETIME COMMENT '支付时间',
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_order_no (order_no),
    UNIQUE KEY uk_payment_no (payment_no),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';

-- 支付回调日志表（记录所有回调请求）
CREATE TABLE IF NOT EXISTS payment_callback_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    payment_no      VARCHAR(64) NOT NULL COMMENT '支付流水号',
    order_no        VARCHAR(64) NOT NULL COMMENT '业务订单号',
    callback_type   VARCHAR(20) NOT NULL COMMENT '回调类型：SYNC-同步, ASYNC-异步',
    request_body    TEXT COMMENT '请求内容',
    response_status INT COMMENT 'HTTP响应状态码',
    response_body   TEXT COMMENT '响应内容',
    is_success      TINYINT(1) DEFAULT 0 COMMENT '是否处理成功',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '回调时间',
    INDEX idx_payment_no (payment_no),
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调日志表';
