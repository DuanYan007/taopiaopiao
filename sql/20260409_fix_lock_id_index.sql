ALTER TABLE seat_locks
    DROP INDEX uk_lock_id,
    ADD KEY idx_lock_id (lock_id);
