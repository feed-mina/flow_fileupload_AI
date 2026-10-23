import React, { useRef, useState } from 'react';
import { Alert, Button, Card } from 'antd';
import { getUploadStatus, uploadFile } from './lib/api.js';

const PENDING_KEY = 'flow-pending-upload';
function restoredRequest() {
  try { return sessionStorage.getItem(PENDING_KEY) || ''; } catch { return ''; }
}
function rememberRequest(id) {
  try { if (id) sessionStorage.setItem(PENDING_KEY, id); else sessionStorage.removeItem(PENDING_KEY); } catch { /* The visible ID permits manual lookup when browser storage is unavailable. */ }
}

export default function UploadPanel({ disabled, policy }) {
  const fileInput=useRef(null);
  const resetFile=()=>{setFile(null);if(fileInput.current)fileInput.current.value='';};
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);
  const [checking, setChecking] = useState(false);
  const [unconfirmedId, setUnconfirmedId] = useState(restoredRequest);
  async function submit(event) {
    event.preventDefault();
    if (!file || busy || disabled || unconfirmedId) return;
    setError(''); setResult(null);
    if (!file.size) { setError('0바이트 파일은 보낼 수 없습니다.'); return; }
    if (file.size > 10 * 1024 * 1024) { setError('파일은 10 MiB 이하여야 합니다.'); return; }
    const requestId = crypto.randomUUID();
    setUnconfirmedId(requestId); rememberRequest(requestId);
    setBusy(true);
    try {
      setResult(await uploadFile(file, requestId));
      resetFile();
      setUnconfirmedId(''); rememberRequest('');
    }
    catch (failure) {
      const uncertain = !failure.status || failure.status >= 500 || failure.code === 'INVALID_RESPONSE' || failure.code === 'UPLOAD_REQUEST_EXISTS';
      const version = failure.details?.policyVersion;
      setError((uncertain ? '업로드 결과를 확인하지 못했습니다. 중복 저장될 수 있으므로 자동 재전송하지 않습니다. 저장 결과 확인을 눌러 주세요.' : failure.message)
        + (Number.isSafeInteger(version) ? ` (적용 정책 버전 ${version})` : '')
        + (failure.details?.requestId ? ` · 오류 추적 번호: ${failure.details.requestId}` : ''));
      if (!uncertain) { setUnconfirmedId(''); rememberRequest(''); }
    } finally { setBusy(false); }
  }
  async function checkResult() {
    if (!unconfirmedId || checking || busy) return;
    setChecking(true);
    try {
      setResult(await getUploadStatus(unconfirmedId));
      resetFile();
      setError(''); setUnconfirmedId(''); rememberRequest('');
    } catch (failure) {
      setError(failure.code === 'UPLOAD_NOT_CONFIRMED'
        ? '완료된 저장 결과를 아직 찾지 못했습니다. 처리 중이거나 저장에 실패했을 수 있습니다. 잠시 후 다시 조회해 주세요. 자동 재전송하지 않습니다.'
        : '저장 결과를 조회하지 못했습니다. 연결을 확인하고 다시 조회해 주세요. 자동 재전송하지 않습니다.');
    } finally { setChecking(false); }
  }
  const suffix=file?.name.includes('.')?file.name.split('.').pop().toLowerCase():'';
  const previewBlocked=policy?.fixed.some(item=>item.enabled&&item.extension===suffix)||policy?.custom.some(item=>item.extension===suffix);
  return <Card className="upload-panel" title="03 파일 업로드">
    <p>파일 1개 · 최대 10 MiB. 서버에서 파일명과 현재 차단 규칙을 검사한 뒤 저장합니다.</p>
    <form onSubmit={submit}>
      <label className="input-label" htmlFor="upload-file">업로드할 파일</label>
      <input ref={fileInput} id="upload-file" type="file" disabled={busy || checking || disabled} onChange={(event) => { setFile(event.target.files?.[0] || null); if (!unconfirmedId) { setError(''); setResult(null); } }} />
      <Button type="primary" htmlType="submit" disabled={!file || busy || disabled || Boolean(unconfirmedId)} loading={busy}>파일 업로드</Button>
    </form>
    {file && <p className="footnote" data-testid="policy-preview">사전 안내: 정책 버전 {policy?.version}에서 {previewBlocked?'차단 대상':'확장자 차단 대상 아님'} ({suffix||'확장자 없음'}). 실제 허용 여부는 전송 시 서버가 최종 판단합니다.</p>}
    <p className="footnote">동시 업로드 4개 · 연결 주소당 변경 요청 분당 120회 기본 제한. 초과 시 60초 뒤 수동으로 다시 시도하세요.</p>
    {busy && <p role="status">파일을 전송하고 검사하고 있습니다.</p>}
    {error && <Alert className="upload-message" role="alert" type="error" title={error} />}
    {unconfirmedId && !busy && <div className="upload-message"><p>미확인 업로드 요청 번호: {unconfirmedId}</p><Button onClick={checkResult} disabled={checking} loading={checking}>저장 결과 확인</Button><p className="footnote">파일을 다시 보내지 않고 저장 결과만 조회합니다. 미확인 요청은 새로고침 후에도 유지합니다. 조회 결과가 없어도 아직 처리 중일 수 있습니다.</p><Button type="text" disabled={checking} onClick={() => { if (window.confirm('이전 업로드가 나중에 저장될 수 있습니다. 요청 번호를 보관한 뒤 새 업로드를 시작하시겠습니까?')) { setUnconfirmedId(''); rememberRequest(''); setError(''); resetFile(); } }}>미확인 요청을 닫고 새 업로드 준비</Button></div>}
    {result && <div className="upload-message" role="status"><strong>업로드 완료</strong><p>{result.originalName} · {result.sizeBytes}바이트 · 적용 정책 버전 {result.policyVersion}</p><p className="upload-id">파일 ID: {result.id}</p></div>}
    <p className="footnote">내용·악성코드 검사는 제공하지 않습니다. 확장자가 허용되어도 안전한 파일을 의미하지 않습니다. 저장 파일의 직접 공개 주소는 제공하지 않습니다.</p>
  </Card>;
}
