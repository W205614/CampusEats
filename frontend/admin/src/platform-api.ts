import { useSession } from './stores/session';
import { ApiError } from '../../contracts/api-error';
export async function request<T>(path: string, method = 'GET', data?: unknown): Promise<T> {
  const store = useSession();
  const headers: Record<string, string> = {};
  if (store.session) headers.Authorization = 'Bearer ' + store.session.token;
  const form = data instanceof FormData;
  if (data && !form) headers['Content-Type'] = 'application/json';
  const response = await fetch('/api/v1' + path, {
    method,
    headers,
    body: data ? (form ? data : JSON.stringify(data)) : undefined,
    signal: AbortSignal.timeout(20000),
  });
  const result = await response.json();
  if (!response.ok) {
    if (response.status === 401) store.set(null);
    throw new ApiError(result.code, result.message, result.requestId, response.status);
  }
  return result.data;
}
export async function exportReport(path: string) {
  const token = useSession().session?.token;
  const r = await fetch('/api/v1' + path, { headers: { Authorization: 'Bearer ' + token } });
  if (!r.ok) throw new Error('导出失败');
  const u = URL.createObjectURL(await r.blob());
  const a = document.createElement('a');
  a.href = u;
  a.download = 'campus-report.xlsx';
  a.click();
  URL.revokeObjectURL(u);
}
