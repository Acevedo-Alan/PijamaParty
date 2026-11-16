import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { whatsAppUrl } from '../help/storeContact.ts';
import { useStoreContact } from '../help/useStoreContact.ts';

interface ProductWhatsAppProps {
  name: string;
  slug: string;
  size: string;
  color: string;
  priceCents: number;
}

/**
 * UC-03: "Consultar por WhatsApp" under the add button, for a picked variant with stock (the parent decides when).
 * A question to the business, not another way to buy: the message names the product, the picks, the price and the
 * product's address, nothing about the visitor (R-05). Nothing without the store's number (UC-36).
 */
export function ProductWhatsApp({ name, slug, size, color, priceCents }: ProductWhatsAppProps) {
  const number = useStoreContact().whatsAppNumber;
  if (number === undefined) {
    return null;
  }
  const link = `${window.location.origin}/producto/${slug}`;
  const message = es.product.whatsAppAskMessage(name, size, color, formatMoney(priceCents), link);
  return (
    <a
      className="pdp-whatsapp"
      href={`${whatsAppUrl(number)}?text=${encodeURIComponent(message)}`}
      target="_blank"
      rel="noopener noreferrer"
      aria-label={es.product.whatsAppAskLabel}
    >
      {es.product.whatsAppAsk}
    </a>
  );
}
