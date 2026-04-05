import { check, sleep } from 'k6';
import { postLockSeat, requestId } from './common.js';

export const options = {
    scenarios: {
        hotspot_conflict: {
            executor: 'constant-vus',
            vus: Number(__ENV.VUS || 50),
            duration: __ENV.DURATION || '60s'
        }
    }
};

const HOT_SEATS = (__ENV.HOT_SEATS || '1,2').split(',');
const USER_BASE = Number(__ENV.USER_BASE || 2000);

export default function () {
    const response = postLockSeat({
        userId: USER_BASE + __VU,
        seatIds: HOT_SEATS,
        requestIdValue: requestId(__VU, __ITER, 'conflict')
    });

    check(response, {
        'hotspot-conflict status is 200/409/429': (res) => [200, 409, 429].includes(res.status)
    });

    sleep(Number(__ENV.SLEEP_SECONDS || 0.2));
}
