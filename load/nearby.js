// k6 run load/nearby.js   (BASE_URL defaults to http://localhost:8080)
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

// people opening the map around five busy areas
const AREAS = [
  [12.9352, 77.6245], // Koramangala
  [12.9784, 77.6408], // Indiranagar
  [12.9116, 77.6474], // HSR Layout
  [12.9250, 77.5938], // Jayanagar
  [12.9698, 77.7500], // Whitefield
];

export const options = {
  vus: 50,
  duration: '30s',
  thresholds: { http_req_failed: ['rate<0.01'] },
};

export default function () {
  const [lat, lng] = AREAS[Math.floor(Math.random() * AREAS.length)];
  const jitter = () => (Math.random() - 0.5) * 0.002; // a few hundred metres
  const res = http.get(`${BASE}/api/v1/activities/nearby?lat=${lat + jitter()}&lng=${lng + jitter()}&radiusKm=3`);
  check(res, { '200': (r) => r.status === 200 });
}
