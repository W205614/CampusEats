import type { components } from './openapi';
type Complete<T> = T extends (infer U)[]
  ? Complete<U>[]
  : T extends object
    ? { [P in keyof T]-?: Complete<T[P]> }
    : T;
export type Id = string;
export type Session = Omit<Complete<components['schemas']['SessionView']>, 'role'> & {
  role: 'PLATFORM_ADMIN' | 'SHOP_MANAGER' | 'OPERATOR' | 'DELIVERER' | 'USER' | 'ADMIN';
  storeId?: string | null;
  campusId?: string | null;
};
export interface Envelope<T> {
  code: string;
  message: string;
  data: T;
  requestId: string;
}
export const roles: Record<string, string> = {
  PLATFORM_ADMIN: '平台管理员',
  SHOP_MANAGER: '店长',
  OPERATOR: '运营员',
  DELIVERER: '配送员',
  USER: '用户',
  ADMIN: '旧版管理员',
};
export const money = (v: string | number) => Number(v || 0).toFixed(2);
export const when = (v: string) =>
  v ? new Date(v).toLocaleString('zh-CN', { hour12: false }) : '—';
