<script setup lang="ts">
import { ref, watch } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { api, toast, loggedIn } from '../../api';
import { useSession } from '../../stores/session';
import { usePagedList } from '../../paged-list';
interface Message { id: string; orderId: string; storeId: string; eventType: string; summary: string; createdAt: string; readAt?: string | null }
const session = useSession(), unreadOnly = ref(false), unread = ref<string>(), busy = ref(false);
let active = false, sequence = 0;
const list = usePagedList<Message>(() => '/messages/page' + (unreadOnly.value ? '?unread=true' : ''), row => row.id, () => active);
const rows = list.records, loading = list.loading, more = list.hasMore;
async function count() {
  const current = ++sequence, token = session.session?.token;
  try {
    const value = await api<{ count: string }>('/messages/unread');
    if (active && current === sequence && token === session.session?.token) unread.value = value.count;
  } catch (e) { if (active && current === sequence && token === session.session?.token) { unread.value = undefined; toast(e); } }
}
async function refresh() { if (active && loggedIn()) await Promise.all([count(), list.load()]); }
function toggleUnread(event: unknown) {
  unreadOnly.value = (event as { detail?: { value?: boolean } }).detail?.value === true;
}
async function mark(row: Message, open = false) {
  if (busy.value || !active) return;
  busy.value = true; const token = session.session?.token;
  try {
    if (!row.readAt) await api('/messages/' + row.id + '/read', 'POST');
    if (!active || token !== session.session?.token) return;
    row.readAt = row.readAt || new Date().toISOString(); await count();
    if (open && active && token === session.session?.token) uni.switchTab({ url: '/pages/orders/index' });
    else if (unreadOnly.value) await list.load();
  } catch (e) { if (active && token === session.session?.token) toast(e); }
  finally { if (active && token === session.session?.token) busy.value = false; }
}
watch(unreadOnly, () => { if (active) void list.load(); });
watch(() => session.session?.token, () => { sequence++; unread.value = undefined; busy.value = false; if (active && session.session) void refresh(); });
onShow(() => { active = true; void refresh(); });
function hide() { active = false; sequence++; list.cancel(); busy.value = false; }
onHide(hide); onUnload(hide);
</script>
<template>
  <view class="screen"><view class="row"><view><view class="title">站内消息</view><view class="muted">{{ unread === undefined ? '未读数待刷新' : '未读 ' + unread + ' 条' }}</view></view><button class="secondary small-button" :disabled="busy || loading" @click="refresh">刷新消息</button></view><view class="row message-filter"><text>只看未读</text><switch :checked="unreadOnly" @change="toggleUnread" /></view><view class="notice">离线消息可在回来后补读。这里只显示当前账号有权查看的订单消息。</view><view v-for="row in rows" :key="row.id" class="card message-card"><view class="row"><text :class="row.readAt ? 'muted' : 'unread'">{{ row.readAt ? '已读' : '未读' }}</text><text class="muted">{{ row.createdAt }}</text></view><view class="message-summary">{{ row.summary }}</view><view class="muted">订单 ID：{{ row.orderId }}</view><view class="row message-actions"><button v-if="!row.readAt" class="secondary small-button" :disabled="busy || loading" @click="mark(row)">标记已读</button><button class="primary small-button" :disabled="busy" @click="mark(row,true)">前往我的订单</button></view></view><button v-if="more" class="secondary" :disabled="busy || loading" :loading="loading" @click="list.load(false)">加载更多消息</button><view v-if="!loading && !rows.length" class="muted empty">当前筛选下暂无消息。</view></view>
</template>
<style scoped>.message-filter{margin:24rpx 0}.message-card{margin-top:24rpx}.message-summary{font-size:30rpx;line-height:1.6;margin:20rpx 0}.message-actions{justify-content:flex-end;margin-top:20rpx;gap:16rpx}.unread{color:#176346;font-weight:650}.empty{padding:56rpx 0;text-align:center}</style>
