import { check, sleep } from 'k6';
import { postLockSeat, requestId } from './common.js';

export const options = {
    scenarios: {
        repeat_click: {
            executor: 'constant-vus',
            vus: Number(__ENV.VUS || 10),
            duration: __ENV.DURATION || '30s'
        }
    }
};

const REPEAT_USER_ID = Number(__ENV.REPEAT_USER_ID || 1);
const REPEAT_SEATS = (__ENV.REPEAT_SEATS || '1,2').split(',');

export default function () {
    const response = postLockSeat({
        userId: REPEAT_USER_ID,
        seatIds: REPEAT_SEATS,
        requestIdValue: requestId(__VU, __ITER, 'repeat')
    });

    check(response, {
        'repeat-click status is 200/409/429': (res) => [200, 409, 429].includes(res.status)
    });

    sleep(Number(__ENV.SLEEP_SECONDS || 0.05));
}
