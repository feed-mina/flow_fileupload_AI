export class ApiError extends Error {
  constructor(message, code, status) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

async function request(path, options = {}) {
  const response = await fetch(path, { ...options, cache: 'no-store' });
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(
      body?.error?.message || '요청을 처리하지 못했습니다. 최신 규칙을 다시 확인해 주세요.',
      body?.error?.code || 'REQUEST_FAILED',
      response.status,
    );
  }
  if (!body || !Array.isArray(body.fixed) || !Array.isArray(body.custom) || !Number.isSafeInteger(body.version)) {
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
