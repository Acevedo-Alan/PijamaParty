import type { ReactNode } from 'react';
import { es } from '../../i18n/es.ts';
import { formatDate } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { isPaid, PAID_STEPS, type PaidStatus } from '../orders/OrderConfirmation.tsx';
import type { OrderStatus } from '../orders/orderTrackingApi.ts';
import { ORDERS_PATH } from './AccountLink.tsx';
import { Group } from './Group.tsx';
import type { MyOrderSummary } from './myOrdersApi.ts';

const messages = es.account.orders;

/** The dot before a status: rose on its way, green delivered, peach awaiting payment, grey closed (T-E7-64). */
function StatusDot({ status }: { status: OrderStatus }) {
  return <span className={`order-dot order-dot-${status.toLowerCase()}`} aria-hidden="true" />;
}

/** "Pedido #1003" and its total, then the status and the date: "Enviado · hecho el 10/11/2026". */
function Header({ order }: { order: MyOrderSummary }) {
  return (
    <div className="order-card-header">
      <h2 className="order-card-number">{es.tracking.title(order.number)}</h2>
      <p className="order-card-total">{formatMoney(order.totalCents)}</p>
      <p className="order-card-status">
        <StatusDot status={order.status} />
        {`${es.tracking.statuses[order.status]} · ${messages.placedOnShort(formatDate(order.createdAt))}`}
      </p>
    </div>
  );
}

/** Which of the four steps a paid order is at, from 1. */
export function stepOf(status: PaidStatus): number {
  return PAID_STEPS.indexOf(status) + 1;
}

/**
 * The thin bar of the four paid steps (Pagado, En preparación, Enviado, Entregado): the reached segments filled, in
 * rose on the way and green once delivered, then "Paso n de 4" or, at the end, "Entregado". `caption` replaces that
 * line (the detail adds the carrier).
 */
function Progress({ status, caption }: { status: PaidStatus; caption?: string | undefined }) {
  const step = stepOf(status);
  const delivered = status === 'DELIVERED';
  return (
    <div className={delivered ? 'order-progress is-delivered' : 'order-progress'}>
      <ol className="order-progress-bar" aria-label={es.tracking.paid.progress}>
        {PAID_STEPS.map((each, index) => (
          <li
            key={each}
            className={index < step ? 'is-reached' : undefined}
            aria-current={index === step - 1 ? 'step' : undefined}
          >
            <span className="visually-hidden">{es.tracking.statuses[each]}</span>
          </li>
        ))}
      </ol>
      <p className="order-progress-caption">
        {caption ?? (delivered ? es.tracking.statuses.DELIVERED : messages.step(step))}
      </p>
    </div>
  );
}

function detailPath(order: MyOrderSummary): string {
  return `${ORDERS_PATH}/${order.id}`;
}

function Card({ order, children }: { order: MyOrderSummary; children?: ReactNode }) {
  return (
    <article className="order-card">
      <Group.Card>
        <div className="group-row order-card-body">
          <Header order={order} />
          {children}
        </div>
        {/* "Seguir envío" while it travels: the detail has the tracking number and Correo's page. */}
        {order.status === 'SHIPPED' && (
          <Group.ActionLink
            to={detailPath(order)}
            icon="truck"
            aria-label={`${messages.trackShipment} · ${es.tracking.title(order.number)}`}
          >
            {messages.trackShipment}
          </Group.ActionLink>
        )}
        <Group.ActionLink
          to={detailPath(order)}
          icon="parcel"
          aria-label={`${messages.viewDetail} · ${es.tracking.title(order.number)}`}
        >
          {messages.viewDetail}
        </Group.ActionLink>
      </Group.Card>
    </article>
  );
}

/** Paid, in preparation, shipped or delivered: with its bar. */
export function PaidOrderCard({ order, status }: { order: MyOrderSummary; status: PaidStatus }) {
  return (
    <Card order={order}>
      <Progress status={status} />
    </Card>
  );
}

/** Awaiting payment: no bar; it is paid from the email's link (UC-22). */
export function PendingOrderCard({ order }: { order: MyOrderSummary }) {
  return (
    <Card order={order}>
      <p className="order-card-note">{messages.payFromEmail}</p>
    </Card>
  );
}

/** Cancelled or expired: no bar. */
export function ClosedOrderCard({ order }: { order: MyOrderSummary }) {
  return <Card order={order} />;
}

/** The card of the order's family of status. */
export function MyOrderCard({ order }: { order: MyOrderSummary }) {
  const { status } = order;
  if (isPaid(status)) {
    return <PaidOrderCard order={order} status={status} />;
  }
  return status === 'AWAITING_PAYMENT' ? <PendingOrderCard order={order} /> : <ClosedOrderCard order={order} />;
}

export const OrderCard = { Header, Progress, StatusDot };
