import { es } from '../../i18n/es.ts';
import type { SavedAddress } from './addressBookApi.ts';

/** "San Martín 1234, piso 3, depto. B": how the address book and the account summary name an address. */
export function addressLine(address: SavedAddress): string {
  const parts = [`${address.street} ${address.number}`];
  if (address.floor !== null) {
    parts.push(es.checkout.floorPart(address.floor));
  }
  if (address.apartment !== null) {
    parts.push(es.checkout.apartmentPart(address.apartment));
  }
  return parts.join(', ');
}

/** "Santa Fe, Santa Fe (3000)": the city, the province and the postal code. */
export function addressPlace(address: SavedAddress): string {
  return `${address.city}, ${es.provinces[address.province]} (${address.postalCode})`;
}
