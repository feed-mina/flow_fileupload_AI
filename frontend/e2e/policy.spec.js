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
  await page.getByLabel('추가할 확장자').fill('sh');
  await page.getByRole('button', { name: '추가', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('이미 추가한 확장자입니다.');
  await expect(page.getByLabel('추가할 확장자')).toHaveValue('sh');
  await expect(page.getByLabel('커스텀 확장자 개수')).toHaveText('0/200');
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
