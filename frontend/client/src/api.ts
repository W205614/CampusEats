import { useSession } from "./stores/session";
import type { Envelope } from "../../contracts/models";
import { ApiError } from "../../contracts/api-error";
export { ApiError } from "../../contracts/api-error";
export function base() {
  let origin = import.meta.env.VITE_API_BASE || "";
  // #ifndef H5
  if (!origin) origin = "http://localhost:18083";
  // #endif
  return origin.replace(/\/$/, "");
}
export const image = (path: string) =>
  path?.startsWith("/") ? base() + path : path;
export function api<T>(
  path: string,
  method: "GET" | "POST" | "PUT" | "DELETE" = "GET",
  data?: unknown,
  headers: Record<string, string> = {},
): Promise<T> {
  const store = useSession();
  return new Promise((resolve, reject) => {
    uni.request({
      url: base() + "/api/v1/user" + path,
      method,
      data: data as any,
      timeout: 15000,
      header: {
        "Content-Type": "application/json",
        ...(store.session
          ? { Authorization: "Bearer " + store.session.token }
          : {}),
        ...headers,
      },
      success(response) {
        const result = response.data as Envelope<T>;
        if (
          response.statusCode >= 200 &&
          response.statusCode < 300 &&
          result.code === "OK"
        )
          resolve(result.data);
        else {
          if (response.statusCode === 401) store.set(null);
          reject(
            new ApiError(
              result.code || "UNAVAILABLE",
              result.message || "服务暂不可用",
              result.requestId || "",
              response.statusCode,
            ),
          );
        }
      },
      fail() {
        reject(new ApiError("NETWORK", "网络连接失败，请重试", "", 0));
      },
    });
  });
}
export function toast(error: unknown) {
  uni.showToast({
    title: error instanceof Error ? error.message : "操作失败",
    icon: "none",
    duration: 2500,
  });
}
export function loggedIn() {
  if (useSession().session) return true;
  uni.navigateTo({ url: "/pages/login/index" });
  return false;
}
export const confirm = (content: string) =>
  new Promise<boolean>((resolve) =>
    uni.showModal({
      title: "请确认",
      content,
      confirmText: "确定",
      cancelText: "取消",
      success: (r) => resolve(r.confirm),
    }),
  );
export function uuid() {
  const values = new Uint8Array(16);
  // #ifdef H5
  crypto.getRandomValues(values);
  // #endif
  // #ifndef H5
  for (let i = 0; i < 16; i++) values[i] = Math.floor(Math.random() * 256);
  // #endif
  values[6] = (values[6]! & 15) | 64;
  values[8] = (values[8]! & 63) | 128;
  const h = Array.from(values, (x) => x.toString(16).padStart(2, "0")).join("");
  return (
    h.slice(0, 8) +
    "-" +
    h.slice(8, 12) +
    "-" +
    h.slice(12, 16) +
    "-" +
    h.slice(16, 20) +
    "-" +
    h.slice(20)
  );
}
