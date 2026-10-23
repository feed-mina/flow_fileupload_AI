import { afterEach, mock, test } from 'node:test';
import assert from 'node:assert/strict';
import { ApiError, addCustom, getPolicy, setFixed } from './api.js';

const policy = { version: 4, fixed: [], custom: [], customCount: 0, maxCount: 200, maxLength: 20 };
afterEach(() => mock.restoreAll());

test('고정 저장은 원하는 활성값과 조회한 버전을 전송한다', async () => {
  let sent;
  mock.method(globalThis, 'fetch', async (path, options) => {
    sent = { path, options };
    return new Response(JSON.stringify(policy), { status: 200 });
  });
  assert.deepEqual(await setFixed('exe', false, 3), policy);
  assert.equal(sent.path, '/api/policy/fixed/exe');
  assert.equal(sent.options.headers['If-Match'], '"3"');
  assert.deepEqual(JSON.parse(sent.options.body), { enabled: false });
});

test('4xx JSON을 성공 응답으로 처리하지 않는다', async () => {
  mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({ error: { code: 'DUPLICATE_EXTENSION', message: '이미 추가한 확장자입니다.' } }), { status: 409 }));
  await assert.rejects(addCustom('sh', 4), (error) => error instanceof ApiError && error.status === 409 && error.code === 'DUPLICATE_EXTENSION');
});

test('프록시 HTML 오류도 사용자에게 보여줄 실패로 바꾼다', async () => {
  mock.method(globalThis, 'fetch', async () => new Response('<html>Unavailable</html>', { status: 503 }));
  await assert.rejects(getPolicy(), (error) => error instanceof ApiError && error.status === 503);
});

test('200이라도 정책 구조가 없으면 성공으로 표시하지 않는다', async () => {
  mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({ ok: true }), { status: 200 }));
  await assert.rejects(getPolicy(), (error) => error.code === 'INVALID_RESPONSE');
});
