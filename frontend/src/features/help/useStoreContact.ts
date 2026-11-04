import { useQuery } from '@tanstack/react-query';
import { fetchStoreContact, type StoreContact } from './storeContactApi.ts';

/** The storefront's query of the store's contact data; "Datos de la tienda" invalidates it after a save. */
export const STORE_CONTACT_KEY = ['store', 'contact'] as const;

const NONE: StoreContact = {};

/**
 * The store's contact data (UC-36), one query shared by "Contactanos", the footer, the phone menu, the WhatsApp button
 * and the header strip. While it loads, or when it fails, it has no values, so nothing that depends on them shows.
 */
export function useStoreContact(): StoreContact {
  const { data } = useQuery({
    queryKey: STORE_CONTACT_KEY,
    queryFn: ({ signal }) => fetchStoreContact(signal),
    staleTime: 5 * 60 * 1000,
  });
  return data ?? NONE;
}
