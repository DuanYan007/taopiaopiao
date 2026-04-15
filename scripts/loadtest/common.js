import http from 'k6/http';
import { Counter, Rate } from 'k6/metrics';

export const success200 = new Counter('status_200_total');
export const conflict409 = new Counter('status_409_total');
export const throttle429 = new Counter('status_429_total');
export const otherStatus = new Counter('status_other_total');
export const okRate = new Rate('ok_rate');

export const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1/api/seckill/lock';
export const SESSION_ID = Number(__ENV.SESSION_ID || 1);
export const UNIT_PRICE = Number(__ENV.UNIT_PRICE || 1280);

export function requestId(vu, iter, tag) {
    return `k6-${tag}-${vu}-${iter}-${Date.now()}`;
}

export function markStatus(status) {
    if (status === 200) {
        success200.add(1);
        okRate.add(true);
        return;
    }

    okRate.add(false);
    if (status === 409) {
        conflict409.add(1);
    } else if (status === 429) {
        throttle429.add(1);
    } else {
        otherStatus.add(1);
    }
}

export function postLockSeat({ userId, seatIds, requestIdValue }) {
    const payload = JSON.stringify({
        sessionId: SESSION_ID,
        seatIds,
        unitPrice: UNIT_PRICE
    });

    const headers = {
        'Content-Type': 'application/json',
        'X-User-Id': String(userId),
        'X-Request-Id': requestIdValue
    };

    const response = http.post(BASE_URL, payload, { headers });
    markStatus(response.status);
    return response;
}

export function buildSeatPool({
    startSeatId = Number(__ENV.SEAT_START_ID || 1),
    endSeatId = Number(__ENV.SEAT_END_ID || 20),
    seatsPerRequest = Number(__ENV.SEATS_PER_REQUEST || 1)
} = {}) {
    const seatPool = [];

    if (startSeatId > endSeatId || seatsPerRequest <= 0) {
        return seatPool;
    }

    for (let current = startSeatId; current <= endSeatId; current += seatsPerRequest) {
        const seatIds = [];
        for (let offset = 0; offset < seatsPerRequest && current + offset <= endSeatId; offset++) {
            seatIds.push(String(current + offset));
        }
        if (seatIds.length === seatsPerRequest) {
            seatPool.push(seatIds);
        }
    }

    return seatPool;
}
