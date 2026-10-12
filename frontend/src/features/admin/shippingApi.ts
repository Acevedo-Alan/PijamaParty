import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { ensureSignedIn, problemProperty } from './adminSession.ts';

type ShippingConfigurationResponse = components['schemas']['ShippingConfigurationResponse'];

export type Province = NonNullable<components['schemas']['ZoneResponse']['province']>;

/** The cost of each province (null: no shipping) and the free-shipping threshold (null: none), in cents (UC-12). */
export interface ShippingConfiguration {
  /** The 24 provinces, in the server's order. */
  zones: { province: Province; costCents: number | null }[];
  freeShippingThresholdCents: number | null;
}

/** The answer of replacing the costs (UC-12). */
export type ZonesAnswer =
  { status: 'saved'; configuration: ShippingConfiguration } | { status: 'invalid'; provinces: Province[] };

/** The answer of setting the free-shipping threshold (UC-12). */
export type ThresholdAnswer = { status: 'saved'; configuration: ShippingConfiguration } | { status: 'invalid' };

function toConfiguration(body: ShippingConfigurationResponse): ShippingConfiguration {
  const { zones } = body;
  if (zones === undefined) {
    throw new Error('Incomplete shipping configuration');
  }
  return {
    zones: zones.map(({ province, costCents }) => {
      if (province === undefined) {
        throw new Error('Incomplete shipping zone');
      }
      return { province, costCents: costCents ?? null };
    }),
    freeShippingThresholdCents: body.freeShippingThresholdCents ?? null,
  };
}

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

export async function fetchShipping(signal?: AbortSignal): Promise<ShippingConfiguration> {
  const { data, response } = await api.GET('/api/admin/shipping', {
    signal: signal ?? null,
  });
  ensureSignedIn(response);
  if (data === undefined) {
    throw failed('Shipping configuration', response);
  }
  return toConfiguration(data);
}

/** Replaces every cost: the provinces left out have no shipping. */
export async function replaceZones(zones: { province: Province; costCents: number }[]): Promise<ZonesAnswer> {
  const { data, error, response } = await api.PUT('/api/admin/shipping/zones', {
    body: { zones },
  });
  ensureSignedIn(response);
  if (response.status === 422) {
    const provinces = problemProperty(error, 'provinces');
    return {
      status: 'invalid',
      provinces: Array.isArray(provinces)
        ? zones.map((zone) => zone.province).filter((province) => provinces.includes(province))
        : [],
    };
  }
  if (data === undefined) {
    throw failed('Shipping costs', response);
  }
  return { status: 'saved', configuration: toConfiguration(data) };
}

/** Sets the free-shipping threshold, or removes it with null. */
export async function setFreeShippingThreshold(thresholdCents: number | null): Promise<ThresholdAnswer> {
  const { data, response } = await api.PUT('/api/admin/shipping/free-shipping', { body: { thresholdCents } });
  ensureSignedIn(response);
  if (response.status === 422) {
    return { status: 'invalid' };
  }
  if (data === undefined) {
    throw failed('Free-shipping threshold', response);
  }
  return { status: 'saved', configuration: toConfiguration(data) };
}
