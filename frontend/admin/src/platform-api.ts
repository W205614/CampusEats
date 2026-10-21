import { useSession } from './stores/session';
import { ApiError } from '../../contracts/api-error';
export async function request<T>(path: string, method = 'GET', data?: unknown): Promise<T> {
  const store = useSession();
  const token = store.session?.token;
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = 'Bearer ' + token;
  const form = data instanceof FormData;
  if (data && !form) headers['Content-Type'] = 'application/json';
  let response: Response;
  let result;
  try {
    response = await fetch('/api/v1' + path, {
      method,
      headers,
      body: data ? (form ? data : JSON.stringify(data)) : undefined,
      signal: AbortSignal.timeout(25000),
    });
    result = await response.json();
    if (!result || typeof result !== 'object' || typeof result.code !== 'string'
      || (response.ok && (result.code !== 'OK' || !('data' in result))))
      throw new Error('Incomplete API response');
  } catch {
    throw new ApiError('NETWORK', method.toUpperCase() === 'GET'
      ? '网络连接失败，请重试' : '操作结果尚未确认，请刷新查看', '', 0);
  }
  if (!response.ok) {
    if (response.status === 401 && token && store.session?.token === token) store.set(null);
    throw new ApiError(result.code, result.message, result.requestId, response.status);
  }
  return result.data;
}
export async function exportReport(path: string) {
  const token = useSession().session?.token;
  const r = await fetch('/api/v1' + path, {
    headers: { Authorization: 'Bearer ' + token }, signal: AbortSignal.timeout(25000),
  });
  if (!r.ok) throw new Error('导出失败');
  const u = URL.createObjectURL(await r.blob());
  const a = document.createElement('a');
  a.href = u;
  a.download = 'campus-report.xlsx';
  a.click();
  URL.revokeObjectURL(u);
}
