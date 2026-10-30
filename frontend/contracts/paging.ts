// Refresh the same loaded history window in bounded batches, including older states.
export async function readPages<T extends { id: string }>(
  fetchPage: (beforeId: string | undefined, limit: number) => Promise<T[]>,
  pages: number,
  isCurrent: () => boolean = () => true,
) {
  const items: T[] = [];
  const target = pages * 100;
  let hasMore = false;
  while (items.length < target && isCurrent()) {
    const limit = Math.min(1000, target - items.length);
    const batch = await fetchPage(items[items.length - 1]?.id, limit);
    if (!isCurrent()) break;
    if (batch.length > limit) throw new Error('History response exceeds requested limit');
    items.push(...batch);
    hasMore = batch.length === limit;
    if (!hasMore) break;
  }
  return { items, hasMore };
}
