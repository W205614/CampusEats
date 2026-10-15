export type Id = string;
export interface Campus {
  id: Id;
  name: string;
  enabled: boolean;
}
export interface Store {
  id: Id;
  campusId: Id;
  campusName: string;
  name: string;
  enabled: boolean;
  open: boolean;
  accepting: boolean;
  hours: string[];
  deliveryFee: string;
  packagingFee: string;
  phone: string;
  version: Id;
}
export interface Product {
  id: Id;
  storeId: Id;
  name: string;
  itemType: 'DISH' | 'SETMEAL';
  categoryId: Id;
  price: string;
  status: number;
  description: string;
  image: string;
  remaining: number;
  flavors?: { name: string; values: string[] }[];
  components?: { dishId: Id; copies: number }[];
  promotion?: { id: Id; priceCents: Id; endsAt: string };
}
export interface CartItem {
  id: Id;
  itemType: 'DISH' | 'SETMEAL';
  itemId: Id;
  name: string;
  quantity: number;
  flavors: Record<string, string>;
  price: string;
  image: string;
}
export interface Cart {
  storeId: Id;
  storeName?: string;
  campusId?: Id;
  campusName?: string;
  cartVersion: Id;
  items: CartItem[];
}
export interface Address {
  id: Id;
  buildingId: Id;
  buildingName: string;
  campusId: Id;
  room: string;
  consignee: string;
  phone: string;
  isDefault: boolean;
}
export interface Selection {
  storeId: Id;
  cartVersion: Id;
  fulfillment: 'DELIVERY' | 'PICKUP';
  addressId?: Id | null;
  couponId?: Id | null;
}
export interface CheckoutInput {
  stores: Selection[];
  platformCouponId?: Id | null;
  quoteHash?: string;
  remark?: string;
}
export interface QuotePart {
  storeId: Id;
  storeName: string;
  campusName: string;
  fulfillment: string;
  subtotalCents: Id;
  shopDiscountCents: Id;
  platformDiscountCents: Id;
  deliveryCents: Id;
  packagingCents: Id;
  totalCents: Id;
  items: { name: string; quantity: number; paidCents: Id }[];
}
export interface Quote {
  stores: QuotePart[];
  totalCents: Id;
  quoteHash: string;
}
export interface Checkout {
  id: Id;
  state: string;
  totalCents: Id;
  version: Id;
  orders: Order[];
}
export interface Order {
  id: Id;
  number: string;
  storeId: Id;
  storeName: string;
  campusName: string;
  version: Id;
  status: number;
  payStatus: number;
  fulfillment: string;
  ready: boolean;
  pickupCode?: string;
  amount: string;
  paidCents: Id;
  refundedCents: Id;
  phone?: string;
  address: string;
  consignee: string;
  courierId?: Id;
  details: Detail[];
  aftersales: Aftersale[];
  refunds: Refund[];
  delivery?: { state: string };
}
export interface Detail {
  id: Id;
  name: string;
  number: number;
  lineTotalCents: Id;
  refundedQuantity: number;
  reservedQuantity: number;
}
export interface Aftersale {
  id: Id;
  orderId: Id;
  storeId: Id;
  kind: string;
  state: string;
  reason: string;
  decision: string;
  version: Id;
  amountCents: Id;
  appealed: boolean;
  createdAt: string;
  items: { detailId: Id; quantity: number; name: string }[];
  evidence: { id: string }[];
}
export interface Refund {
  id: Id;
  orderId: Id;
  amountCents: Id;
  state: string;
  attempts: number;
  lastError: string;
}
export interface Coupon {
  id: Id;
  storeId?: Id;
  name: string;
  kind: string;
  thresholdCents: Id;
  value: Id;
  capCents?: Id;
  total: number;
  claimed: number;
  perUser: number;
  startsAt: string;
  endsAt: string;
  enabled: boolean;
  state?: string;
}
export interface Review {
  id: Id;
  orderId: Id;
  rating: number;
  content: string;
  reply?: string;
  hidden: boolean;
  hideReason: string;
}
export interface Staff {
  id: Id;
  username: string;
  name: string;
  role: string;
  storeId?: Id;
  campusId?: Id;
  status: number;
  mustChangePassword: boolean;
}
export const cents = (value: string | number | undefined) =>
  ((Number(value) || 0) / 100).toFixed(2);
export const orderStatus: Record<number, string> = {
  1: '待支付',
  2: '待接单',
  3: '备餐中',
  4: '配送中',
  5: '已完成',
  6: '已取消',
};
export const states: Record<string, string> = {
  PENDING: '待处理',
  PAID: '已支付',
  CANCELLED: '已取消',
  AVAILABLE: '可使用',
  LOCKED: '已锁定',
  USED: '已使用',
  ESCALATED: '平台介入中',
  APPROVED: '退款处理中',
  REJECTED: '已拒绝',
  WITHDRAWN: '已撤销',
  REFUNDED: '已退款',
  REQUESTED: '待执行',
  PROCESSING: '处理中',
  SUCCEEDED: '已完成',
  FAILED: '失败',
  OPEN: '可抢单',
  ASSIGNED: '已分配',
  PICKED: '已取餐',
  COMPLETED: '已送达',
};
