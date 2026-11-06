<script setup lang="ts">
import {onMounted,onUnmounted,ref,watch} from 'vue';
import {request} from '../platform-api';
import {notifyError} from '../api';
import {useSession} from '../stores/session';
type Delivery={id:string;eventId:string;orderId:string;channel:string;state:string;attempts:number;lastError:string|null;createdAt:string;updatedAt:string};
const session=useSession(),rows=ref<Delivery[]>([]),state=ref(''),loading=ref(false),more=ref(false);
const labels:Record<string,string>={REQUESTED:'待发送',PROCESSING:'发送中',ACCEPTED:'渠道已受理',SUCCEEDED:'短信确认送达',FAILED:'失败',UNKNOWN:'结果未知',SKIPPED:'未满足发送条件'};
let sequence=0,active=true;
async function load(reset=true){
  if(loading.value && !reset)return;
  const current=++sequence,token=session.session?.token;
  loading.value=true;
  try{
    const query=new URLSearchParams({limit:'50'});
    if(state.value)query.set('state',state.value);
    if(!reset && rows.value.length)query.set('beforeId',rows.value[rows.value.length-1]!.id);
    const result=await request<Delivery[]>('/platform/notifications/deliveries?'+query);
    if(!active || current!==sequence || token!==session.session?.token)return;
    rows.value=reset?result:[...rows.value,...result];more.value=result.length===50;
  }catch(e){if(active && current===sequence)notifyError(e);}
  finally{if(active && current===sequence)loading.value=false;}
}
watch(state,()=>{void load();});
watch(()=>session.session?.token,()=>{sequence++;rows.value=[];more.value=false;if(session.session)void load();});
onMounted(()=>{void load();});onUnmounted(()=>{active=false;sequence++;});
</script>
<template>
  <section class="page-head"><div><h1>外部通知投递</h1><p class="muted">查看短信和微信订阅提醒。渠道受理不等于用户已读；订单与站内消息保留完整业务进度。</p></div><el-button :loading="loading" @click="load()">刷新</el-button></section>
  <section class="panel">
    <div class="row"><el-select v-model="state" placeholder="全部状态" clearable style="width:220px"><el-option v-for="(label,value) in labels" :key="value" :label="label" :value="value" /></el-select></div>
    <el-alert type="info" :closable="false" title="结果未知的通知不会自动重发。请先在渠道侧核实；未授权或未绑定手机号会跳过。" style="margin:16px 0" />
    <el-table :data="rows" v-loading="loading" row-key="id">
      <el-table-column prop="id" label="投递编号" width="110"/><el-table-column prop="orderId" label="订单编号" width="120"/>
      <el-table-column prop="channel" label="渠道" width="110"/><el-table-column label="状态" width="150"><template #default="{row}">{{labels[row.state]||row.state}}</template></el-table-column>
      <el-table-column prop="attempts" label="发送次数" width="100"/><el-table-column prop="lastError" label="原因" min-width="200"/>
      <el-table-column prop="createdAt" label="创建时间" min-width="190"/><el-table-column prop="updatedAt" label="更新时间" min-width="190"/>
    </el-table>
    <el-button v-if="more" :loading="loading" @click="load(false)" style="margin-top:16px">加载更多</el-button>
  </section>
</template>
