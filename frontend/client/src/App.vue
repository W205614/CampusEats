<script setup lang="ts">
import { onLaunch } from "@dcloudio/uni-app";
onLaunch(() => {});
</script>
<style>
page {
  background: #f2f4f0;
  font-family: "Microsoft YaHei", "DengXian", "PingFang SC", "Segoe UI", sans-serif;
  color: #20382d;
  font-size: 28rpx;
}
view,
text,
input,
button {
  box-sizing: border-box;
}
.screen {
  padding: 30rpx;
  max-width: 900rpx;
  margin: auto;
}
.hero {
  padding: 38rpx 34rpx;
  background: #e3ebe3;
  border-radius: 8rpx;
  margin-bottom: 30rpx;
}
.eyebrow {
  font-size: 20rpx;
  letter-spacing: 3rpx;
  color: #22634e;
}
.title {
  font-size: 44rpx;
  font-weight: 750;
  line-height: 1.25;
  margin: 16rpx 0;
}
.subtitle,
.muted {
  color: #59675e;
  font-size: 24rpx;
  line-height: 1.7;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}
.section-title {
  font-size: 32rpx;
  font-weight: 650;
  margin: 30rpx 0 20rpx;
}
.card {
  background: #fff;
  border: 0;
  border-radius: 8rpx;
  padding: 28rpx;
  margin-bottom: 22rpx;
}
.primary {
  background: #22634e !important;
  color: #fff !important;
  border-radius: 6rpx;
  font-size: 28rpx;
  border: none;
  margin: 0;
}
.primary::after,
.secondary::after {
  border: 0;
}
.secondary {
  background: #e7eee7;
  color: #234f3c;
  border-radius: 6rpx;
  font-size: 24rpx;
  margin: 0;
}
.small-button {
  padding: 0 24rpx;
  line-height: 65rpx;
  font-size: 24rpx;
}
.tag {
  background: #e7eee7;
  color: #234f3c;
  border-radius: 8rpx;
  padding: 7rpx 12rpx;
  font-size: 20rpx;
  display: inline-block;
}
.tag.gray {
  background: #eeefe9;
  color: #7a8170;
}
.price {
  color: #22634e;
  font-size: 33rpx;
  font-weight: 700;
}
.field-label {
  font-size: 25rpx;
  color: #59675e;
  margin: 24rpx 0 12rpx;
}
.field {
  padding: 20rpx;
  background: #f6f6ef;
  border: 1rpx solid #e2e4d8;
  border-radius: 12rpx;
  height: 80rpx;
}
.notice {
  background: #edf3ed;
  color: #4c6151;
  font-size: 24rpx;
  line-height: 1.7;
  padding: 20rpx;
  border-radius: 6rpx;
  margin-bottom: 22rpx;
}
.empty {
  text-align: center;
  padding: 70rpx 20rpx;
  color: #59675e;
}
.empty-title {
  font-size: 34rpx;
  margin-bottom: 14rpx;
  color: #20382d;
}
.line {
  padding: 19rpx 0;
  border-bottom: 1rpx solid #eef0e7;
}
.bottom-space {
  height: 50rpx;
}
.quantity {
  display: flex;
  align-items: center;
  gap: 22rpx;
}
.quantity button {
  height: 56rpx;
  width: 56rpx;
  line-height: 52rpx;
  margin: 0;
  padding: 0;
  background: #e3ebe3;
  color: #22634e;
  border: 0;
  border-radius: 50%;
  font-size: 32rpx;
}
.quantity button::after {
  border: 0;
}
.divider {
  height: 1rpx;
  background: #eee;
  margin: 24rpx 0;
}
.modal {
  position: fixed;
  inset: 0;
  background: rgba(40, 37, 30, 0.38);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
  z-index: 100;
}
.modal .card {
  width: 100%;
  max-width: 650rpx;
}
.options {
  display: flex;
  flex-wrap: wrap;
  gap: 15rpx;
}
.option {
  border: 1rpx solid #dfdfd2;
  padding: 14rpx 24rpx;
  border-radius: 30rpx;
  font-size: 24rpx;
}
.option.selected {
  border-color: #22634e;
  background: #e7eee7;
  color: #22634e;
}
.food-art {
  width: 140rpx;
  height: 140rpx;
  border-radius: 8rpx;
  background: #e3ebe3;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 66rpx;
  flex-shrink: 0;
}
.food-art image {
  width: 100%;
  height: 100%;
  border-radius: 8rpx;
}
.card-name {
  font-weight: 650;
  font-size: 31rpx;
}
.card-description {
  color: #59675e;
  font-size: 22rpx;
  line-height: 1.5;
  margin-top: 8rpx;
}
.sticky-summary {
  position: sticky;
  bottom: 15rpx;
  padding: 24rpx;
  background: #173f35;
  color: #fff;
  border-radius: 20rpx;
  margin-top: 30rpx;
}
.sticky-summary .price {
  color: #e4eadc;
}
.form-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 30rpx;
}
.form-actions button {
  flex: 1;
}
.tap {
  color: #22634e;
  font-size: 25rpx;
}
.link {
  text-decoration: underline;
  color: #22634e;
}
textarea{width:100%;box-sizing:border-box;background:#f2f4f0;padding:20rpx;border-radius:12rpx;margin:24rpx 0;min-height:150rpx}input{min-height:72rpx}picker{min-height:48rpx}.modal>.card{width:calc(100% - 48rpx);max-width:820rpx;max-height:88vh;overflow:auto}
/* #ifdef H5 */
body, uni-page-body { font-family: "Microsoft YaHei", "DengXian", "PingFang SC", "Segoe UI", sans-serif; color: #20382d; }
a:focus-visible, button:focus-visible { outline: 3px solid #6b927c; outline-offset: 3px; }
/* #endif */
.primary[disabled], .secondary[disabled] { opacity: .6; }
</style>
