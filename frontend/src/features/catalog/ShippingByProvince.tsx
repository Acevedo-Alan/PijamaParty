import { useId, useState } from 'react';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import type { Province } from '../help/shippingRatesApi.ts';
import { useShippingRates } from '../help/useShippingRates.ts';

const PROVINCES = Object.keys(es.provinces) as Province[];

/**
 * D-07: the shipping cost of the province the visitor picks, the same the checkout charges (UC-12). Shipping is
 * priced per province, so there is no postal code to type. Without the costs there is nothing to show.
 */
export function ShippingByProvince() {
  const rates = useShippingRates();
  const [province, setProvince] = useState<Province | ''>('');
  const selectId = useId();

  if (rates.data === undefined) {
    return null;
  }
  const zone = rates.data.zones.find((rate) => rate.province === province);

  return (
    <div className="shipping-estimator">
      <label htmlFor={selectId}>{es.product.shipping}</label>
      <select
        id={selectId}
        value={province}
        onChange={(event) => {
          setProvince(event.target.value as Province | '');
        }}
      >
        <option value="">{es.product.chooseProvince}</option>
        {PROVINCES.map((code) => (
          <option key={code} value={code}>
            {es.provinces[code]}
          </option>
        ))}
      </select>
      <p className="shipping-result" aria-live="polite">
        {province !== '' &&
          (zone === undefined
            ? es.product.noShippingTo
            : zone.costCents === 0
              ? es.product.freeShippingTo(es.provinces[province])
              : es.product.shippingTo(es.provinces[province], formatMoney(zone.costCents)))}
      </p>
    </div>
  );
}
