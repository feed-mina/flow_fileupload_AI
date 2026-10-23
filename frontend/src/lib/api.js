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
  const response = await fetch(path, { ...options, cache: 'no-store' });
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(
      body?.error?.message || '요청을 처리하지 못했습니다. 최신 규칙을 다시 확인해 주세요.',
      body?.error?.code || 'REQUEST_FAILED',
      response.status,
      body?.error,
    );
  }
  if (!valid(body)) {
    throw new ApiError('규칙 응답을 읽지 못했습니다. 다시 조회해 주세요.', 'INVALID_RESPONSE', response.status);
  }
  return body;
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

export const uploadFile = (file) => {
  const body = new FormData();
  body.append('file', file);
  return request('/api/uploads', { method: 'POST', body },
    (value) => value?.state === 'READY' && typeof value.id === 'string'
      && typeof value.originalName === 'string' && typeof value.extension === 'string'
      && Number.isSafeInteger(value.sizeBytes) && value.sizeBytes > 0 && Number.isSafeInteger(value.policyVersion));
};
