USE `taopiaopiao`;

CREATE TABLE IF NOT EXISTS `order_prepare` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `xid` varchar(128) DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `session_id` bigint DEFAULT NULL,
  `event_id` bigint DEFAULT NULL,
  `seat_ids` json DEFAULT NULL,
  `seat_count` int DEFAULT NULL,
  `unit_price` decimal(10,2) DEFAULT NULL,
  `total_amount` decimal(10,2) DEFAULT NULL,
  `expire_time` datetime DEFAULT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_prepare_order_no` (`order_no`),
  KEY `idx_order_prepare_status` (`status`),
  KEY `idx_order_prepare_xid` (`xid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TCC订单预留表';
