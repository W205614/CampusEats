import { ref, onUnmounted } from 'vue';
import { request } from './platform-api';
import { useSession } from './stores/session';
import { mergeRecords, type CursorPage, type Store } from '../../contracts/platform';

/** A page sequence belongs to one employee session and one applied filter. */
export function useStorePages() {
  const session = useSession(), stores = ref<Store[]>([]), storeHasMore = ref(false), storeLoading = ref(false);
  let cursor: string | null = null, epoch = 0, active = true, applied = '';
  onUnmounted(() => { active = false; epoch++; });
  async function loadStores(reset = false, filters = '') {
    if (!reset && (storeLoading.value || !storeHasMore.value)) return false;
    const token = session.session?.token;
    if (!token) return false;
    if (reset) epoch++;
    const targetFilters = reset ? filters : applied;
    const current = epoch;
    storeLoading.value = true;
    try {
      const params = new URLSearchParams(targetFilters);
      params.set('limit', '50');
      if (!reset && cursor) params.set('cursor', cursor);
      const page = await request<CursorPage<Store>>('/merchant/stores/page?' + params);
      if (!active || current !== epoch || token !== session.session?.token) return false;
      stores.value = mergeRecords(reset ? [] : stores.value, page.records, row => row.id);
      applied = targetFilters;
      cursor = page.nextCursor;
      storeHasMore.value = page.hasMore;
      return true;
    } finally {
      if (active && current === epoch && token === session.session?.token) storeLoading.value = false;
    }
  }
  function clearStores() {
    epoch++; cursor = null; stores.value = []; storeHasMore.value = false; storeLoading.value = false;
  }
  return { stores, storeHasMore, storeLoading, loadStores, clearStores };
}
