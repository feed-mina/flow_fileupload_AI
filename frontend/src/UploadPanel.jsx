import React, { useState } from 'react';
import { Alert, Button, Card } from 'antd';
import { uploadFile } from './lib/api.js';

export default function UploadPanel({ disabled }) {
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);
  async function submit(event) {
    event.preventDefault();
    if (!file || busy || disabled) return;
    setError(''); setResult(null);
    if (!file.size) { setError('0바이트 파일은 보낼 수 없습니다.'); return; }
    if (file.size > 10 * 1024 * 1024) { setError('파일은 10 MiB 이하여야 합니다.'); return; }
    setBusy(true);
    try { setResult(await uploadFile(file)); }
    catch (failure) {
      const uncertain = !failure.status || failure.status >= 500 || failure.code === 'INVALID_RESPONSE';
      const version = failure.details?.policyVersion;
      setError((uncertain ? '업로드 결과를 확인하지 못했습니다. 중복 저장될 수 있으므로 자동 재전송하지 않습니다.' : failure.message)
        + (Number.isSafeInteger(version) ? ` (적용 정책 버전 ${version})` : ''));
    } finally { setBusy(false); }
  }
  return <Card className="upload-panel" title="03 파일 업로드">
    <p>파일 1개 · 최대 10 MiB. 서버에서 파일명과 현재 차단 규칙을 검사한 뒤 저장합니다.</p>
    <form onSubmit={submit}>
      <label className="input-label" htmlFor="upload-file">업로드할 파일</label>
      <input id="upload-file" type="file" disabled={busy || disabled} onChange={(event) => { setFile(event.target.files?.[0] || null); setError(''); setResult(null); }} />
      <Button type="primary" htmlType="submit" disabled={!file || busy || disabled} loading={busy}>파일 업로드</Button>
    </form>
    {busy && <p role="status">파일을 전송하고 검사하고 있습니다.</p>}
    {error && <Alert className="upload-message" role="alert" type="error" title={error} />}
    {result && <div className="upload-message" role="status"><strong>업로드 완료</strong><p>{result.originalName} · {result.sizeBytes}바이트 · 적용 정책 버전 {result.policyVersion}</p><p className="upload-id">파일 ID: {result.id}</p></div>}
    <p className="footnote">내용·악성코드 검사는 제공하지 않습니다. 확장자가 허용되어도 안전한 파일을 의미하지 않습니다. 저장 파일의 직접 공개 주소는 제공하지 않습니다.</p>
  </Card>;
}
