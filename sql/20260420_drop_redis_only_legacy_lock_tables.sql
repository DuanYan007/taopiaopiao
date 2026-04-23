-- Drop legacy lock audit tables after moving runtime to Redis-only.
-- Safe to run multiple times.

DROP TABLE IF EXISTS seat_locks;
DROP TABLE IF EXISTS lock_orders;
