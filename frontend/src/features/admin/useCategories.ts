import { useQuery } from '@tanstack/react-query';
import { fetchCategories } from './categoriesApi.ts';

export const CATEGORIES_KEY = ['admin', 'categories'] as const;

/** Every category, active or not, by position (UC-05); read only when `enabled`. */
export function useCategories(enabled = true) {
  return useQuery({ queryKey: CATEGORIES_KEY, queryFn: ({ signal }) => fetchCategories(signal), enabled });
}
