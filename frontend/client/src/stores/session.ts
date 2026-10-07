import { defineStore } from "pinia";
import { ref } from "vue";
import type { Session } from "../../../contracts/models";
function read(): Session | null {
  try {
    // #ifdef H5
    return JSON.parse(sessionStorage.getItem("campus-user") || "null");
    // #endif
    // #ifndef H5
    return uni.getStorageSync("campus-user") || null;
    // #endif
  } catch {
    return null;
  }
}
export const useSession = defineStore("session", () => {
  const session = ref<Session | null>(read());
  function set(value: Session | null) {
    session.value = value;
    // #ifdef H5
    if (value) sessionStorage.setItem("campus-user", JSON.stringify(value));
    else sessionStorage.removeItem("campus-user");
    // #endif
    // #ifndef H5
    if (value) uni.setStorageSync("campus-user", value);
    else uni.removeStorageSync("campus-user");
    // #endif
  }
  return { session, set };
});
