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

export function buildSeatPool() {
    return [
        ['1'], ['2'], ['3'], ['4'], ['5'],
        ['6'], ['7'], ['8'], ['9'], ['10'],
        ['11'], ['12'], ['13'], ['14'], ['15'],
        ['16'], ['17'], ['18'], ['19'], ['20']
    ];
}
