import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import './orders.css';

export interface OrderItemLine {
  productName: string;
  size: string;
  color: string;
  quantity: number;
  lineTotalCents: number;
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
          <li key={index}>
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
