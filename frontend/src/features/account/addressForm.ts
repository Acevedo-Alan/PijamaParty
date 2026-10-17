import { isProvince, type Province } from '../checkout/checkoutForm.ts';
import type { AddressField, AddressFields, SavedAddress } from './addressBookApi.ts';

/** An address of the book as typed (UC-20). The server normalizes and validates it again (RNF-28). */
export interface AddressFormValues {
  street: string;
  number: string;
  floor: string;
  apartment: string;
  city: string;
  /** Empty until the customer chooses one. */
  province: Province | '';
  postalCode: string;
}

export type AddressFormField = keyof AddressFormValues;

export const EMPTY_ADDRESS: AddressFormValues = {
  street: '',
  number: '',
  floor: '',
  apartment: '',
  city: '',
  province: '',
  postalCode: '',
};

/** The fields in the order of the form, so the first failing one takes the focus. */
export const ADDRESS_FORM_FIELDS: readonly AddressFormField[] = [
  'street',
  'number',
  'floor',
  'apartment',
  'city',
  'province',
  'postalCode',
];

// The rules of the order's address (UC-13), as accounts.domain.AddressForm applies them.
const POSTAL_CODE = /^(?:[0-9]{4}|[A-Z][0-9]{4}[A-Z]{3})$/;

/** Length in code points, as the server counts it. */
function within(value: string, min: number, max: number): boolean {
  const length = Array.from(value.trim()).length;
  return length >= min && length <= max;
}

const RULES: Record<AddressFormField, (value: string) => boolean> = {
  street: (value) => within(value, 1, 100),
  number: (value) => within(value, 1, 10),
  floor: (value) => within(value, 0, 10),
  apartment: (value) => within(value, 0, 10),
  city: (value) => within(value, 1, 80),
  province: (value) => isProvince(value),
  postalCode: (value) => POSTAL_CODE.test(value.trim().toUpperCase()),
};

/** The fields that break a rule, in the order of the form; none when the address can be sent. */
export function invalidAddressFields(form: AddressFormValues): AddressFormField[] {
  return ADDRESS_FORM_FIELDS.filter((field) => !RULES[field](form[field]));
}

/** The fields of INVALID_ADDRESS are the form's own. */
export function toFormFields(fields: readonly AddressField[]): AddressFormField[] {
  return ADDRESS_FORM_FIELDS.filter((field) => (fields as readonly string[]).includes(field));
}

export function toAddressForm(address: SavedAddress): AddressFormValues {
  return {
    street: address.street,
    number: address.number,
    floor: address.floor ?? '',
    apartment: address.apartment ?? '',
    city: address.city,
    province: address.province,
    postalCode: address.postalCode,
  };
}

/** The body of the address; optional floor and apartment are left out when blank. */
export function toAddressFields(form: AddressFormValues, province: Province): AddressFields {
  const { street, number, floor, apartment, city, postalCode } = form;
  return {
    street,
    number,
    ...(floor.trim() === '' ? {} : { floor }),
    ...(apartment.trim() === '' ? {} : { apartment }),
    city,
    province,
    postalCode,
  };
}

/** The fields of a saved address, to send it again unchanged (marking it primary). */
export function savedFields(address: SavedAddress): AddressFields {
  return toAddressFields(toAddressForm(address), address.province);
}
