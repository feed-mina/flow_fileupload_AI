import { test, expect } from '@playwright/test';

const initial = {
  version: 0,
  fixed: ['bat', 'cmd', 'com', 'cpl', 'exe', 'scr', 'js'].map((extension, index) => ({ id: index + 1, extension, enabled: false })),
  custom: [], customCount: 0, maxCount: 200, maxLength: 20,
};

test.beforeEach(async ({ page }) => {
  page.on('response', async (response) => {
    if (response.url().includes('/api/') && response.status() >= 400) {
      console.log(`API failure ${response.status()} ${new URL(response.url()).pathname}: ${await response.text().catch(() => 'unreadable')}`);
    }
  });
});

test('T01 → T02 → T04: 실제 서버와 DB로 초기 상태·선택 저장·커스텀 추가 확인', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('checkbox')).toHaveCount(7);
  for (const extension of ['bat', 'cmd', 'com', 'cpl', 'exe', 'scr', 'js']) {
    await expect(page.getByRole('checkbox', { name: extension, exact: true })).not.toBeChecked();
  }
  await page.getByRole('checkbox', { name: 'exe', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('규칙을 저장했습니다.');
  await page.reload();
  await expect(page.getByRole('checkbox', { name: 'exe', exact: true })).toBeChecked();
  await page.getByRole('checkbox', { name: 'exe', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('규칙을 저장했습니다.');
  await page.getByLabel('추가할 확장자').fill('sh');
  await page.getByRole('button', { name: '추가', exact: true }).click();
  await expect(page.getByRole('list', { name: '커스텀 확장자 목록' }).getByRole('listitem')).toContainText(['sh']);
  await expect(page.getByLabel('커스텀 확장자 개수')).toHaveText('1/200');
  await page.reload();
  await expect(page.getByRole('list', { name: '커스텀 확장자 목록' }).getByRole('listitem')).toContainText(['sh']);
  await expect(page.getByRole('checkbox', { name: 'exe', exact: true })).not.toBeChecked();
});

test('저장 실패 시 선택을 확정하지 않고 재조회 전 편집을 막는다', async ({ page }) => {
  await page.route('**/api/policy', (route) => route.fulfill({ json: initial }));
  await page.route('**/api/policy/fixed/exe', (route) => route.fulfill({ status: 503, json: { error: { code: 'POLICY_STORAGE_UNAVAILABLE', message: '연결 실패' } } }));
  await page.goto('/');
  const exe = page.getByRole('checkbox', { name: 'exe', exact: true });
  await exe.click();
  await expect(page.getByRole('alert')).toContainText('저장 결과를 확인하지 못했습니다.');
  await expect(exe).not.toBeChecked();
  await expect(exe).toBeDisabled();
  await page.getByRole('button', { name: '최신 규칙 다시 보기' }).click();
  await expect(exe).toBeEnabled();
});

test('커스텀 추가 실패 시 사용자가 쓴 입력을 보존한다', async ({ page }) => {
  await page.route('**/api/policy', (route) => route.fulfill({ json: initial }));
  await page.route('**/api/policy/custom', (route) => route.fulfill({ status: 409, json: { error: { code: 'DUPLICATE_EXTENSION', message: '이미 추가한 확장자입니다.' } } }));
  await page.goto('/');
  const raw = '.' + 'a'.repeat(20);
  await page.getByLabel('추가할 확장자').fill(raw);
  await page.getByRole('button', { name: '추가', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('이미 추가한 확장자입니다.');
  await expect(page.getByLabel('추가할 확장자')).toHaveValue(raw);
  await expect(page.getByTestId('custom-field-error')).toContainText('이미 추가한 확장자입니다.');
  await expect(page.getByLabel('커스텀 확장자 개수')).toHaveText('0/200');
});

test('처음 조회한 true를 그대로 표시하고 로딩 중에는 편집하지 못한다', async ({page}) => {
  let release;
  const pending=new Promise(resolve=>{release=resolve;});
  let reads=0;
  await page.route('**/api/policy',async route=>{reads++;await pending;await route.fulfill({json:{...initial,fixed:initial.fixed.map(x=>x.extension==='exe'?{...x,enabled:true}:x)}});});
  await page.goto('/');
  await expect.poll(()=>reads).toBe(1);
  await expect(page.getByRole('checkbox')).toHaveCount(0);
  await expect(page.getByRole('status')).toContainText('불러오고');
  release();
  await expect(page.getByRole('checkbox',{name:'exe',exact:true})).toBeChecked();
});

test('저장 중 빠른 재클릭은 쓰기 요청을 추가하지 않는다', async ({page}) => {
  let release;const pending=new Promise(resolve=>{release=resolve;});let writes=0;
  await page.route('**/api/policy',route=>route.fulfill({json:initial}));
  await page.route('**/api/policy/fixed/exe',async route=>{writes++;await pending;await route.fulfill({json:{...initial,version:1,fixed:initial.fixed.map(x=>x.extension==='exe'?{...x,enabled:true}:x)}});});
  await page.goto('/');const exe=page.getByRole('checkbox',{name:'exe',exact:true});
  await exe.click();await expect(exe).toBeDisabled();
  await expect.poll(()=>writes).toBe(1);await exe.dispatchEvent('click');
  expect(writes).toBe(1);release();
  await expect(exe).toBeChecked();await expect(exe).toBeEnabled();expect(writes).toBe(1);
});

test('삭제 실패 시 항목과 개수를 유지하고 재조회로 상태를 확인한다', async ({page}) => {
  const state={...initial,custom:[{id:8,extension:'sh',enabled:true}],customCount:1};
  await page.route('**/api/policy',route=>route.fulfill({json:state}));
  await page.route('**/api/policy/custom/8',route=>route.fulfill({status:503,json:{error:{code:'POLICY_STORAGE_UNAVAILABLE',message:'실패'}}}));
  await page.goto('/');await page.getByRole('button',{name:'sh 삭제',exact:true}).click();
  await expect(page.getByRole('button',{name:'sh 삭제',exact:true})).toBeDisabled();
  await expect(page.getByRole('listitem')).toContainText(['sh']);
  await expect(page.getByLabel('커스텀 확장자 개수')).toHaveText('1/200');
  await page.getByRole('button',{name:'최신 규칙 다시 보기'}).click();
  await expect(page.getByRole('button',{name:'sh 삭제',exact:true})).toBeEnabled();
});

test('최초 조회 실패를 빈 목록으로 표시하지 않고 재조회 후 빈 상태를 보여준다', async ({page}) => {
  let fail=true;
  await page.route('**/api/policy',route=>fail?route.fulfill({status:503,json:{error:{code:'UNAVAILABLE'}}}):route.fulfill({json:initial}));
  await page.goto('/');
  await expect(page.getByText('규칙을 조회하지 못했습니다. 저장된 목록이 비어 있다는 뜻은 아닙니다.')).toBeVisible();
  await expect(page.getByText('추가한 확장자가 없습니다.')).toHaveCount(0);
  fail=false;await page.getByRole('button',{name:'규칙 다시 조회',exact:true}).click();
  await expect(page.getByText('추가한 확장자가 없습니다.')).toBeVisible();
});

test('실제 저장 후 응답이 유실되어도 재전송 없이 새로고침 뒤 GET으로 확인한다', async ({page}) => {
  let posts=0;let saved;
  await page.route('**/api/uploads',async route=>{posts++;const response=await route.fetch();expect(response.status()).toBe(201);saved=await response.json();await route.abort('failed');});
  await page.goto('/');
  await page.getByLabel('업로드할 파일').setInputFiles({name:'lost-response.txt',mimeType:'text/plain',buffer:Buffer.from('response lost fixture')});
  await page.getByRole('button',{name:'파일 업로드',exact:true}).click();
  await expect(page.getByText(/미확인 업로드 요청 번호:/)).toBeVisible();
  expect(posts).toBe(1);expect(saved.state).toBe('READY');
  await page.reload();await page.getByRole('button',{name:'저장 결과 확인',exact:true}).click();
  await expect(page.getByText('업로드 완료',{exact:true})).toBeVisible();
  await expect(page.getByText(`파일 ID: ${saved.id}`,{exact:true})).toBeVisible();expect(posts).toBe(1);
});

test('업로드 결과 조회의 404와 장애를 실패 확정이나 자동 POST로 바꾸지 않는다', async ({page}) => {
  const requestId='22222222-2222-4222-8222-222222222222';let posts=0;let status=404;
  await page.addInitScript(id=>sessionStorage.setItem('flow-pending-upload',id),requestId);
  await page.route('**/api/policy',route=>route.fulfill({json:initial}));
  await page.route('**/api/uploads',route=>{posts++;return route.abort();});
  await page.route('**/api/uploads/status/*',route=>route.fulfill({status,json:{error:{code:status===404?'UPLOAD_NOT_CONFIRMED':'UNAVAILABLE'}}}));
  await page.goto('/');await page.getByRole('button',{name:'저장 결과 확인',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('처리 중이거나');
  await expect(page.getByRole('button',{name:'파일 업로드',exact:true})).toBeDisabled();
  status=503;await page.getByRole('button',{name:'저장 결과 확인',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('저장 결과를 조회하지 못했습니다.');expect(posts).toBe(0);
});

test('좁은 화면에서 키보드로 고정 선택을 저장할 수 있다', async ({ page }) => {
  let state = structuredClone(initial);
  await page.route('**/api/policy', (route) => route.fulfill({ json: state }));
  await page.route('**/api/policy/fixed/exe', async (route) => {
    state = { ...state, version: 1, fixed: state.fixed.map((item) => item.extension === 'exe' ? { ...item, enabled: true } : item) };
    await route.fulfill({ json: state });
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await page.getByRole('checkbox', { name: 'exe', exact: true }).focus();
  await page.keyboard.press('Space');
  await expect(page.getByRole('checkbox', { name: 'exe', exact: true })).toBeChecked();
  await expect(page.getByRole('status')).toHaveText('규칙을 저장했습니다.');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('DEC 업로드: 선택된 EXE 차단 → 해제 후 저장 → 커스텀 삭제 유지', async ({ page }) => {
  await page.goto('/');
  const exe = page.getByRole('checkbox', { name: 'exe', exact: true });
  await expect(exe).not.toBeChecked();
  await exe.click();
  await expect(exe).toBeChecked();
  await expect(exe).toBeEnabled();
  await page.getByLabel('업로드할 파일').setInputFiles({ name:'sample.EXE', mimeType:'image/jpeg', buffer:Buffer.from('MZ test fixture') });
  await page.getByRole('button',{name:'파일 업로드',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('차단한 확장자');
  await expect(page.getByText('업로드 완료',{exact:true})).toHaveCount(0);
  await exe.click();
  await expect(exe).not.toBeChecked();
  await expect(exe).toBeEnabled();
  await page.getByRole('button',{name:'파일 업로드',exact:true}).click();
  await expect(page.getByText('업로드 완료',{exact:true})).toBeVisible();
  await expect(page.getByText(/파일 ID:/)).toBeVisible();
  await page.getByRole('button',{name:'sh 삭제',exact:true}).click();
  await expect(page.getByLabel('커스텀 확장자 개수')).toHaveText('0/200');
  await page.reload();
  await expect(page.getByRole('list',{name:'커스텀 확장자 목록'}).getByRole('listitem')).toHaveCount(0);
});
