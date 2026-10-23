import { afterEach, mock, test } from 'node:test';
import assert from 'node:assert/strict';
import { ApiError, addCustom, deleteCustom, getPolicy, setFixed, uploadFile, getUploadStatus } from './api.js';

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

test('JSON이 아닌 프록시 413도 같은 크기 초과 안내로 변환한다', async () => {
  mock.method(globalThis,'fetch',async()=>new Response('<html>large</html>',{status:413}));
  await assert.rejects(getPolicy(),error=>error.status===413 && error.code==='UPLOAD_SIZE_LIMIT' && error.message==='파일은 10 MiB, 전체 요청은 12 MiB 이하여야 합니다.');
});

test('200이라도 정책 구조가 없으면 성공으로 표시하지 않는다', async () => {
  mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({ ok: true }), { status: 200 }));
  await assert.rejects(getPolicy(), (error) => error.code === 'INVALID_RESPONSE');
});

test('삭제는 ID와 최신 버전을 전송한다', async () => {
  mock.method(globalThis,'fetch',async (path,options) => {
    assert.equal(path,'/api/policy/custom/8');
    assert.equal(options.method,'DELETE');
    assert.equal(options.headers['If-Match'],'"4"');
    return new Response(JSON.stringify(policy));
  });
  await deleteCustom(8,4);
});

test('업로드는 multipart 경계를 브라우저에 맡기고 READY만 성공으로 받는다', async () => {
  const file = new File(['safe fixture'],'sample.txt');
  mock.method(globalThis,'fetch',async (path,options) => {
    assert.equal(path,'/api/uploads');
    assert.equal(options.headers['Content-Type'],undefined);
    assert.match(options.headers['X-Upload-Request-Id'], /^[0-9a-f-]{36}$/);
    assert.equal(options.body.get('file').name,'sample.txt');
    return new Response(JSON.stringify({id:'test-id',originalName:'sample.txt',extension:'txt',sizeBytes:12,policyVersion:4,state:'PENDING'}),{status:201});
  });
  await assert.rejects(uploadFile(file),error => error.code==='INVALID_RESPONSE');
});

test('업로드 응답 유실 후 조회는 GET만 보내고 원래 요청 번호를 확인한다', async () => {
  const requestId = crypto.randomUUID();
  const result = {id:'file-id',requestId,originalName:'sample.txt',extension:'txt',sizeBytes:12,policyVersion:4,state:'READY'};
  mock.method(globalThis,'fetch',async (path,options) => {
    assert.equal(path,`/api/uploads/status/${requestId}`);
    assert.equal(options.method,undefined);
    return new Response(JSON.stringify(result));
  });
  assert.deepEqual(await getUploadStatus(requestId), result);
});

test('다른 요청 번호의 업로드 결과와 미확인 404를 성공으로 처리하지 않는다', async () => {
  const requestId = crypto.randomUUID();
  mock.method(globalThis,'fetch',async () => new Response(JSON.stringify({id:'x',requestId:crypto.randomUUID(),originalName:'a.txt',extension:'txt',sizeBytes:1,policyVersion:0,state:'READY'})));
  await assert.rejects(getUploadStatus(requestId),error => error.code==='INVALID_RESPONSE');
  mock.restoreAll();
  mock.method(globalThis,'fetch',async () => new Response(JSON.stringify({error:{code:'UPLOAD_NOT_CONFIRMED',message:'아직 확인되지 않음'}}),{status:404}));
  await assert.rejects(getUploadStatus(requestId),error => error.status===404 && error.code==='UPLOAD_NOT_CONFIRMED');
});

test('느린 요청은 제한 시간 뒤 중단하며 쓰기를 재전송하지 않는다', async () => {
  let requests = 0;
  mock.method(globalThis,'setTimeout',fn => { queueMicrotask(fn); return 1; });
  mock.method(globalThis,'clearTimeout',() => {});
  mock.method(globalThis,'fetch',async (path,options) => {
    requests++;
    return new Promise((resolve,reject) => options.signal.addEventListener('abort',()=>reject(new DOMException('timeout','AbortError')),{once:true}));
  });
  await assert.rejects(addCustom('sh',4),error=>error.name==='AbortError');
  assert.equal(requests,1);
});
