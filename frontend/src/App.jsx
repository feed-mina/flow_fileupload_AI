import React, { useState } from 'react';
import { Alert, Button, Card, Checkbox, Input, Skeleton, Tag } from 'antd';
import { usePolicy } from './usePolicy.js';
import UploadPanel from './UploadPanel.jsx';

export default function App() {
  const { policy, loading, pending, error, notice, mustRefresh, refresh, updateFixed, createCustom, removeCustom } = usePolicy();
  const [extension, setExtension] = useState('');
  const disabled = loading || Boolean(pending) || mustRefresh;

  async function submit(event) {
    event.preventDefault();
    if (await createCustom(extension)) setExtension('');
  }

  return (
    <div className="app">
      <header className="topbar"><span className="brand">파일 업로드 정책</span><span className="subtitle">차단 규칙 관리</span></header>
      <main>
        <div className="intro"><p className="eyebrow">FILE POLICY</p><h1>보낼 수 있는 파일의<br />기준을 정하세요.</h1><p>차단할 확장자를 선택하거나 직접 추가하세요.<br />저장된 규칙은 새로고침 후에도 유지됩니다.</p></div>

        {error && <Alert role="alert" type="error" title={error} action={<Button onClick={refresh} disabled={loading || Boolean(pending)}>최신 규칙 다시 보기</Button>} />}
        <div className="live" role="status" aria-live="polite">{loading ? '규칙을 불러오고 있습니다.' : pending ? '규칙을 저장하고 있습니다.' : notice}</div>

        {!policy && loading ? <Card><Skeleton active paragraph={{ rows: 5 }} /></Card> : policy && (
          <div className="policy-grid">
            <Card className="policy-card" title={<><span className="step">01</span>고정 확장자</>}>
              <p className="help">선택한 확장자를 차단하도록 저장합니다.</p>
              <fieldset disabled={disabled} className="fixed-list"><legend className="sr-only">고정 확장자 선택</legend>
                {policy.fixed.map((item) => (
                  <div key={item.id} className={`fixed-item ${item.enabled ? 'selected' : ''}`}>
                    <Checkbox checked={item.enabled} disabled={disabled} onChange={(event) => updateFixed(item.extension, event.target.checked)}>{item.extension}</Checkbox>
                  </div>
                ))}
              </fieldset>
              <p className="footnote">처음에는 모두 미선택 상태입니다.<br />선택·해제할 때마다 저장합니다.</p>
            </Card>

            <Card className="policy-card" title={<><span className="step">02</span>커스텀 확장자</>} extra={<span aria-label="커스텀 확장자 개수">{policy.customCount}/{policy.maxCount}</span>}>
              <p className="help">목록에 없는 확장자를 직접 추가하세요.</p>
              <form onSubmit={submit}>
                <label className="input-label" htmlFor="extension">추가할 확장자</label>
                <div className="input-row"><Input id="extension" value={extension} maxLength={policy.maxLength + 1} placeholder="예: sh" autoComplete="off" disabled={disabled} onChange={(event) => setExtension(event.target.value)} /><Button htmlType="submit" type="primary" disabled={disabled || !extension.trim() || policy.customCount >= policy.maxCount} loading={pending === 'custom'}>추가</Button></div>
              </form>
              <p className="footnote">영문·숫자 최대 {policy.maxLength}자 · 최대 {policy.maxCount}개<br />앞의 점과 대소문자는 저장할 때 정리합니다.</p>
              <div className="custom-list" role="list" aria-label="커스텀 확장자 목록">
                {policy.custom.length ? policy.custom.map((item) => <span role="listitem" key={item.id}><Tag>{item.extension}</Tag><Button type="text" aria-label={`${item.extension} 삭제`} disabled={disabled} onClick={() => removeCustom(item.id)}>×</Button></span>) : <p className="empty">추가한 확장자가 없습니다.</p>}
              </div>
            </Card>
          </div>
        )}
        {policy && <UploadPanel disabled={disabled} />}
        <p className="scope-note">현재 로컬 단일 정책입니다. 공개 배포 전 권한·보관 기간·전체 저장 한도를 결정해야 합니다.</p>
      </main>
    </div>
  );
}
