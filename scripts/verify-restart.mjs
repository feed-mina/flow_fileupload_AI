import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

// This executable is intentionally restricted to a disposable CI database.
if (!/^jdbc:postgresql:\/\/(localhost|127\.0\.0\.1):\d+\/flow_smoke$/.test(process.env.DB_URL || '')) {
  throw new Error('DB_URL must point to the disposable localhost flow_smoke database.');
}
const port = 8082;
const base = `http://127.0.0.1:${port}/api/policy`;
const evidence = [];
let child;
let output = '';
let phase = 0;
await mkdir('test-results', { recursive: true });

async function start() {
  phase += 1;
  output = '';
  child = spawn('java', ['-jar', resolve('backend/target/flow-policy.jar'), `--server.port=${port}`], { env: process.env, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.on('data', data => { output += data; });
  child.stderr.on('data', data => { output += data; });
  let startupError;
  child.on('error', error => { startupError = error; });
  const deadline = Date.now() + 90000;
  while (Date.now() < deadline) {
    if (startupError) throw startupError;
    if (child.exitCode !== null) throw new Error(`Java exited: ${output}`);
    try {
      const response = await fetch(base, { signal: AbortSignal.timeout(2000) });
      if (response.ok) return;
    } catch { /* The process has not opened its HTTP port yet. */ }
    await new Promise(resolve => setTimeout(resolve, 300));
  }
  throw new Error(`Startup timed out: ${output}`);
}
async function stop() {
  if (!child) return;
  if (child.exitCode === null) {
    const exited = once(child, 'exit');
    child.kill('SIGTERM');
    let timer;
    const timeout = new Promise(resolve => { timer = setTimeout(() => resolve('timeout'), 15000); });
    if (await Promise.race([exited, timeout]) === 'timeout') {
      child.kill('SIGKILL');
      await exited;
    }
    clearTimeout(timer);
  }
  await writeFile(`test-results/restart-phase-${phase}.log`, output);
  child = null;
}
async function get() {
  const response = await fetch(base, { signal: AbortSignal.timeout(5000) });
  assert.equal(response.status, 200);
  return response.json();
}
async function write(path, method, body, version, expected = 200) {
  const security = await fetch(base.replace('/api/policy','/api/session'));
  const session = await security.json();
  const cookie = security.headers.get('set-cookie').split(';',1)[0];
  const response = await fetch(base + path, { method, headers: { 'Cookie': cookie, 'X-CSRF-TOKEN': session.csrfToken, 'Content-Type': 'application/json', 'If-Match': `"${version}"` }, body: JSON.stringify(body), signal: AbortSignal.timeout(5000) });
  assert.equal(response.status, expected, await response.clone().text());
  return response.json();
}
const enabled = (policy, name) => policy.fixed.find(item => item.extension === name).enabled;
function pass(id, detail) { evidence.push({ id, result: 'PASS', detail }); console.log(`PASS ${id}: ${detail}`); }

try {
  await start();
  let policy = await get();
  assert.deepEqual(policy.fixed.map(item => item.extension), ['bat', 'cmd', 'com', 'cpl', 'exe', 'scr', 'js']);
  assert.ok(policy.fixed.every(item => item.enabled === false));
  assert.equal(policy.customCount, 0);
  pass('T01', 'Fresh PostgreSQL: seven fixed extensions are unchecked; no custom extensions.');
  policy = await write('/fixed/exe', 'PUT', { enabled: true }, policy.version);
  policy = await get();
  assert.equal(enabled(policy, 'exe'), true);
  pass('T02', 'PUT exe=true survives a new GET.');
  policy = await write('/fixed/exe', 'PUT', { enabled: false }, policy.version);
  policy = await write('/fixed/scr', 'PUT', { enabled: true }, policy.version);
  const before = policy;
  await stop();
  await start();
  policy = await get();
  assert.equal(enabled(policy, 'exe'), false);
  assert.equal(enabled(policy, 'scr'), true);
  assert.equal(policy.version, before.version);
  assert.deepEqual(policy.fixed.map(item => item.id), before.fixed.map(item => item.id));
  pass('T03', 'A second Java process retains exe=false, scr=true, policy version and row IDs in the same PostgreSQL.');
  policy = await write('/custom', 'POST', { extension: 'sh' }, policy.version, 201);
  policy = await get();
  assert.deepEqual(policy.custom.map(item => item.extension), ['sh']);
  assert.equal(policy.customCount, 1);
  pass('T04', 'POST sh survives a new GET with customCount=1.');
} finally {
  await stop();
  await writeFile('test-results/restart-evidence.json', JSON.stringify({ at: new Date().toISOString(), evidence }, null, 2));
}
