import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import {
    buildSeatPool,
    collectTaggedMetricRows,
    collectTaggedCounterRows,
    getMetricCount,
    getMetricRate,
    getMetricValue,
    postLockSeat,
    requestId
} from './common.js';

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
const STATUS0_LOG_LIMIT_PER_VU = Number(__ENV.STATUS0_LOG_LIMIT_PER_VU || 1);

let status0LoggedForVu = 0;

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

function logStatus0Sample(response, requestIdValue, userId, attempt, seatIds) {
    if (response.status !== 0 || status0LoggedForVu >= STATUS0_LOG_LIMIT_PER_VU) {
        return;
    }

    status0LoggedForVu += 1;
    const errorText = response && response.error ? String(response.error) : 'unknown';
    const errorCode = response && response.error_code ? String(response.error_code) : 'none';
    const bodyPreview = response && response.body ? String(response.body).slice(0, 160) : '';

    console.error(
        [
            'STATUS0_SAMPLE',
            `vu=${__VU}`,
            `attempt=${attempt}`,
            `userId=${userId}`,
            `requestId=${requestIdValue}`,
            `error=${JSON.stringify(errorText)}`,
            `error_code=${JSON.stringify(errorCode)}`,
            `seatIds=${JSON.stringify(seatIds)}`,
            `body=${JSON.stringify(bodyPreview)}`
        ].join(' ')
    );
}

function formatInteger(value) {
    return Math.round(Number(value || 0)).toString();
}

function formatPercentFromRate(rate) {
    return `${(Number(rate || 0) * 100).toFixed(2)}%`;
}

function formatDurationMs(value) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) {
        return 'n/a';
    }
    return `${Number(value).toFixed(2)}ms`;
}

function selectTransportFailureRows(data) {
    const directRows = collectTaggedCounterRows(data, 'transport_failure_total')
        .filter((row) => row.count > 0);
    if (directRows.length > 0) {
        return {
            source: 'transport_failure_total',
            rows: directRows
        };
    }

    const fallbackRows = collectTaggedMetricRows(data, ({ name, tags }) =>
        name.startsWith('http_req_failed{') && (tags.error || tags.error_code)
    ).filter((row) => row.count > 0);

    return {
        source: 'http_req_failed',
        rows: fallbackRows
    };
}

export function handleSummary(data) {
    const failureRows = selectTransportFailureRows(data);
    const rows = failureRows.rows.slice(0, 8);
    const lines = [
        'LOCK ONLY BURST SUMMARY',
        '',
        'Requests',
        `  total=${formatInteger(getMetricCount(data, 'http_reqs'))} 200=${formatInteger(getMetricCount(data, 'status_200_total'))} 429=${formatInteger(getMetricCount(data, 'status_429_total'))} status_0=${formatInteger(getMetricCount(data, 'status_0_total'))} other=${formatInteger(getMetricCount(data, 'status_other_total'))}`,
        `  http_req_failed=${formatPercentFromRate(getMetricRate(data, 'http_req_failed'))} ok_rate=${formatPercentFromRate(getMetricRate(data, 'ok_rate'))}`,
        '',
        'Business',
        `  success=${formatInteger(getMetricCount(data, 'lock_business_success_total'))} user_final_success_rate=${formatPercentFromRate(getMetricRate(data, 'user_final_success_rate'))}`,
        '',
        'Latency',
        `  http_req_duration avg=${formatDurationMs(getMetricValue(data, 'http_req_duration', 'avg'))} p95=${formatDurationMs(getMetricValue(data, 'http_req_duration', 'p(95)'))}`,
        ''
    ];

    lines.push('Transport Failure Breakdown');
    if (rows.length === 0) {
        lines.push('  none');
    } else {
        lines.push(`  source=${failureRows.source}`);
        for (const row of rows) {
            const error = row.tags.error || 'unknown';
            const errorCode = row.tags.error_code || 'none';
            lines.push(`  count=${formatInteger(row.count)} error=${error} error_code=${errorCode}`);
        }
    }

    return {
        stdout: `${lines.join('\n')}\n`
    };
}

export default function () {
    const seatIds = seatPool[(__VU - 1) % seatPool.length];
    const userId = USER_BASE + __VU;

    for (let attempt = 0; attempt < MAX_ATTEMPTS_PER_USER; attempt++) {
        const requestIdValue = requestId(__VU, attempt, 'lock-only-burst');
        const response = postLockSeat({
            userId,
            seatIds,
            requestIdValue
        });

        logStatus0Sample(response, requestIdValue, userId, attempt, seatIds);

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
