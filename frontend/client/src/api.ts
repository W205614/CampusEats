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
export function image(path: string) {
  if (!path?.startsWith("/")) return path;
  const url = base() + path;
  // uni-h5 prefixes root-relative image sources with its /app/ router base.
  // Server uploads and demo images belong to the API origin instead.
  // #ifdef H5
  return new URL(url, window.location.origin).href;
  // #endif
  // #ifndef H5
  return url;
  // #endif
}
export function api<T>(
  path: string,
  method: "GET" | "POST" | "PUT" | "DELETE" = "GET",
  data?: unknown,
  headers: Record<string, string> = {},
): Promise<T> {
  const store = useSession();
  const pathname = path.split("?", 1)[0] || "";
  // Keep this list aligned with the server's anonymous GET routes. Private reads
  // and every mutation must surface their original result without replaying it.
  const publicRead =
    method === "GET" &&
    (/^\/(campuses|stores|search)$/.test(pathname) ||
      /^\/campuses\/[^/]+\/buildings$/.test(pathname) ||
      /^\/stores\/[^/]+\/(products|categories|reviews)$/.test(pathname));
  const login = /^\/auth\/(demo|wechat)$/.test(pathname);
  const token = login ? undefined : store.session?.token;
  return new Promise((resolve, reject) => {
    function request(anonymous = false) {
      const requestHeaders: Record<string, string> = {
        "Content-Type": "application/json",
        ...(token ? { Authorization: "Bearer " + token } : {}),
        ...headers,
      };
      if (anonymous || login)
        for (const name of Object.keys(requestHeaders))
          if (name.toLowerCase() === "authorization") delete requestHeaders[name];
      uni.request({
        url: base() + "/api/v1/user" + path,
        method,
        data: data as any,
        timeout: 25000,
        header: requestHeaders,
        success(response) {
          const result = response.data as Envelope<T>;
          if (!result || typeof result !== "object" || typeof result.code !== "string"
            || (response.statusCode >= 200 && response.statusCode < 300
              && (result.code !== "OK" || !("data" in result)))) {
            reject(new ApiError(
              "NETWORK",
              method === "GET" ? "服务响应不完整，请重试" : "操作结果尚未确认，请刷新查看",
              "", 0,
            ));
            return;
          }
          if (
            response.statusCode >= 200 &&
            response.statusCode < 300 &&
            result.code === "OK"
          )
            resolve(result.data);
          else {
            if (response.statusCode === 401) {
              // A delayed failure for an old session must not erase a newer login.
              if (token && store.session?.token === token) store.set(null);
              if (publicRead && token && !anonymous) {
                request(true);
                return;
              }
            }
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
          reject(new ApiError(
            "NETWORK",
            method === "GET" ? "网络连接失败，请重试" : "操作结果尚未确认，请刷新查看",
            "", 0,
          ));
        },
      });
    }
    request();
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
