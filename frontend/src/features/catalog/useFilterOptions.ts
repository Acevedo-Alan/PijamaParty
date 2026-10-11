import { useQuery } from '@tanstack/react-query';
import { fetchFilterOptions } from './catalogApi.ts';

/** The options of the filters panel for the whole shop or one category (UC-02). */
export function useFilterOptions(category: string | undefined) {
  return useQuery({
    queryKey: ['catalog', 'filters', category ?? null],
    queryFn: ({ signal }) => fetchFilterOptions(category, signal),
  });
}
