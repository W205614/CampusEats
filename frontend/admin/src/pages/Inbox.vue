<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElCheckbox } from 'element-plus';
import { useRouter } from 'vue-router';
import { request } from '../platform-api';
import { notifyError } from '../api';
import { useSession } from '../stores/session';
import { usePagedList } from '../paged-list';
import { selectedStore } from '../context';
interface Message { id: string; orderId: string; storeId: string; eventType: string; summary: string; createdAt: string; readAt?: string | null }
const session = useSession(), router = useRouter(), unreadOnly = ref(false), unread = ref<string>(), busy = ref(false);
const list = usePagedList<Message>(() => '/me/messages/page' + (unreadOnly.value ? '?unread=true' : ''), row => row.id);
const rows = list.records, loading = list.loading, more = list.hasMore;
let alive = true, sequence = 0;
onBeforeUnmount(() => { alive = false; sequence++; });
async function count() {
  const current = ++sequence, token = session.session?.token;
  try {
    const response = await request<{ count: string }>('/me/messages/unread');
    if (alive && current === sequence && token === session.session?.token) unread.value = response.count;
  } catch (e) { if (alive && current === sequence && token === session.session?.token) { unread.value = undefined; notifyError(e); } }
}
async function refresh() { await Promise.all([count(), list.load()]); }
async function mark(row: Message, open = false) {
  if (busy.value) return;
  busy.value = true; const token = session.session?.token;
  try {
    const targetStore = String(row.storeId ?? '');
    const selectsStore = session.session?.role !== 'DELIVERER';
    if (open && selectsStore && !/^[1-9][0-9]*$/.test(targetStore))
      throw new Error('消息店铺信息无效，请刷新后重试');
    if (!row.readAt) await request('/me/messages/' + row.id + '/read', 'POST');
    if (!alive || token !== session.session?.token) return;
    row.readAt = row.readAt || new Date().toISOString();
    await count();
    if (open && alive && token === session.session?.token) {
      if (selectsStore) selectedStore.value = targetStore;
      await router.push({ path: '/orders', query: { orderId: row.orderId } });
    }
    else if (unreadOnly.value) await list.load();
  } catch (e) { if (alive && token === session.session?.token) notifyError(e); }
  finally { if (alive && token === session.session?.token) busy.value = false; }
}
watch(unreadOnly, () => void list.load());
watch(() => session.session?.token, () => { sequence++; busy.value = false; unread.value = undefined; if (session.session) void refresh(); });
onMounted(refresh);
</script>
<template>
  <section class="page-head"><div><h1>站内消息</h1><p class="muted">业务消息持久保存，离线后回来仍可补读。消息列表始终按当前账号和资源权限查询。</p></div><el-button :disabled="busy" :loading="loading" @click="refresh">刷新消息</el-button></section>
  <section class="panel"><div class="toolbar"><strong>{{ unread === undefined ? '未读数待刷新' : '未读 ' + unread + ' 条' }}</strong><el-checkbox v-model="unreadOnly">只看未读</el-checkbox></div><el-table :data="rows"><el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.readAt ? 'info' : 'success'">{{ row.readAt ? '已读' : '未读' }}</el-tag></template></el-table-column><el-table-column prop="summary" label="消息" min-width="260" /><el-table-column prop="orderId" label="订单 ID" /><el-table-column prop="createdAt" label="时间" min-width="180" /><el-table-column label="操作" min-width="220"><template #default="{row}"><el-button v-if="!row.readAt" :disabled="busy || loading" @click="mark(row)">标记已读</el-button><el-button :disabled="busy" @click="mark(row,true)">前往订单履约</el-button></template></el-table-column></el-table><el-button v-if="more" :loading="loading" :disabled="busy" @click="list.load(false)">加载更多消息</el-button><p v-if="!loading && !rows.length" class="muted">当前筛选下暂无消息。</p></section>
</template>
