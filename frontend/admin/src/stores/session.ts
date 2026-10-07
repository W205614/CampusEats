import { defineStore } from "pinia";
import { ref } from "vue";
import type { Session } from "../../../contracts/models";
export const useSession = defineStore("session", () => {
  let initial: Session | null = null;
  try {
    initial = JSON.parse(sessionStorage.getItem("campus-admin") || "null");
  } catch {}
  const session = ref<Session | null>(initial);
  function set(value: Session | null) {
    session.value = value;
    if (value) sessionStorage.setItem("campus-admin", JSON.stringify(value));
    else sessionStorage.removeItem("campus-admin");
  }
  return { session, set };
});
