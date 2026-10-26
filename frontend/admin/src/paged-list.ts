import { ref, watch, onUnmounted, type Ref } from 'vue';
import { request } from './platform-api';
import { notifyError } from './api';
import { useSession } from './stores/session';
import type { CursorPage } from '../../contracts/platform';

export function usePagedList<T>(path: () => string, key: (row: T) => string) {
  const session = useSession(), records = ref<T[]>([]) as Ref<T[]>;
  const loading = ref(false), hasMore = ref(false), loaded = ref(false);
  let cursor: string | null = null, sequence = 0, alive = true;
  const scope = () => (session.session?.token || '') + '|' + path();
  function clear() { ++sequence; records.value = []; cursor = null; hasMore.value = loaded.value = loading.value = false; }
  function cancel() { ++sequence; loading.value = false; }
  async function load(reset = true) {
    if (!alive || (!reset && (loading.value || !hasMore.value))) return false;
    const current = ++sequence, expected = scope(); loading.value = true;
    const target = path() + (path().includes('?') ? '&' : '?') + 'limit=50'
      + (!reset && cursor ? '&cursor=' + encodeURIComponent(cursor) : '');
    try {
      const page = await request<CursorPage<T>>(target);
      if (!alive || current !== sequence || expected !== scope()) return false;
      if (!Array.isArray(page.records) || typeof page.hasMore !== 'boolean') throw new Error('分页响应不完整，请重试');
      const values = new Map((reset ? [] : records.value).map(row => [key(row),row]));
      page.records.forEach(row => values.set(key(row),row));
      records.value = [...values.values()]; cursor=page.nextCursor; hasMore.value=page.hasMore; loaded.value=true;
      return true;
    } catch(error) { if (alive && current === sequence && expected === scope()) notifyError(error); return false; }
    finally { if (alive && current === sequence && expected === scope()) loading.value=false; }
  }
  watch(scope,clear,{flush:'sync'}); onUnmounted(() => { alive=false; cancel(); });
  return { records,loading,hasMore,loaded,load,clear,cancel };
}
