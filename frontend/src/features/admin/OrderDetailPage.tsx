import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState, type SubmitEvent } from 'react';
import { Link, useParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatDateTime } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import {
  changeOrderStatus,
  correctTrackingNumber,
  fetchOrder,
  type AdminOrder,
  type OrderStatus,
  type StatusChange,
} from './ordersApi.ts';

const messages = es.admin.orderDetail;

type SettableStatus = keyof typeof messages.actions;

function isSettable(status: OrderStatus): status is SettableStatus {
  return status in messages.actions;
}

function provinceName(province: string): string {
  return province in es.provinces ? es.provinces[province as keyof typeof es.provinces] : province;
}

interface Props {
  order: AdminOrder;
  /** Reads the order again after a change, or when it changed under the administrator's eyes. */
  onChanged: () => Promise<unknown>;
}

/**
 * A button per status the order may move to. Shipping asks for the tracking number; cancelling asks to confirm and,
 * for a paid or preparing order, whether the stock goes back (UC-18).
 */
function StatusActions({ order, onChanged }: Props) {
  const id = useId();
  const [asking, setAsking] = useState<'SHIPPED' | 'CANCELLED' | null>(null);
  const [trackingNumber, setTrackingNumber] = useState('');
  const [restock, setRestock] = useState<boolean | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [saving, setSaving] = useState(false);
  const decidesStock = order.status === 'PAID' || order.status === 'PREPARING';

  async function send(change: StatusChange) {
    setNotice(null);
    setSaving(true);
    try {
      const answer = await changeOrderStatus(order.id, change);
      if (answer.status === 'changed') {
        setAsking(null);
        setTrackingNumber('');
        setRestock(null);
        setNotice({ kind: 'ok', text: messages.changed(es.tracking.statuses[answer.orderStatus]) });
        await onChanged();
      } else if (answer.status === 'stale' || answer.status === 'notFound') {
        setAsking(null);
        setNotice({ kind: 'error', text: messages.stale });
        await onChanged();
      } else {
        setNotice({ kind: 'error', text: messages.problems[answer.problem] });
      }
    } catch {
      setNotice({ kind: 'error', text: es.admin.actionError });
    } finally {
      setSaving(false);
    }
  }

  function choose(to: SettableStatus) {
    setNotice(null);
    if (to === 'SHIPPED' || to === 'CANCELLED') {
      setAsking(to);
    } else {
      void send({ from: order.status, to });
    }
  }

  function ship(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (trackingNumber.trim() === '') {
      setNotice({ kind: 'error', text: messages.problems.MISSING_TRACKING_NUMBER });
      return;
    }
    void send({ from: order.status, to: 'SHIPPED', shipmentTrackingNumber: trackingNumber });
  }

  function cancel(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!decidesStock) {
      void send({ from: order.status, to: 'CANCELLED' });
      return;
    }
    if (restock === null) {
      setNotice({ kind: 'error', text: messages.problems.RESTOCK_DECISION_REQUIRED });
      return;
    }
    void send({ from: order.status, to: 'CANCELLED', restock });
  }

  const next = order.nextStatuses.filter(isSettable);
  if (next.length === 0) {
    return <Feedback notice={notice} />;
  }

  return (
    <div className="admin-form">
      {asking === null && (
        <div className="admin-actions" role="group" aria-label={messages.changeTo}>
          {next.map((to) => (
            <button
              key={to}
              type="button"
              className={to === 'CANCELLED' ? 'btn btn-secondary danger' : 'btn'}
              disabled={saving}
              onClick={() => {
                choose(to);
              }}
            >
              {messages.actions[to]}
            </button>
          ))}
        </div>
      )}
      {asking === 'SHIPPED' && (
        <form className="admin-form" onSubmit={ship} noValidate>
          <div className="field">
            <label htmlFor={`${id}-tracking`}>{messages.shipmentLabel}</label>
            <input
              id={`${id}-tracking`}
              value={trackingNumber}
              maxLength={60}
              autoComplete="off"
              onChange={(event) => {
                setTrackingNumber(event.target.value);
              }}
            />
          </div>
          <div className="admin-actions">
            <button type="submit" className="btn" disabled={saving}>
              {messages.ship}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={saving}
              onClick={() => {
                setAsking(null);
              }}
            >
              {messages.keep}
            </button>
          </div>
        </form>
      )}
      {asking === 'CANCELLED' && (
        <form className="admin-form" onSubmit={cancel} noValidate>
          {decidesStock && (
            <fieldset className="field">
              <legend>{messages.restockQuestion}</legend>
              <label>
                <input
                  type="radio"
                  name={`${id}-restock`}
                  checked={restock === true}
                  onChange={() => {
                    setRestock(true);
                  }}
                />{' '}
                {messages.restockYes}
              </label>
              <label>
                <input
                  type="radio"
                  name={`${id}-restock`}
                  checked={restock === false}
                  onChange={() => {
                    setRestock(false);
                  }}
                />{' '}
                {messages.restockNo}
              </label>
            </fieldset>
          )}
          <div className="admin-actions">
            <button type="submit" className="btn danger" disabled={saving}>
              {messages.confirmCancel}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={saving}
              onClick={() => {
                setAsking(null);
                setRestock(null);
              }}
            >
              {messages.keep}
            </button>
          </div>
        </form>
      )}
      <Feedback notice={notice} />
    </div>
  );
}

