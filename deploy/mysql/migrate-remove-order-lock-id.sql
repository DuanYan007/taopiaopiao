SET NAMES utf8mb4;

USE `taopiaopiao`;

ALTER TABLE `orders`
  DROP INDEX `idx_lock_id`,
  DROP COLUMN `lock_id`;
