import http from 'node:http';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';

// Nginx rejects this declared body length before proxying it to a backend.
// No file or user data is sent. Actual application file boundaries are tested separately.
const evidence = await new Promise((resolve, reject) => {
  const req = http.request('http://127.0.0.1:18081/api/uploads', {
    method:'POST',headers:{'Content-Type':'multipart/form-data; boundary=ux-test','Content-Length':12*1024*1024+1},
  }, response => {
    let body='';response.setEncoding('utf8');response.on('data',chunk=>body+=chunk);
    response.on('end',()=>resolve({status:response.statusCode,contentType:response.headers['content-type'],body:JSON.parse(body)}));
  });
  req.on('error',reject);req.setTimeout(10000,()=>req.destroy(new Error('proxy contract timed out')));
  req.end();
});
assert.equal(evidence.status,413);
assert.match(evidence.contentType,/application\/json/);
assert.equal(evidence.body.error.code,'UPLOAD_SIZE_LIMIT');
assert.equal(evidence.body.error.message,'파일은 10 MiB, 전체 요청은 12 MiB 이하여야 합니다.');
assert.ok(evidence.body.error.requestId);
assert.equal(/nginx|\/app\/|Exception|stack/.test(JSON.stringify(evidence.body)),false);
await mkdir('test-results',{recursive:true});
await writeFile('test-results/proxy-evidence.json',JSON.stringify(evidence,null,2));
console.log('PASS BE-U04: actual Nginx 12 MiB request limit returns sanitized JSON 413 with requestId.');
