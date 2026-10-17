import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';
import { problemProperty } from '../admin/adminSession.ts';
import { isProvince, type Province } from '../checkout/checkoutForm.ts';

type SavedAddressRequest = components['schemas']['SavedAddressRequest'];
type SavedAddressResponse = components['schemas']['SavedAddressResponse'];

/** The query key of the address book; every change refetches it, and signing out forgets it. */
export const ADDRESSES_KEY = ['account', 'addresses'] as const;

/** An address of the customer's book, as GET /api/account/addresses lists it (UC-20, T-E4-05). */
export interface SavedAddress {
  id: string;
  street: string;
  number: string;
  floor: string | null;
  apartment: string | null;
  city: string;
  province: Province;
  postalCode: string;
  primary: boolean;
}

/** The fields an address goes with, as typed: the server trims and normalizes them (RNF-28). */
export type AddressFields = Omit<SavedAddressRequest, 'primary'>;

/** The fields of INVALID_ADDRESS, in the request's own names. */
export type AddressField = 'street' | 'number' | 'floor' | 'apartment' | 'city' | 'postalCode';

const ADDRESS_FIELDS: readonly AddressField[] = ['street', 'number', 'floor', 'apartment', 'city', 'postalCode'];

/** GET /api/account/addresses: null when nobody is signed in as a customer. */
export type AddressList = SavedAddress[] | null;

/** POST and PUT of an address. */
export type SaveAddressAnswer =
  | { status: 'saved'; address: SavedAddress }
  | { status: 'invalid'; fields: AddressField[] }
  | { status: 'full' }
  | { status: 'notFound' }
  | { status: 'signedOut' };

/** DELETE of an address. */
export type DeleteAddressAnswer = { status: 'deleted' } | { status: 'notFound' } | { status: 'signedOut' };

function failed(action: string, response: Response): Error {
  return new Error(`${action} failed with status ${String(response.status)}`);
}

function toAddress(data: SavedAddressResponse | undefined): SavedAddress | null {
  if (
    data?.id === undefined ||
    data.street === undefined ||
    data.number === undefined ||
    data.city === undefined ||
    data.province === undefined ||
    !isProvince(data.province) ||
    data.postalCode === undefined ||
    data.primary === undefined
  ) {
    return null;
  }
  return {
    id: data.id,
    street: data.street,
    number: data.number,
    floor: data.floor ?? null,
    apartment: data.apartment ?? null,
    city: data.city,
    province: data.province,
    postalCode: data.postalCode,
    primary: data.primary,
  };
}

function toSaved(action: string, response: Response, data: SavedAddressResponse | undefined): SaveAddressAnswer {
  const address = toAddress(data);
  if (address === null) {
    throw failed(action, response);
  }
  return { status: 'saved', address };
}

/** INVALID_ADDRESS names its fields; ADDRESS_BOOK_FULL only comes from adding. */
function unprocessable(action: string, response: Response, error: unknown): SaveAddressAnswer {
  const problem = problemProperty(error, 'problem');
  if (problem === 'ADDRESS_BOOK_FULL') {
    return { status: 'full' };
  }
  const names = problemProperty(error, 'fields');
  if (problem !== 'INVALID_ADDRESS' || !Array.isArray(names)) {
    throw failed(action, response);
  }
  return { status: 'invalid', fields: ADDRESS_FIELDS.filter((field) => names.includes(field)) };
}

export async function fetchAddresses(signal?: AbortSignal): Promise<AddressList> {
  const { data, response } = await api.GET('/api/account/addresses', { signal: signal ?? null });
  if (response.status === 401 || response.status === 403) {
    return null;
  }
  if (response.status !== 200 || !Array.isArray(data)) {
    throw failed('Address list', response);
  }
  return data.map((each) => {
    const address = toAddress(each);
    if (address === null) {
      throw failed('Address list', response);
    }
    return address;
  });
}

export async function addAddress(fields: AddressFields, primary: boolean): Promise<SaveAddressAnswer> {
  const { data, error, response } = await api.POST('/api/account/addresses', { body: { ...fields, primary } });
  switch (response.status) {
    case 201:
      return toSaved('Adding an address', response, data);
    case 401:
      return { status: 'signedOut' };
    case 422:
      return unprocessable('Adding an address', response, error);
    default:
      throw failed('Adding an address', response);
  }
}

/** Replaces every field; primary true makes it the primary, false leaves it as it was. */
export async function editAddress(id: string, fields: AddressFields, primary: boolean): Promise<SaveAddressAnswer> {
  const { data, error, response } = await api.PUT('/api/account/addresses/{id}', {
    params: { path: { id } },
    body: { ...fields, primary },
  });
  switch (response.status) {
    case 200:
      return toSaved('Editing an address', response, data);
    case 401:
      return { status: 'signedOut' };
    case 404:
      return { status: 'notFound' };
    case 422:
      return unprocessable('Editing an address', response, error);
    default:
      throw failed('Editing an address', response);
  }
}

export async function deleteAddress(id: string): Promise<DeleteAddressAnswer> {
  const { response } = await api.DELETE('/api/account/addresses/{id}', { params: { path: { id } } });
  switch (response.status) {
    case 204:
      return { status: 'deleted' };
    case 401:
      return { status: 'signedOut' };
    case 404:
      return { status: 'notFound' };
    default:
      throw failed('Deleting an address', response);
  }
}
