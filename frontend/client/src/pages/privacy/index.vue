<script setup lang="ts">
import {ref} from 'vue';
import {onShow} from '@dcloudio/uni-app';
import {api,toast} from '../../api';
const policy=ref<{version:string;operator:string;contact:string;privacyText:string;termsText:string}|null>(null);
onShow(async()=>{try{policy.value=await api('/auth/privacy-policy');}catch(e){toast(e);}});
</script>
<template>
  <view class="screen" v-if="policy">
    <view class="title">隐私政策与用户协议</view>
    <view class="muted">版本 {{policy.version}} · {{policy.operator}}</view>
    <view class="section-title">隐私政策</view><view class="card policy-text">{{policy.privacyText}}</view>
    <view class="section-title">用户协议</view><view class="card policy-text">{{policy.termsText}}</view>
    <view class="muted">联系方式：{{policy.contact}}</view>
  </view>
</template>
<style scoped>.policy-text{white-space:pre-wrap;line-height:1.9}</style>
