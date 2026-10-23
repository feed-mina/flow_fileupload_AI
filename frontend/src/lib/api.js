export class ApiError extends Error {
  constructor(message, code, status, details) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.details = details;
  }
}

async function request(path, options = {}, valid = (body) => body && Array.isArray(body.fixed) && Array.isArray(body.custom) && Number.isSafeInteger(body.version)) {
  const controller = new AbortController();
  const abort = () => controller.abort();
  options.signal?.addEventListener('abort', abort, { once: true });
  if (options.signal?.aborted) controller.abort();
  const timeout = setTimeout(abort, 15000);
  try {
    const response = await fetch(path, { ...options, signal: controller.signal, cache: 'no-store' });
    const body = await response.json().catch(() => null);
    if (!response.ok) {
      const sizeLimit = response.status === 413;
      throw new ApiError(
        sizeLimit ? '파일은 10 MiB, 전체 요청은 12 MiB 이하여야 합니다.' : body?.error?.message || '요청을 처리하지 못했습니다. 최신 규칙을 다시 확인해 주세요.',
        sizeLimit ? 'UPLOAD_SIZE_LIMIT' : body?.error?.code || 'REQUEST_FAILED',
        response.status,
        body?.error,
      );
    }
    if (!valid(body)) {
      throw new ApiError('규칙 응답을 읽지 못했습니다. 다시 조회해 주세요.', 'INVALID_RESPONSE', response.status);
    }
    return body;
  } finally {
    clearTimeout(timeout);
    options.signal?.removeEventListener('abort', abort);
  }
}

export const getPolicy = (signal) => request('/api/policy', { signal });

export const setFixed = (extension, enabled, version) => request(`/api/policy/fixed/${encodeURIComponent(extension)}`, {
  method: 'PUT',
  headers: { 'Content-Type': 'application/json', 'If-Match': `"${version}"` },
  body: JSON.stringify({ enabled }),
});

export const addCustom = (extension, version) => request('/api/policy/custom', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'If-Match': `"${version}"` },
  body: JSON.stringify({ extension }),
});

export const deleteCustom = (id, version) => request(`/api/policy/custom/${encodeURIComponent(id)}`, {
  method: 'DELETE', headers: { 'If-Match': `"${version}"` },
});

const readyUpload = (value, requestId) => value?.state === 'READY' && typeof value.id === 'string'
  && typeof value.originalName === 'string' && typeof value.extension === 'string'
  && Number.isSafeInteger(value.sizeBytes) && value.sizeBytes > 0
  && Number.isSafeInteger(value.policyVersion) && value.requestId === requestId;

export const getUploadStatus = (requestId) => request(`/api/uploads/status/${encodeURIComponent(requestId)}`, {},
  (value) => readyUpload(value, requestId));

export const uploadFile = (file, requestId = crypto.randomUUID()) => {
  const body = new FormData();
  body.append('file', file);
  return request('/api/uploads', { method: 'POST', body, headers: { 'X-Upload-Request-Id': requestId } },
    (value) => readyUpload(value, requestId));
};
