import { api, ApiError } from './api';
import type { components } from '../../contracts/openapi';

type PaymentIntent = components['schemas']['PaymentIntent'];
export type PaymentView = {
  [Key in 'id' | 'checkoutId' | 'channel' | 'state' | 'expiresAt' | 'closeRequested']-?: NonNullable<PaymentIntent[Key]>;
} & Pick<PaymentIntent, 'lastError'> & { action: Record<string, string> };
export async function startPayment(id: string, version: string): Promise<PaymentView> {
  let channel = 'MINI_PROGRAM';
  // #ifdef H5
  channel = /MicroMessenger/i.test(navigator.userAgent)
    ? 'JSAPI'
    : /Android|iPhone|iPad|Mobile/i.test(navigator.userAgent) ? 'H5' : 'NATIVE';
  // #endif
  let payment: PaymentView;
  try {
    payment = await api<PaymentView>('/checkouts/' + id + '/payment');
  } catch (error) {
    if (!(error instanceof ApiError) || error.code !== 'PAYMENT_NOT_FOUND') throw error;
    try {
      payment = await api<PaymentView>('/checkouts/' + id + '/payment-intents', 'POST',
        { channel, version }, { 'Idempotency-Key': 'payment_' + id });
    } catch (error) {
      // #ifdef H5
      if (error instanceof ApiError && error.code === 'WECHAT_IDENTITY_REQUIRED' && channel === 'JSAPI') {
        const oauth = await api<{url:string}>('/auth/wechat/oauth/start', 'POST');
        window.location.assign(oauth.url);
      }
      // #endif
      throw error;
    }
  }
  if (payment.state === 'SUCCEEDED' || payment.closeRequested || !payment.action.type) return payment;
  const action = payment.action;
  if (action.type === 'requestPayment') {
    // #ifdef MP-WEIXIN
    await uni.requestPayment({ provider: 'wxpay', timeStamp: action.timeStamp!, nonceStr: action.nonceStr!,
      package: action.package!, signType: 'RSA', paySign: action.paySign! });
    // #endif
  }
  // #ifdef H5
  if (action.type === 'redirect') {
    const url = new URL(action.url!);
    url.searchParams.set('redirect_url', window.location.href);
    window.location.assign(url.href);
  }
  if (action.type === 'wechatBridge') {
    type Bridge = { invoke(name:string,args:Record<string,string>,callback:(result:unknown)=>void):void };
    const bridge = (window as unknown as {WeixinJSBridge?:Bridge}).WeixinJSBridge;
    if (!bridge) throw new Error('微信支付尚未就绪，请在微信中重新打开页面');
    await new Promise<void>((resolve) => bridge.invoke('getBrandWCPayRequest', action, () => resolve()));
  }
  // #endif
  return api<PaymentView>('/checkouts/' + id + '/payment');
}

export async function paymentQr(value: string): Promise<string> {
  // #ifdef H5
  const QRCode = await import('qrcode');
  return QRCode.toDataURL(value, { width: 280, margin: 2 });
  // #endif
  // #ifndef H5
  return '';
  // #endif
}
