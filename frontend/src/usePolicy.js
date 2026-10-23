import { useCallback, useEffect, useRef, useState } from 'react';
import { addCustom, getPolicy, setFixed } from './lib/api.js';

export function usePolicy() {
  const [policy, setPolicy] = useState(null);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [mustRefresh, setMustRefresh] = useState(false);
  const activeRead = useRef(null);
  const writing = useRef(false);

  const refresh = useCallback(async () => {
    activeRead.current?.abort();
    const controller = new AbortController();
    activeRead.current = controller;
    setLoading(true);
    setError('');
    setNotice('');
    try {
      const next = await getPolicy(controller.signal);
      if (!controller.signal.aborted) {
        setPolicy(next);
        setMustRefresh(false);
      }
    } catch (failure) {
      if (!controller.signal.aborted) {
        setMustRefresh(true);
        setError('규칙을 불러오지 못했습니다. 연결을 확인하고 다시 조회해 주세요.');
      }
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
    return () => activeRead.current?.abort();
  }, [refresh]);

  const mutate = async (key, action) => {
    if (!policy || loading || mustRefresh || writing.current) return false;
    writing.current = true;
    setPending(key);
    setError('');
    setNotice('');
    try {
      const next = await action(policy.version);
      setPolicy(next);
      setNotice('규칙을 저장했습니다.');
      return true;
    } catch (failure) {
      const uncertain = !failure.status || failure.status >= 500 || failure.code === 'INVALID_RESPONSE';
      const conflict = failure.status === 412 || failure.status === 428;
      setMustRefresh(uncertain || conflict);
      setError(uncertain
        ? '저장 결과를 확인하지 못했습니다. 최신 규칙을 다시 조회해 주세요.'
        : failure.message);
      return false;
    } finally {
      writing.current = false;
      setPending('');
    }
  };

  return {
    policy, loading, pending, error, notice, mustRefresh, refresh,
    updateFixed: (extension, enabled) => mutate(extension, (version) => setFixed(extension, enabled, version)),
    createCustom: (extension) => mutate('custom', (version) => addCustom(extension, version)),
  };
}
