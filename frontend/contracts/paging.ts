// Refresh every loaded page so polling preserves history and its current states.
export async function readPages<T extends { id: string }>(fetchPage: (beforeId?: string) => Promise<T[]>, pages: number) {
  const items: T[] = [];
  let hasMore = false;
  for (let page = 0; page < pages; page++) {
    const batch = await fetchPage(items[items.length - 1]?.id);
    items.push(...batch);
    hasMore = batch.length === 100;
    if (!hasMore) break;
  }
  return { items, hasMore };
}