/** While the order is shipped, its tracking number can be corrected (UC-18). */
function TrackingNumberForm({ order, onChanged }: Props) {
  const id = useId();
  const [value, setValue] = useState(order.shipmentTrackingNumber ?? '');
  const [notice, setNotice] = useState<Notice | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setNotice(null);
    setSaving(true);
    try {
      const answer = await correctTrackingNumber(order.id, value);
      if (answer.status === 'corrected') {
        setValue(answer.shipmentTrackingNumber);
        setNotice({ kind: 'ok', text: messages.correctedShipment });
        await onChanged();
      } else if (answer.status === 'invalid') {
        setNotice({ kind: 'error', text: messages.problems.INVALID_TRACKING_NUMBER });
      } else {
        setNotice({ kind: 'error', text: messages.stale });
        await onChanged();
      }
    } catch {
      setNotice({ kind: 'error', text: es.admin.actionError });
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="admin-form inline-form" onSubmit={(event) => void submit(event)} noValidate>
      <div className="field">
        <label htmlFor={`${id}-number`}>{messages.shipment}</label>
        <input
          id={`${id}-number`}
          value={value}
          maxLength={60}
          autoComplete="off"
          onChange={(event) => {
            setValue(event.target.value);
          }}
        />
      </div>
      <button type="submit" className="btn" disabled={saving}>
        {messages.correctShipment}
      </button>
      <Feedback notice={notice} />
    </form>
  );
}

function OrderView({ order, onChanged }: Props) {
  const { buyer, address } = order;
  const floorAndApartment = messages.floorAndApartment(address.floor, address.apartment);
  return (
    <>
      <title>{`${messages.title(order.number)} | ${es.admin.title}`}</title>
      <div className="admin-title-row">
        <h1>{messages.title(order.number)}</h1>
        <span className="admin-badge">{es.tracking.statuses[order.status]}</span>
      </div>
      <p className="admin-card-meta">
        {messages.placedAt(formatDateTime(order.createdAt))}
        {order.deliveredAt !== null && ` · ${messages.deliveredAt(formatDateTime(order.deliveredAt))}`}
      </p>
      {order.alerts.map((alert) => (
        <p key={alert} className="order-alert" role="alert">
          {alert === 'PAID_AFTER_CLOSING' ? messages.alerts.PAID_AFTER_CLOSING : messages.alerts[alert](buyer.phone)}
        </p>
      ))}
      <section className="admin-section" aria-labelledby="order-status">
        <h2 id="order-status">{messages.status}</h2>
        {order.status === 'SHIPPED' ? (
          <TrackingNumberForm order={order} onChanged={onChanged} />
        ) : (
          order.shipmentTrackingNumber !== null && (
            <p>
              {messages.shipment}: {order.shipmentTrackingNumber}
            </p>
          )
        )}
        <StatusActions order={order} onChanged={onChanged} />
      </section>
      <section className="admin-section" aria-labelledby="order-buyer">
        <h2 id="order-buyer">{messages.buyer}</h2>
        <p>{buyer.name}</p>
        <p>{messages.nationalId(buyer.nationalId)}</p>
        <p>
          <a href={`tel:${buyer.phone}`}>{buyer.phone}</a> · <a href={`mailto:${buyer.email}`}>{buyer.email}</a>
        </p>
      </section>
      <section className="admin-section" aria-labelledby="order-address">
        <h2 id="order-address">{messages.address}</h2>
        <p>
          {address.street} {address.number}
          {floorAndApartment !== '' && `, ${floorAndApartment}`}
        </p>
        <p>
          {address.postalCode} {address.city}, {provinceName(address.province)}
        </p>
      </section>
      <section className="admin-section" aria-labelledby="order-items">
        <h2 id="order-items">{messages.items}</h2>
        <ul className="admin-list">
          {order.lines.map((line, index) => (
            <li key={index} className="order-line">
              <span>
                <strong>{line.productName}</strong>
                <br />
                <span className="admin-card-meta">
                  {messages.lineDetail(line.color, line.size, line.quantity, formatMoney(line.unitPriceCents))}
                </span>
              </span>
              <span>{formatMoney(line.amountCents)}</span>
            </li>
          ))}
        </ul>
        <dl className="order-amounts">
          <dt>{es.tracking.subtotal}</dt>
          <dd>{formatMoney(order.subtotalCents)}</dd>
          {order.discountCents > 0 && (
            <>
              <dt>{order.couponCode === null ? es.tracking.discount : es.tracking.discountWith(order.couponCode)}</dt>
              <dd>−{formatMoney(order.discountCents)}</dd>
            </>
          )}
          <dt>{es.tracking.shipping}</dt>
          <dd>{order.shippingCents === 0 ? es.tracking.free : formatMoney(order.shippingCents)}</dd>
          <dt>{es.tracking.total}</dt>
          <dd>
            <strong>{formatMoney(order.totalCents)}</strong>
          </dd>
        </dl>
        <p className="admin-card-meta">{messages.payment(order.paymentProvider)}</p>
      </section>
    </>
  );
}

/** UC-18 at /admin/pedidos/:orderId: the order, its alerts first, and what it may move to. */
export function OrderDetailPage() {
  const { orderId = '' } = useParams();
  const queryClient = useQueryClient();
  const order = useQuery({
    queryKey: ['admin', 'orders', 'detail', orderId],
    queryFn: ({ signal }) => fetchOrder(orderId, signal),
  });

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ['admin', 'orders'] });
  }

  return (
    <>
      <Link to="/admin/pedidos" className="back-link">
        {messages.backToList}
      </Link>
      {order.isPending && <p role="status">{es.admin.loading}</p>}
      {order.isError && (
        <div role="alert">
          <p>{es.admin.loadError}</p>
          <button type="button" className="btn" onClick={() => void order.refetch()}>
            {es.admin.retry}
          </button>
        </div>
      )}
      {order.data?.status === 'notFound' && <p>{messages.notFound}</p>}
      {order.data?.status === 'found' && <OrderView order={order.data.order} onChanged={refresh} />}
    </>
  );
}
