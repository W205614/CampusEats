import { useSession } from "./stores/session";
import { ElMessage } from "element-plus";
import type { Envelope } from "../../contracts/models";
import { ApiError } from "../../contracts/api-error";
export { ApiError } from "../../contracts/api-error";
export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const store = useSession();
  const headers = new Headers(options.headers);
  if (store.session)
    headers.set("Authorization", "Bearer " + store.session.token);
  if (options.body && !(options.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  let response: Response;
  try {
    response = await fetch("/api/v1/admin" + path, {
      ...options,
      headers,
      signal: AbortSignal.timeout(15000),
    });
  } catch {
    throw new ApiError("NETWORK", "网络连接失败，请重试", "", 0);
  }
  const result = (await response.json()) as Envelope<T>;
  if (!response.ok) {
    if (response.status === 401) store.set(null);
    if (result.code === "PASSWORD_CHANGE_REQUIRED")
      window.location.href = "/admin/password";
    throw new ApiError(
      result.code,
      result.message,
      result.requestId,
      response.status,
    );
  }
  return result.data;
}
export const body = (value: unknown) => JSON.stringify(value);
export function notifyError(error: unknown) {
  ElMessage.error(error instanceof Error ? error.message : "操作失败");
}
export async function download(path: string) {
  const store = useSession();
  const response = await fetch("/api/v1/admin" + path, {
    headers: { Authorization: "Bearer " + store.session?.token },
    signal: AbortSignal.timeout(15000),
  });
  if (!response.ok) throw new Error("导出失败");
  const url = URL.createObjectURL(await response.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = "campus-report.xlsx";
  a.click();
  URL.revokeObjectURL(url);
}
