<script setup lang="ts">
import {ref,watch} from 'vue';
import {request} from '../platform-api';
import {selectedStore} from '../context';
import {useSession} from '../stores/session';
import {notifyError} from '../api';
const session=useSession(), merchant=ref({subMchid:'',enabled:false,version:'0'}), statements=ref<Record<string,unknown>[]>([]), differences=ref<Record<string,unknown>[]>([]), exceptions=ref<Record<string,unknown>[]>([]), busy=ref(false);
const day=ref(new Date(Date.now()-86400000).toLocaleDateString('sv-SE',{timeZone:'Asia/Shanghai'})), coupon=ref(''), participants=ref('');
const integrations=ref<Record<string,unknown>[]>([]);
let sequence=0;
async function load(){const current=++sequence,store=selectedStore.value;if(!store)return;
 try {const values=await Promise.all([request<typeof merchant.value>(`/merchant/stores/${store}/payment-config`),request<Record<string,unknown>[]>(`/merchant/stores/${store}/channel-reconciliations`),request<Record<string,unknown>[]>(`/merchant/stores/${store}/channel-differences`)]);
 if(current!==sequence)return;[merchant.value,statements.value,differences.value]=values;
 if(session.session?.role==='PLATFORM_ADMIN') {const [value,states]=await Promise.all([request<Record<string,unknown>[]>('/platform/payment-exceptions'),request<Record<string,unknown>[]>('/platform/integrations')]);if(current===sequence){exceptions.value=value;integrations.value=states;}}
 }catch(e){if(current===sequence)notifyError(e);}}
watch(selectedStore,()=>void load(),{immediate:true});
async function run(action:()=>Promise<unknown>){if(busy.value)return;busy.value=true;try{await action();await load();}catch(e){notifyError(e);}finally{busy.value=false;}}
async function review(row:Record<string,unknown>){const reason=window.prompt('填写人工核对依据（不会自动改账）');if(reason?.trim())await run(()=>request(`/platform/stores/${selectedStore.value}/channel-differences/${row.id}/review`,'POST',{reason}));}
</script>
<template>
 <section><h2>渠道收款与对账</h2><p>商家独立收款，平台不抽佣。这里的账单和差异处理不代表平台向商家付款。</p>
 <template v-if="session.session?.role==='PLATFORM_ADMIN'"><h3>接入状态</h3><el-table :data="integrations"><el-table-column prop="provider" label="服务"/><el-table-column prop="status" label="验证状态"/><el-table-column prop="connectivityEvidence" label="渠道响应记录"/><el-table-column prop="acceptanceEvidence" label="人工验收证据"/></el-table></template>
 <el-form inline @submit.prevent><el-form-item label="微信子商户号"><el-input v-model="merchant.subMchid" :disabled="session.session?.role!=='PLATFORM_ADMIN'" /></el-form-item><el-form-item label="启用收款"><el-switch v-model="merchant.enabled" :disabled="session.session?.role!=='PLATFORM_ADMIN'" /></el-form-item><el-button v-if="session.session?.role==='PLATFORM_ADMIN'" :disabled="busy" @click="run(()=>request(`/platform/stores/${selectedStore}/payment-config`,'PUT',merchant))">保存收款配置</el-button></el-form>
 <p>录入已完成进件和授权的子商户号。已有交易保留原收款主体，用于退款及核对。</p>
 <el-form inline @submit.prevent><el-form-item label="渠道账单日期"><el-input v-model="day" type="date" /></el-form-item><el-button :disabled="busy||!selectedStore" @click="run(()=>request(`/merchant/stores/${selectedStore}/channel-reconciliations`,'POST',{businessDate:day}))">下载并对账</el-button><el-button :disabled="busy" @click="load">刷新</el-button></el-form>
 <el-table :data="statements"><el-table-column prop="businessDate" label="日期"/><el-table-column prop="state" label="结果"/><el-table-column prop="tradeCount" label="交易记录"/><el-table-column prop="differenceCount" label="差异数"/><el-table-column prop="feeCents" label="渠道手续费/分"/></el-table>
 <h3>渠道差异</h3><el-table :data="differences"><el-table-column prop="reference" label="渠道单号"/><el-table-column prop="kind" label="差异类型"/><el-table-column prop="expectedCents" label="本地金额/分"/><el-table-column prop="actualCents" label="渠道金额/分"/><el-table-column prop="state" label="处理状态"/><el-table-column prop="resolution" label="核对依据"/><el-table-column v-if="session.session?.role==='PLATFORM_ADMIN'"><template #default="{row}"><el-button v-if="row.state==='OPEN'" :disabled="busy" @click="review(row)">记录核对</el-button></template></el-table-column></el-table>
 <template v-if="session.session?.role==='PLATFORM_ADMIN'"><h3>待核对支付</h3><el-table :data="exceptions"><el-table-column prop="checkoutId" label="结算单"/><el-table-column prop="state" label="状态"/><el-table-column prop="lastError" label="错误码"/><el-table-column><template #default="{row}"><el-button v-if="row.state==='REVIEW'" :disabled="busy" @click="run(()=>request(`/platform/payment-exceptions/${row.id}/retry`,'POST'))">重新查单</el-button></template></el-table-column></el-table>
 <h3>跨店联合优惠参与范围</h3><p>在优惠尚未领取时配置，列出的商家共同承担减免。</p><el-form inline @submit.prevent><el-form-item label="优惠模板ID"><el-input v-model="coupon" /></el-form-item><el-form-item label="参与店铺ID，逗号分隔"><el-input v-model="participants" /></el-form-item><el-button :disabled="busy" @click="run(()=>request(`/platform/coupons/${coupon}/participants`,'PUT',{stores:participants.split(',').map(x=>x.trim())}))">保存参与范围</el-button></el-form></template>
 </section>
</template>
