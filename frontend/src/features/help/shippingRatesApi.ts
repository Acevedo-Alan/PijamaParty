import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';

export type Province = NonNullable<components['schemas']['ZoneRateResponse']['province']>;

/** The shipping costs a visitor sees before buying (RNF-31.6), in cents. */
export interface ShippingRates {
  /** Only the provinces with shipping, in the server's order; a cost of 0 is a free shipping. */
  zones: { province: Province; costCents: number }[];
  /** Null: no free shipping. */
  freeShippingThresholdCents: number | null;
}

/** GET /api/shipping/rates (T-E5-12): the same costs and threshold the checkout charges (UC-12). */
export async function fetchShippingRates(signal?: AbortSignal): Promise<ShippingRates> {
  const { data, response } = await api.GET('/api/shipping/rates', { signal: signal ?? null });
  if (data?.zones === undefined) {
    throw new Error(`The shipping rates failed with status ${String(response.status)}`);
  }
  return {
    zones: data.zones.map(({ province, costCents }) => {
      if (province === undefined || costCents === undefined) {
        throw new Error('Incomplete shipping rate');
      }
      return { province, costCents };
    }),
    freeShippingThresholdCents: data.freeShippingThresholdCents ?? null,
  };
}
