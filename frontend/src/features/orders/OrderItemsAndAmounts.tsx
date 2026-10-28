import { BlurredImage } from '../../components/BlurredImage.tsx';
import type { ListingImage } from '../catalog/catalogApi.ts';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import './orders.css';

export interface OrderItemLine {
  productName: string;
  size: string;
  color: string;
  quantity: number;
  lineTotalCents: number;
  /** The thumbnail (E7): the photo, or null for the placeholder; left out, no thumbnail. */
  image?: ListingImage | null;
}

function Thumbnail({ item }: { item: OrderItemLine }) {
  if (item.image === undefined) {
    return null;
  }
  if (item.image === null) {
    return <div className="ph tracking-item-photo" aria-hidden="true" />;
  }
  return (
    <BlurredImage
      className="tracking-item-photo"
      src={item.image.src}
      srcSet={item.image.srcSet}
      sizes="56px"
      alt={es.cart.photoAlt(item.productName, item.color)}
      {...(item.image.placeholder === undefined ? {} : { placeholder: item.image.placeholder })}
    />
  );
}

interface Props {
  items: OrderItemLine[];
  subtotalCents: number;
  discountCents: number;
  couponCode: string | null;
  shippingCents: number;
  totalCents: number;
}

/** An order's items and amounts, as the tracking link (UC-14) and "Mis pedidos" (UC-22) show them. */
export function OrderItemsAndAmounts({ items, subtotalCents, discountCents, couponCode, shippingCents, totalCents }: Props) {
  return (
    <>
      <ul className="tracking-items">
        {items.map((item, index) => (
          <li key={index} className={item.image === undefined ? undefined : 'with-photo'}>
            <Thumbnail item={item} />
            <span className="tracking-item-name">{item.productName}</span>
            <span className="tracking-item-detail">{es.tracking.itemDetail(item.color, item.size, item.quantity)}</span>
            <span className="tracking-item-amount">{formatMoney(item.lineTotalCents)}</span>
          </li>
        ))}
      </ul>
      <dl className="tracking-amounts">
        <div>
          <dt>{es.tracking.subtotal}</dt>
          <dd>{formatMoney(subtotalCents)}</dd>
        </div>
        {discountCents > 0 && (
          <div>
            <dt>{couponCode === null ? es.tracking.discount : es.tracking.discountWith(couponCode)}</dt>
            <dd>−{formatMoney(discountCents)}</dd>
          </div>
        )}
        <div>
          <dt>{es.tracking.shipping}</dt>
          <dd>{shippingCents === 0 ? es.tracking.free : formatMoney(shippingCents)}</dd>
        </div>
        <div className="tracking-total">
          <dt>{es.tracking.total}</dt>
          <dd>{formatMoney(totalCents)}</dd>
        </div>
      </dl>
    </>
  );
}
