-- Normalize orders.seat_ids and lock_orders.seat_ids_json to JSON arrays of strings.
-- Safe to re-run.

ALTER TABLE orders
    MODIFY COLUMN seat_ids JSON NOT NULL COMMENT '座位ID列表(JSON数组)';

ALTER TABLE lock_orders
    MODIFY COLUMN seat_ids_json JSON NOT NULL COMMENT '座位ID列表(JSON数组)';

UPDATE orders
SET seat_ids = JSON_ARRAY(CAST(JSON_UNQUOTE(JSON_EXTRACT(seat_ids, '$')) AS CHAR))
WHERE JSON_TYPE(seat_ids) IN ('INTEGER', 'STRING');

CREATE TEMPORARY TABLE tmp_normalized_orders_seat_ids (
    id BIGINT PRIMARY KEY,
    seat_ids JSON NOT NULL
);

INSERT INTO tmp_normalized_orders_seat_ids (id, seat_ids)
SELECT o.id,
       CONCAT(
           '[',
           GROUP_CONCAT(JSON_QUOTE(CAST(JSON_UNQUOTE(j.seat_id) AS CHAR)) ORDER BY j.ord SEPARATOR ','),
           ']'
       )
FROM orders o
JOIN JSON_TABLE(
    o.seat_ids,
    '$[*]' COLUMNS (
        ord FOR ORDINALITY,
        seat_id JSON PATH '$'
    )
) j
WHERE JSON_TYPE(o.seat_ids) = 'ARRAY'
GROUP BY o.id;

UPDATE orders o
JOIN tmp_normalized_orders_seat_ids t ON t.id = o.id
SET o.seat_ids = t.seat_ids;

DROP TEMPORARY TABLE tmp_normalized_orders_seat_ids;

UPDATE lock_orders
SET seat_ids_json = JSON_ARRAY(CAST(JSON_UNQUOTE(JSON_EXTRACT(seat_ids_json, '$')) AS CHAR))
WHERE JSON_TYPE(seat_ids_json) IN ('INTEGER', 'STRING');

CREATE TEMPORARY TABLE tmp_normalized_lock_orders_seat_ids (
    id BIGINT PRIMARY KEY,
    seat_ids_json JSON NOT NULL
);

INSERT INTO tmp_normalized_lock_orders_seat_ids (id, seat_ids_json)
SELECT l.id,
       CONCAT(
           '[',
           GROUP_CONCAT(JSON_QUOTE(CAST(JSON_UNQUOTE(j.seat_id) AS CHAR)) ORDER BY j.ord SEPARATOR ','),
           ']'
       )
FROM lock_orders l
JOIN JSON_TABLE(
    l.seat_ids_json,
    '$[*]' COLUMNS (
        ord FOR ORDINALITY,
        seat_id JSON PATH '$'
    )
) j
WHERE JSON_TYPE(l.seat_ids_json) = 'ARRAY'
GROUP BY l.id;

UPDATE lock_orders l
JOIN tmp_normalized_lock_orders_seat_ids t ON t.id = l.id
SET l.seat_ids_json = t.seat_ids_json;

DROP TEMPORARY TABLE tmp_normalized_lock_orders_seat_ids;
