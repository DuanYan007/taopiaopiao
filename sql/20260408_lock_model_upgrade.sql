ALTER TABLE orders
    ADD COLUMN lock_id VARCHAR(64) NULL COMMENT '锁ID' AFTER session_id;

UPDATE orders
SET lock_id = CONCAT('legacy-order-', id)
WHERE lock_id IS NULL OR lock_id = '';

ALTER TABLE orders
    MODIFY COLUMN lock_id VARCHAR(64) NOT NULL COMMENT '锁ID',
    ADD KEY idx_lock_id (lock_id);

ALTER TABLE seat_locks
    ADD COLUMN lock_id VARCHAR(64) NULL COMMENT '锁ID' AFTER seat_id;

UPDATE seat_locks
SET lock_id = CONCAT('legacy-lock-', id)
WHERE lock_id IS NULL OR lock_id = '';

ALTER TABLE seat_locks
    MODIFY COLUMN lock_id VARCHAR(64) NOT NULL COMMENT '锁ID',
    DROP INDEX uk_session_user_seat,
    ADD KEY idx_lock_id (lock_id),
    ADD KEY idx_session_user_seat (session_id, user_id, seat_id),
    ADD KEY idx_order_no (order_no);
