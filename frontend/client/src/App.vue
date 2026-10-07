<script setup lang="ts">
import { onLaunch } from "@dcloudio/uni-app";
onLaunch(() => {});
</script>
<style>
page {
  background: #f7f5f1;
  font-family: -apple-system, "Microsoft YaHei", sans-serif;
  color: #30332b;
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
  background: #ece6d9;
  border-radius: 28rpx;
  margin-bottom: 30rpx;
}
.eyebrow {
  font-size: 20rpx;
  letter-spacing: 3rpx;
  color: #9e673e;
}
.title {
  font-size: 44rpx;
  font-weight: 750;
  line-height: 1.25;
  margin: 16rpx 0;
}
.subtitle,
.muted {
  color: #838776;
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
  border: 1rpx solid #e7e8df;
  border-radius: 22rpx;
  padding: 28rpx;
  margin-bottom: 22rpx;
}
.primary {
  background: #c25f32 !important;
  color: #fff !important;
  border-radius: 15rpx;
  font-size: 28rpx;
  border: none;
  margin: 0;
}
.primary::after,
.secondary::after {
  border: 0;
}
.secondary {
  background: #f0ede4;
  color: #765d43;
  border-radius: 14rpx;
  font-size: 24rpx;
  margin: 0;
}
.small-button {
  padding: 0 24rpx;
  line-height: 65rpx;
  font-size: 24rpx;
}
.tag {
  background: #f3e8d8;
  color: #9c622f;
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
  color: #ad5129;
  font-size: 33rpx;
  font-weight: 700;
}
.field-label {
  font-size: 25rpx;
  color: #666e5c;
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
  background: #fbefdd;
  color: #966337;
  font-size: 24rpx;
  line-height: 1.7;
  padding: 20rpx;
  border-radius: 14rpx;
  margin-bottom: 22rpx;
}
.empty {
  text-align: center;
  padding: 70rpx 20rpx;
  color: #858976;
}
.empty-title {
  font-size: 34rpx;
  margin-bottom: 14rpx;
  color: #454d3b;
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
  background: #f2eee3;
  color: #a15932;
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
  border-color: #c25f32;
  background: #fff1e5;
  color: #ae562f;
}
.food-art {
  width: 140rpx;
  height: 140rpx;
  border-radius: 22rpx;
  background: radial-gradient(
    circle at 50% 45%,
    #fff5da 0 43%,
    #d4b884 44% 50%,
    #eee3cc 51%
  );
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 66rpx;
  flex-shrink: 0;
}
.food-art image {
  width: 100%;
  height: 100%;
  border-radius: 22rpx;
}
.card-name {
  font-weight: 650;
  font-size: 31rpx;
}
.card-description {
  color: #878b7e;
  font-size: 22rpx;
  line-height: 1.5;
  margin-top: 8rpx;
}
.sticky-summary {
  position: sticky;
  bottom: 15rpx;
  padding: 24rpx;
  background: #353b2d;
  color: #fff;
  border-radius: 20rpx;
  box-shadow: 0 8rpx 30rpx #0002;
  margin-top: 30rpx;
}
.sticky-summary .price {
  color: #f6c494;
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
  color: #9e5c35;
  font-size: 25rpx;
}
.link {
  text-decoration: underline;
  color: #99532b;
}
</style>
