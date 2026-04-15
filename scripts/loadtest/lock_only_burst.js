import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { buildSeatPool, postLockSeat, requestId } from './common.js';

const MAX_ATTEMPTS_PER_USER = Number(__ENV.MAX_ATTEMPTS_PER_USER || __ENV.REQUESTS_PER_USER || 3);
const REQUEST_INTERVAL_SECONDS = Number(__ENV.REQUEST_INTERVAL_SECONDS || 1);
const STOP_ON_409 = __ENV.STOP_ON_409 === 'true';
const STOP_ON_429 = __ENV.STOP_ON_429 === 'true';

const lockBusinessSuccess = new Counter('lock_business_success_total');
const lockBusinessConflict = new Counter('lock_business_conflict_total');
const lockBusinessDuplicate = new Counter('lock_business_duplicate_total');
const lockBusinessOtherFailure = new Counter('lock_business_other_failure_total');
const userStoppedAfterSuccess = new Counter('user_stopped_after_success_total');
const userStoppedAfterTerminalFailure = new Counter('user_stopped_after_terminal_failure_total');
const userReachedMaxAttempts = new Counter('user_reached_max_attempts_total');
const userFinalSuccessRate = new Rate('user_final_success_rate');

export const options = {
    scenarios: {
        lock_only_burst: {
            executor: 'per-vu-iterations',
            vus: Number(__ENV.USERS || 800),
            iterations: 1,
            maxDuration: __ENV.MAX_DURATION || '30s'
        }
    }
};

const USER_BASE = Number(__ENV.USER_BASE || 100000);
const seatPool = buildSeatPool({
    startSeatId: Number(__ENV.SEAT_START_ID || 1),
    endSeatId: Number(__ENV.SEAT_END_ID || 160),
    seatsPerRequest: Number(__ENV.SEATS_PER_REQUEST || 1)
});

if (seatPool.length === 0) {
    throw new Error('seatPool is empty, check SEAT_START_ID/SEAT_END_ID/SEATS_PER_REQUEST');
}

function parseResultBody(response) {
    if (!response || !response.body) {
        return null;
    }

    try {
        return JSON.parse(response.body);
    } catch (error) {
        return null;
    }
}

export default function () {
    const seatIds = seatPool[(__VU - 1) % seatPool.length];
    const userId = USER_BASE + __VU;

    for (let attempt = 0; attempt < MAX_ATTEMPTS_PER_USER; attempt++) {
        const response = postLockSeat({
            userId,
            seatIds,
            requestIdValue: requestId(__VU, attempt, 'lock-only-burst')
        });

        check(response, {
            'lock-only-burst status is 200/409/429': (res) => [200, 409, 429].includes(res.status)
        });

        const result = parseResultBody(response);

        if (response.status === 200 && result && result.code === 200 && result.data && result.data.code === 0) {
            lockBusinessSuccess.add(1);
            userStoppedAfterSuccess.add(1);
            userFinalSuccessRate.add(true);
            return;
        }

        if (response.status === 200 && result && result.code !== 200) {
            if (result.code === 2) {
                lockBusinessConflict.add(1);
            } else if (result.code === 3) {
                lockBusinessDuplicate.add(1);
            } else {
                lockBusinessOtherFailure.add(1);
            }
            userStoppedAfterTerminalFailure.add(1);
            userFinalSuccessRate.add(false);
            return;
        }

        if (response.status === 409 && STOP_ON_409) {
            userStoppedAfterTerminalFailure.add(1);
            userFinalSuccessRate.add(false);
            return;
        }

        if (response.status === 429 && STOP_ON_429) {
            userStoppedAfterTerminalFailure.add(1);
            userFinalSuccessRate.add(false);
            return;
        }

        if (attempt + 1 < MAX_ATTEMPTS_PER_USER && REQUEST_INTERVAL_SECONDS > 0) {
            sleep(REQUEST_INTERVAL_SECONDS);
        }
    }

    userReachedMaxAttempts.add(1);
    userFinalSuccessRate.add(false);
}
