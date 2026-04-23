import http from 'k6/http';
import { Counter, Rate } from 'k6/metrics';

export const success200 = new Counter('status_200_total');
export const conflict409 = new Counter('status_409_total');
export const throttle429 = new Counter('status_429_total');
export const otherStatus = new Counter('status_other_total');
export const status0 = new Counter('status_0_total');
export const unexpected4xx = new Counter('status_unexpected_4xx_total');
export const status5xx = new Counter('status_5xx_total');
export const unexpectedOtherStatus = new Counter('status_unexpected_other_total');
export const transportFailureTotal = new Counter('transport_failure_total');
export const transportFailureRate = new Rate('transport_failure_rate');
export const okRate = new Rate('ok_rate');

export const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1/api/seckill/lock';
export const SESSION_ID = Number(__ENV.SESSION_ID || 1);
export const UNIT_PRICE = Number(__ENV.UNIT_PRICE || 1280);

export function requestId(vu, iter, tag) {
    return `k6-${tag}-${vu}-${iter}-${Date.now()}`;
}

function sanitizeTagValue(value) {
    const normalized = String(value || 'unknown').trim().replace(/[\s=,]+/g, '_');
    return normalized.length > 64 ? normalized.slice(0, 64) : normalized;
}

function hasTransportFailure(response, status) {
    return status === 0;
}

function markTransportFailure(response) {
    transportFailureTotal.add(1, {
        error: sanitizeTagValue(response && response.error ? response.error : 'unknown'),
        error_code: sanitizeTagValue(response && response.error_code ? response.error_code : 'none')
    });
}

export function markStatus(response) {
    const status = response && Number.isFinite(response.status) ? response.status : -1;
    const transportFailure = hasTransportFailure(response, status);

    transportFailureRate.add(transportFailure);
    if (transportFailure) {
        markTransportFailure(response);
    }

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
        if (status === 0) {
            status0.add(1);
        } else if (status >= 400 && status < 500) {
            unexpected4xx.add(1);
        } else if (status >= 500 && status < 600) {
            status5xx.add(1);
        } else {
            unexpectedOtherStatus.add(1);
        }
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
    markStatus(response);
    return response;
}

function getMetric(data, metricName) {
    return data && data.metrics ? data.metrics[metricName] : null;
}

export function getMetricCount(data, metricName) {
    const metric = getMetric(data, metricName);
    if (!metric || !metric.values) {
        return 0;
    }
    return Number(metric.values.count || 0);
}

export function getMetricRate(data, metricName) {
    const metric = getMetric(data, metricName);
    if (!metric || !metric.values) {
        return 0;
    }
    return Number(metric.values.rate || 0);
}

export function getMetricValue(data, metricName, valueName) {
    const metric = getMetric(data, metricName);
    if (!metric || !metric.values || metric.values[valueName] === undefined) {
        return null;
    }
    return Number(metric.values[valueName]);
}

function parseMetricTags(rawTags) {
    if (!rawTags) {
        return {};
    }

    const tags = {};
    for (const part of rawTags.split(',')) {
        const separatorIndex = part.indexOf(':');
        if (separatorIndex === -1) {
            continue;
        }
        const key = part.slice(0, separatorIndex).trim();
        const value = part.slice(separatorIndex + 1).trim();
        tags[key] = value;
    }
    return tags;
}

function getMetricCountLikeValue(metric) {
    if (!metric || !metric.values) {
        return 0;
    }

    if (metric.values.count !== undefined) {
        return Number(metric.values.count || 0);
    }
    if (metric.values.fails !== undefined) {
        return Number(metric.values.fails || 0);
    }
    if (metric.values.value !== undefined) {
        return Number(metric.values.value || 0);
    }
    return 0;
}

export function collectTaggedCounterRows(data, metricName) {
    if (!data || !data.metrics) {
        return [];
    }

    const prefix = `${metricName}{`;
    const rows = [];
    for (const [name, metric] of Object.entries(data.metrics)) {
        if (!name.startsWith(prefix) || !metric || !metric.values) {
            continue;
        }

        const count = Number(metric.values.count || 0);
        const openBraceIndex = name.indexOf('{');
        const closeBraceIndex = name.lastIndexOf('}');
        const rawTags = openBraceIndex === -1 || closeBraceIndex === -1
            ? ''
            : name.slice(openBraceIndex + 1, closeBraceIndex);

        rows.push({
            name,
            count,
            tags: parseMetricTags(rawTags)
        });
    }

    rows.sort((left, right) => right.count - left.count || left.name.localeCompare(right.name));
    return rows;
}

export function collectTaggedMetricRows(data, predicate) {
    if (!data || !data.metrics) {
        return [];
    }

    const rows = [];
    for (const [name, metric] of Object.entries(data.metrics)) {
        const openBraceIndex = name.indexOf('{');
        const closeBraceIndex = name.lastIndexOf('}');
        if (openBraceIndex === -1 || closeBraceIndex === -1 || closeBraceIndex <= openBraceIndex) {
            continue;
        }

        const rawTags = name.slice(openBraceIndex + 1, closeBraceIndex);
        const tags = parseMetricTags(rawTags);
        if (!predicate({ name, metric, tags })) {
            continue;
        }

        rows.push({
            name,
            count: getMetricCountLikeValue(metric),
            tags
        });
    }

    rows.sort((left, right) => right.count - left.count || left.name.localeCompare(right.name));
    return rows;
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
