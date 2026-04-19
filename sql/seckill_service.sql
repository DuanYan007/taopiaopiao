-- 秒杀服务 - 座位锁定表
CREATE TABLE IF NOT EXISTS seat_locks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    session_id BIGINT NOT NULL COMMENT '场次ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    seat_id VARCHAR(50) NOT NULL COMMENT '座位ID（seats 表主键ID的字符串形式）',
    lock_id VARCHAR(64) NOT NULL COMMENT '锁ID',
    seat_row INT NOT NULL COMMENT '座位行号',
    seat_col INT NOT NULL COMMENT '座位列号',
    lock_time BIGINT NOT NULL COMMENT '锁定时间戳(毫秒)',
    expire_time BIGINT NOT NULL COMMENT '过期时间戳(毫秒)',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-已释放, 1-已锁定, 2-已支付, 3-已超时释放',
    order_no VARCHAR(64) DEFAULT NULL COMMENT '订单号',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_lock_id (lock_id),
    KEY idx_session_user_seat (session_id, user_id, seat_id),
    KEY idx_session_status (session_id, status),
    KEY idx_expire_time (expire_time),
    KEY idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='座位锁定表';
