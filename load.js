// k6 script. The rate is fixed and the connection count is the variable, because that is what the
// collapse follows: at the same 2 000 rps, twelve connections never go slow and two hundred always
// do. Override with VUS, RPS, DURATION, TARGET.
import http from 'k6/http';

export const options = {
    vus: Number(__ENV.VUS || 200),
    duration: __ENV.DURATION || '30s',
    rps: Number(__ENV.RPS || 2000),
    summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
    discardResponseBodies: false,
};

const target = __ENV.TARGET || '127.0.0.1:8080';

export default function () {
    http.get(`http://${target}/`);
}
