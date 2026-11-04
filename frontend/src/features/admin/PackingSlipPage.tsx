import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { Link, useParams } from 'react-router';
import { Button } from '../../components/ui/Button.tsx';
import { es } from '../../i18n/es.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatDate } from '../../i18n/formatDate.ts';
import { fetchOrder, hasPackingSlip, type AdminOrder } from './ordersApi.ts';
import './packingSlip.css';

const messages = esAdmin.packingSlip;
const detail = esAdmin.orderDetail;

function provinceName(province: string): string {
  return province in es.provinces ? es.provinces[province as keyof typeof es.provinces] : province;
}

/** What goes inside the parcel (D-E8-10): who it is for and what it holds, never an amount. */
function Slip({ order }: { order: AdminOrder }) {
  const { buyer, address } = order;
  const floorAndApartment = detail.floorAndApartment(address.floor, address.apartment);
  return (
    <article className="packing-slip" aria-labelledby="slip-title">
      <header className="packing-slip-head">
        <p className="packing-slip-store">{es.store.name}</p>
        <h1 id="slip-title">{detail.title(order.number)}</h1>
        <p>{messages.placedOn(formatDate(order.createdAt))}</p>
      </header>
      <section aria-labelledby="slip-recipient">
        <h2 id="slip-recipient">{messages.recipient}</h2>
        <p className="packing-slip-name">{buyer.name}</p>
        <p>{buyer.phone}</p>
        <p>
          {address.street} {address.number}
          {floorAndApartment !== '' && `, ${floorAndApartment}`}
        </p>
        <p>
          {address.postalCode} {address.city}, {provinceName(address.province)}
        </p>
        {order.shipmentTrackingNumber !== null && (
          <p>
            {messages.shipment}: {order.shipmentTrackingNumber}
          </p>
        )}
      </section>
      <section aria-labelledby="slip-items">
        <h2 id="slip-items">{messages.items}</h2>
        <table className="packing-slip-lines">
          <thead>
            <tr>
              <th scope="col">{messages.product}</th>
              <th scope="col">{messages.size}</th>
              <th scope="col">{messages.color}</th>
              <th scope="col" className="packing-slip-quantity">
                {messages.quantity}
              </th>
              <th scope="col">{messages.sku}</th>
            </tr>
          </thead>
          <tbody>
            {order.lines.map((line) => (
              <tr key={line.sku}>
                <td>{line.productName}</td>
                <td>{line.size}</td>
                <td>{line.color}</td>
                <td className="packing-slip-quantity">{line.quantity}</td>
                <td className="packing-slip-sku">{line.sku}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </article>
  );
}

/**
 * UC-18 (T-E8-07) at /admin/pedidos/:orderId/remito: the packing slip of a paid order, for printing. The print dialog
 * opens once the order is shown; printing changes nothing, so it records no activity.
 */
export function PackingSlipPage() {
  const { orderId = '' } = useParams();
  const order = useQuery({
    queryKey: ['admin', 'orders', 'detail', orderId],
    queryFn: ({ signal }) => fetchOrder(orderId, signal),
  });
  const found = order.data?.status === 'found' ? order.data.order : null;
  const printable = found !== null && hasPackingSlip(found.status);
  const printed = useRef(false);

  useEffect(() => {
    if (printable && !printed.current) {
      printed.current = true;
      window.print();
    }
  }, [printable]);

  return (
    <>
      {found !== null && <title>{`${messages.title(found.number)} | ${esAdmin.title}`}</title>}
      <div className="packing-slip-actions">
        <Link to={`/admin/pedidos/${orderId}`} className="back-link">
          {messages.backToOrder}
        </Link>
        {printable && (
          <Button
            variant="primary"
            onClick={() => {
              window.print();
            }}
          >
            {messages.printAgain}
          </Button>
        )}
      </div>
      {order.isPending && <p role="status">{esAdmin.loading}</p>}
      {order.isError && (
        <div role="alert">
          <p>{esAdmin.loadError}</p>
          <Button onClick={() => void order.refetch()}>{esAdmin.retry}</Button>
        </div>
      )}
      {order.data?.status === 'notFound' && <p>{detail.notFound}</p>}
      {found !== null && !printable && <p>{messages.notPrintable}</p>}
      {printable && <Slip order={found} />}
    </>
  );
}
