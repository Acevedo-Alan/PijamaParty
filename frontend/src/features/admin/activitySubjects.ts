import { es } from '../../i18n/es.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import type { ActivityEntry } from './activityApi.ts';

/** How an activity entry (UC-37) names and links what it was about: the "Actividad" screen and the summary's block. */
const subjectNames: Readonly<Partial<Record<string, string>>> = esAdmin.activityList.subjects;
const provinceNames: Readonly<Partial<Record<string, string>>> = es.provinces;

/** The screen of the thing an entry is about, when it has one. */
export function subjectPath({ type, id }: ActivityEntry['subject']): string | null {
  switch (type) {
    case 'PRODUCT':
      return id === null ? null : `/admin/productos/${id}`;
    case 'ORDER':
      return id === null ? null : `/admin/pedidos/${id}`;
    case 'CATEGORY':
      return '/admin/categorias';
    case 'WITHDRAWAL':
      return '/admin/arrepentimientos';
    case 'COUPON':
      return '/admin/cupones';
    case 'REVIEW':
      return '/admin/resenas';
    case 'SHIPPING':
      return '/admin/envios';
    case 'HOME_BANNER':
      return '/admin/portada';
    case 'STORE_SETTINGS':
      return '/admin/datos-de-la-tienda';
    default:
      return null;
  }
}

/** How the screen names an entry's subject: its label (a province translated), or else its section. */
export function subjectLabel({ type, label }: ActivityEntry['subject']): string | null {
  if (label === null) {
    return subjectNames[type] ?? null;
  }
  return type === 'SHIPPING' ? (provinceNames[label] ?? label) : label;
}
