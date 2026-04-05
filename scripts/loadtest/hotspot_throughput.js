import { check, sleep } from 'k6';
import { buildSeatPool, postLockSeat, requestId } from './common.js';

export const options = {
    scenarios: {
        hotspot_throughput: {
            executor: 'constant-vus',
            vus: Number(__ENV.VUS || 50),
            duration: __ENV.DURATION || '60s'
        }
    }
};

const USER_BASE = Number(__ENV.USER_BASE || 3000);
const seatPool = buildSeatPool();

export default function () {
    const seatIds = seatPool[(__VU + __ITER) % seatPool.length];
    const response = postLockSeat({
        userId: USER_BASE + __VU,
        seatIds,
        requestIdValue: requestId(__VU, __ITER, 'throughput')
    });

    check(response, {
        'hotspot-throughput status is 200/409/429': (res) => [200, 409, 429].includes(res.status)
    });

    sleep(Number(__ENV.SLEEP_SECONDS || 0.2));
}
