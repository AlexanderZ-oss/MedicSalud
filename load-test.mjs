const baseUrl = process.env.API_URL || 'http://localhost:8080/api/v1';
const users = Number(process.env.USERS || 100);

async function request(url, options = {}) {
  const started = performance.now();
  try {
    const response = await fetch(url, options);
    return { status: response.status, duration: performance.now() - started };
  } catch (error) {
    return { status: 0, duration: performance.now() - started, error: error.message };
  }
}

const loginResponse = await fetch(`${baseUrl}/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'personal', password: 'Personal@2024', totpCode: '' }),
});
const login = await loginResponse.json();
if (!login.accessToken) {
  throw new Error(`No se pudo obtener token de prueba. HTTP ${loginResponse.status}`);
}

const authHeaders = {
  Authorization: `Bearer ${login.accessToken}`,
};
const publicResults = await Promise.all(
  Array.from({ length: users }, () => request(`${baseUrl}/health`)),
);
const authenticatedResults = await Promise.all(
  Array.from({ length: users }, () => request(`${baseUrl}/dashboard`, { headers: authHeaders })),
);

function report(name, results, expectedStatus) {
  const passed = results.filter((result) => result.status === expectedStatus).length;
  const failed = results.length - passed;
  const durations = results.map((result) => result.duration).sort((a, b) => a - b);
  const p95 = durations[Math.floor(durations.length * 0.95) - 1] ?? 0;
  const max = durations.at(-1) ?? 0;
  console.log(`${name}: total=${results.length} passed=${passed} failed=${failed} p95_ms=${p95.toFixed(1)} max_ms=${max.toFixed(1)}`);
  return failed === 0;
}

const publicOk = report('HEALTH', publicResults, 200);
const authenticatedOk = report('DASHBOARD', authenticatedResults, 200);
if (!publicOk || !authenticatedOk) process.exitCode = 1;
