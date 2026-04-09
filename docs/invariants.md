# Invariants

## Business Invariants
- `X-User-Id` from trusted headers is the effective user identity for lock-seat requests.
- A seat cannot be sold to two users for the same session.
- `ORDER_PAID` means payment has succeeded, not merely that order creation succeeded.
- Payment success side effects must eventually complete even if they are split across multiple consumers.
- Cancellation only applies to unpaid orders. Paid orders must not be rolled back by timeout handlers.

## Data Invariants
- Redis is the fast seat-state source on the hot path.
- MySQL is the durable source for orders and `seat_locks`.
- `seat_locks` and Redis seat state must converge after paid or cancel processing.
- Consumer retries must be safe; repeated consumption must not duplicate final effects.

## Design Invariants
- High concurrency favors eventual consistency over cross-service strong consistency.
- OpenResty protects the backend before traffic reaches gateway and Java services.
- Changes to payment or cancellation must be reviewed end-to-end: producer, listener, consumer, Redis, MySQL.

## Before Merging a Change
- Confirm which state transitions are allowed.
- Confirm retry behavior and idempotency point.
- Confirm failure recovery path, not just happy path.

## Known Follow-up
- Payment-success handling still needs a follow-up for the case where payment confirmation arrives after the seat lock TTL has expired.
- In that scenario, if another user has already re-locked and purchased the same seat, the old paid event must not be treated as a harmless idempotent success.
- The required follow-up is: detect lock-owner mismatch or missing lock during paid-event processing, move the old order into an explicit compensation path such as refund-pending, and add a compensating job or workflow to close the loop.
