import { chromium, expect } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';

const base = process.env.PUBLIC_BASE_URL || 'https://flow-fileupload-ai.vercel.app';
if (!['https://flow-fileupload-ai.vercel.app', 'http://127.0.0.1:18090'].includes(base))
  throw new Error('Only the approved production URL or local contract fixture is allowed');
const writes = process.env.PUBLIC_WRITE_TEST === 'true';
const out = process.env.PUBLIC_EVIDENCE_DIR || '../test-results/public-deployment';
await mkdir(out, { recursive: true });
const report = { startedAt: new Date().toISOString(), base, writes,
  excluded: ['container recreation', 'backup', 'restore'],
  checks: [], uploads: [], cleanup: [], note: 'No credentials, cookies or CSRF tokens are recorded' };
const browser = await chromium.launch(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {});
const context = await browser.newContext({ baseURL: base });
const page = await context.newPage();
page.setDefaultTimeout(20000);
let policy;
let browserReady = false;
let sessionReady = false;
async function check(name, fn) {
  try { const details = await fn(); report.checks.push({ name, result: 'PASS', details }); return true; }
  catch (error) { report.checks.push({ name, result: 'FAIL', error: String(error.message).slice(0, 1200) }); return false; }
}
function blocked(name, reason) { report.checks.push({ name, result: 'BLOCKED', reason }); }
async function api(path, method = 'GET', data, version, token = true) {
  return page.evaluate(async ({path, method, data, version, token}) => {
    const headers = {};
    if (data !== undefined) headers['Content-Type'] = 'application/json';
    if (version !== undefined) headers['If-Match'] = '"' + version + '"';
    if (token && method !== 'GET') {
      const session = await fetch('/api/session', { credentials: 'include', cache: 'no-store' });
      const body = await session.json();
      if (!session.ok || typeof body.csrfToken !== 'string') throw new Error('Session unavailable: ' + session.status);
      headers['X-CSRF-TOKEN'] = body.csrfToken;
    }
    const response = await fetch(path, { method, headers, credentials: 'include', cache: 'no-store',
      ...(data === undefined ? {} : { body: JSON.stringify(data) }) });
    return { status: response.status, body: await response.json().catch(() => null) };
  }, {path, method, data, version, token});
}
async function snapshot() {
  const r = await api('/api/policy'); expect(r.status).toBe(200);
  expect(r.body.fixed).toHaveLength(7); return r.body;
}
async function upload(size, extension) {
  const requestId = randomUUID();
  report.uploads.push({ requestId, sizeBytes: size, extension, outcome: 'unconfirmed' });
  const item = report.uploads.at(-1);
  const result = await page.evaluate(async ({size, extension, requestId}) => {
    const s = await fetch('/api/session', { credentials: 'include', cache: 'no-store' });
    const session = await s.json();
    if (!s.ok || !session.csrfToken) throw new Error('Upload session unavailable: ' + s.status);
    const form = new FormData();
    form.append('file', new File([new Uint8Array(size).fill(65)], 'public-qa.' + extension, {type:'application/octet-stream'}));
    const r = await fetch('/api/uploads', {method:'POST', credentials:'include',
      headers:{'X-CSRF-TOKEN':session.csrfToken,'X-Upload-Request-Id':requestId},body:form});
    return {status:r.status,body:await r.json().catch(()=>null)};
  }, {size, extension, requestId});
  item.httpStatus = result.status;
  item.outcome = result.status === 201 ? 'READY' : result.status >= 500 ? 'unconfirmed' : 'rejected';
  if (result.body?.id) item.id = result.body.id;
  if (result.body?.error?.code) item.code = result.body.error.code;
  return { ...result, requestId };
}
try {
  const root = await check('01 public HTML and React screen', async () => {
    const response = await page.goto('/', { waitUntil: 'domcontentloaded', timeout: 60000 });
    expect(response.status()).toBe(200);
    await expect(page.getByRole('heading', {level:1})).toBeVisible();
    await expect(page.getByRole('checkbox')).toHaveCount(7);
    await page.screenshot({path:out+'/public-desktop.png', fullPage:true});
    return {httpStatus:response.status(), fixedCheckboxes:7};
  });
  browserReady = root;
  await check('02 Vercel rewrite policy JSON', async () => {
    const r = await context.request.get('/api/policy');
    expect(r.status()).toBe(200); expect(r.headers()['content-type']).toContain('application/json');
    policy = await r.json(); expect(policy.fixed).toHaveLength(7);
    return {version:policy.version,fixed:policy.fixed.length,customCount:policy.customCount};
  });
  sessionReady = await check('03 production Origin and session cookie', async () => {
    const r = await context.request.get('/api/session', { headers:{Origin:base} });
    // Spring CORS may reject Origin even before the custom Origin filter.
    expect(r.status(), 'Origin-aware session must be accepted').toBe(200);
    const s = await r.json(); expect(typeof s.csrfToken).toBe('string');
    const cookies = await context.cookies(base);
    const cookie = cookies.find(c=>c.name==='JSESSIONID');
    expect(cookie, 'Session cookie must be scoped to the frontend origin').toBeTruthy();
    expect(cookie.httpOnly).toBe(true);
    if (base.startsWith('https:')) expect(cookie.secure, 'Production session cookie must be Secure').toBe(true);
    return {authenticated:s.authenticated,loginAvailable:s.loginAvailable,httpOnly:cookie.httpOnly,secure:cookie.secure,sameSite:cookie.sameSite};
  });
  if (!writes) {
    blocked('04-10 write scenarios', 'Read-only run; set PUBLIC_WRITE_TEST=true only for an authorized test');
  } else if (!browserReady || !sessionReady || !policy) {
    blocked('04-10 write scenarios', 'Public rendering, policy JSON or Origin/session prerequisite failed');
  } else {
    await check('04 missing CSRF is rejected', async () => {
      const r = await api('/api/policy/custom','POST',{extension:'qa'+randomUUID().slice(0,8)},policy.version,false);
      expect(r.status).toBe(403);
      expect(r.body?.error?.code).toBe('CSRF_INVALID');
      return {status:r.status,code:r.body.error.code};
    });
    await check('05 fixed save, reload and restore', async () => {
      const before = await snapshot();
      const original = before.fixed.find(x=>x.extension==='exe').enabled;
      let ownedVersion;
      try {
        const response = page.waitForResponse(r=>r.url().endsWith('/api/policy/fixed/exe') && r.request().method()==='PUT');
        await page.getByRole('checkbox',{name:'exe',exact:true}).click();
        const r = await response; const body = await r.json();
        expect(r.status()).toBe(200); ownedVersion=body.version;
        expect(body.fixed.find(x=>x.extension==='exe').enabled).toBe(!original);
        await page.reload();
        await expect(page.getByRole('checkbox',{name:'exe',exact:true})).toBeChecked({checked:!original});
      } finally {
        if (ownedVersion !== undefined) {
          const current = await snapshot();
          // Do not overwrite another visitor's subsequent edit.
          if (current.version !== ownedVersion) {
            report.cleanup.push({type:'fixed',result:'MANUAL_REVIEW',extension:'exe',original});
            throw new Error('Concurrent edit detected; fixed policy was not overwritten');
          }
          const restore = await api('/api/policy/fixed/exe','PUT',{enabled:original},ownedVersion);
          expect(restore.status).toBe(200);
          report.cleanup.push({type:'fixed',result:'RESTORED'});
          await page.reload();
          await expect(page.getByRole('checkbox',{name:'exe',exact:true})).toBeChecked({checked:original});
        }
      }
      return {savedAndReloaded:true,restored:true};
    });
    const suffix='qa'+randomUUID().replaceAll('-','').slice(0,12);
    await check('06 custom add, blocked upload and delete', async () => {
      const before = await snapshot();
      expect(before.customCount).toBeLessThan(200);
      let ownId;
      try {
        await page.reload();
        await page.getByLabel('추가할 확장자').fill(suffix);
        const response=page.waitForResponse(r=>r.url().endsWith('/api/policy/custom') && r.request().method()==='POST');
        await page.getByRole('button',{name:'추가',exact:true}).click();
        const added=await response; const body=await added.json(); expect(added.status()).toBe(201);
        ownId=body.custom.find(x=>x.extension===suffix)?.id; expect(ownId).toBeTruthy();
        await page.reload();
        await expect(page.getByRole('button',{name:suffix+' 삭제',exact:true})).toBeVisible();
        const blockedFile=await upload(64,suffix);
        expect(blockedFile.status).toBe(422); expect(blockedFile.body.error.code).toBe('EXTENSION_BLOCKED');
        await page.getByRole('button',{name:suffix+' 삭제',exact:true}).click();
        await expect(page.getByRole('button',{name:suffix+' 삭제',exact:true})).toHaveCount(0);
      } finally {
        const current=await snapshot();
        const own=current.custom.find(x=>x.extension===suffix);
        if (own) {
          // Handle an uncertain POST response using the unique fixture suffix, never delete others.
          if (ownId !== undefined) expect(own.id).toBe(ownId);
          const removed=await api('/api/policy/custom/'+own.id,'DELETE',undefined,current.version);
          expect(removed.status).toBe(200);
        }
        expect((await snapshot()).custom.some(x=>x.extension===suffix)).toBe(false);
        report.cleanup.push({type:'custom',result:'ABSENT',extension:suffix});
      }
      return {added:true,blockedStatus:422,deleted:true};
    });
    const allowed='qu'+randomUUID().replaceAll('-','').slice(0,12);
    await check('07 GUI allowed upload and status lookup', async () => {
      await page.reload();
      const pending=page.waitForResponse(r=>r.url().endsWith('/api/uploads') && r.request().method()==='POST');
      await page.getByLabel('업로드할 파일').setInputFiles({name:'public-qa.'+allowed,mimeType:'text/plain',buffer:Buffer.from('harmless public smoke fixture')});
      await page.getByRole('button',{name:'파일 업로드',exact:true}).click();
      const response=await pending; const body=await response.json();
      report.uploads.push({requestId:body.requestId,id:body.id,sizeBytes:body.sizeBytes,outcome:response.status()===201?'READY':'unconfirmed'});
      expect(response.status()).toBe(201); expect(body.state).toBe('READY');
      await expect(page.getByRole('status').filter({hasText:'업로드 완료'})).toBeVisible();
      const found=await api('/api/uploads/status/'+body.requestId);
      expect(found.status).toBe(200); expect(found.body.id).toBe(body.id);
      return {status:201,statusLookup:200};
    });
    for (const [name,size,status] of [['08 below limit',10*1024*1024-1,201],['09 exact limit',10*1024*1024,201],['10 over limit',10*1024*1024+1,413]]) {
      await check(name, async () => {
        const r=await upload(size,allowed); expect(r.status).toBe(status);
        if(status===201) {
          expect(r.body.sizeBytes).toBe(size); expect(r.body.state).toBe('READY');
          const found=await api('/api/uploads/status/'+r.requestId);
          expect(found.status).toBe(200); expect(found.body.id).toBe(r.body.id);
        }
        return {sizeBytes:size,status:r.status,code:r.body?.error?.code};
      });
    }
    await check('11 GUI oversized file message', async () => {
      await page.reload();
      await page.getByLabel('업로드할 파일').setInputFiles({name:'public-qa.'+allowed,mimeType:'text/plain',buffer:Buffer.alloc(10*1024*1024+1,65)});
      await page.getByRole('button',{name:'파일 업로드',exact:true}).click();
      await expect(page.getByRole('alert')).toContainText('10 MiB');
      return {clientSizeMessage:true};
    });
  }
} finally {
  await page.screenshot({path:out+'/public-final.png',fullPage:true}).catch(()=>{});
  report.finishedAt = new Date().toISOString();
  report.ok = report.checks.every(c=>c.result==='PASS') && (writes || report.checks.filter(c=>c.result==='FAIL').length===0);
  await writeFile(out+'/results.json', JSON.stringify(report,null,2));
  console.log('PUBLIC_DEPLOYMENT_RESULT '+JSON.stringify(report));
  await browser.close();
}
if (report.checks.some(c=>c.result==='FAIL') || (writes && report.checks.some(c=>c.result==='BLOCKED'))) process.exitCode=1;
