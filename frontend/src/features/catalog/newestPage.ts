import { fetchProductPage } from "./catalogApi.ts";
import { NO_FILTERS } from "./listingCriteria.ts";

/**
 * The first page of the newest products, of one category or of the whole store (UC-01). "Completá tu look" and
 * "Lo más nuevo" read it through this one key, so two of them on a page, or a return to a page, share the request
 * (T-E8-22). The listing itself is an infinite query with its own cache shape and keeps its own key.
 */
export function newestPageQuery(category: string | undefined) {
  return {
    queryKey: ["catalog", "newest", category ?? null] as const,
    queryFn: ({ signal }: { signal: AbortSignal }) =>
      fetchProductPage(
        category,
        { filters: NO_FILTERS, sort: "NEWEST" },
        0,
        signal,
      ),
  };
}
