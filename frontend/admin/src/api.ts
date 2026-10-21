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
  const token = store.session?.token;
  const headers = new Headers(options.headers);
  if (token) headers.set("Authorization", "Bearer " + token);
  if (options.body && !(options.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  let response: Response;
  let result: Envelope<T>;
  try {
    response = await fetch("/api/v1/admin" + path, {
      ...options,
      headers,
      signal: AbortSignal.timeout(25000),
    });
    result = (await response.json()) as Envelope<T>;
    if (!result || typeof result !== "object" || typeof result.code !== "string"
      || (response.ok && (result.code !== "OK" || !("data" in result))))
      throw new Error("Incomplete API response");
  } catch {
    throw new ApiError(
      "NETWORK",
      !options.method || options.method.toUpperCase() === "GET"
        ? "网络连接失败，请重试" : "操作结果尚未确认，请刷新查看",
      "", 0,
    );
  }
  if (!response.ok) {
    if (response.status === 401 && token && store.session?.token === token) store.set(null);
    if (result.code === "PASSWORD_CHANGE_REQUIRED" && token && store.session?.token === token)
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
    signal: AbortSignal.timeout(25000),
  });
  if (!response.ok) throw new Error("导出失败");
  const url = URL.createObjectURL(await response.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = "campus-report.xlsx";
  a.click();
  URL.revokeObjectURL(url);
}
