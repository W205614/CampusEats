import type { components, operations } from './openapi';
type Schemas = components['schemas'];
/** Response fields are derived from the server schema, including nested records. */
type Complete<T> = T extends (infer U)[]
  ? Complete<U>[]
  : T extends object
    ? { [P in keyof T]-?: Complete<T[P]> }
    : T;
export type Id = string;
export type Session = Complete<Schemas['SessionView']>;
export type Envelope<T> = Omit<operations['cart']['responses'][200]['content']['*/*'], 'data'> & {
  data: T;
};
export type Category = Complete<Schemas['CategoryView']>;
export type Flavor = Complete<Schemas['FlavorView']>;
export type Component = Complete<Schemas['ComponentView']>;
export type Product = Complete<Schemas['ProductView']>;
export type CartItem = Complete<Schemas['CartItemView']>;
export type Cart = Complete<Schemas['Cart']>;
export type Building = Complete<Schemas['BuildingView']>;
export type Address = Complete<Schemas['AddressView']>;
export type Shop = Complete<Schemas['ShopView']>;
export type Quote = Complete<Schemas['Quote']>;
export type Detail = Complete<Schemas['DetailView']>;
export type Order = Complete<Schemas['OrderView']>;
export type Page<T> = Omit<Complete<Schemas['PageOrderView']>, 'records'> & { records: T[] };
export type SubmitRequest = Schemas['Submit'];
export type PreviewRequest = Schemas['Preview'];
export type CartChangeRequest = Schemas['CartChange'];
export type ActionRequest = Schemas['OrderAction'];
export const statuses: Record<number, string> = {
  1: '待支付',
  2: '待接单',
  3: '备餐中',
  4: '配送中',
  5: '已送达',
  6: '已取消',
};
export const roles: Record<string, string> = {
  ADMIN: '管理员',
  OPERATOR: '运营员',
  DELIVERER: '配送员',
  USER: '用户',
};
export const money = (v: string | number) => Number(v || 0).toFixed(2);
export const when = (v: string) =>
  v ? new Date(v).toLocaleString('zh-CN', { hour12: false }) : '—';
