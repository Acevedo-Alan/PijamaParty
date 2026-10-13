import type { components } from '../../api/schema.d.ts';

type PlaceOrderRequest = components['schemas']['PlaceOrderRequest'];
export type Province = PlaceOrderRequest['address']['province'];

/** The 24 jurisdictions in the order of the API's enum (UC-12); the select shows their Spanish names. */
export const PROVINCES: readonly Province[] = [
  'BUENOS_AIRES',
  'CIUDAD_DE_BUENOS_AIRES',
  'CATAMARCA',
  'CHACO',
  'CHUBUT',
  'CORDOBA',
  'CORRIENTES',
  'ENTRE_RIOS',
  'FORMOSA',
  'JUJUY',
  'LA_PAMPA',
  'LA_RIOJA',
  'MENDOZA',
  'MISIONES',
  'NEUQUEN',
  'RIO_NEGRO',
  'SALTA',
  'SAN_JUAN',
  'SAN_LUIS',
  'SANTA_CRUZ',
  'SANTA_FE',
  'SANTIAGO_DEL_ESTERO',
  'TIERRA_DEL_FUEGO',
  'TUCUMAN',
];

export function isProvince(value: string): value is Province {
  return (PROVINCES as readonly string[]).includes(value);
}

/** Step 1 as typed. The server normalizes and validates it again (RNF-28), so it goes as typed. */
export interface ShippingForm {
  name: string;
  email: string;
  phone: string;
  nationalId: string;
  street: string;
  number: string;
  floor: string;
  apartment: string;
  city: string;
  /** Empty until the buyer chooses one. */
  province: Province | '';
  postalCode: string;
}

export type FormField = keyof ShippingForm;

export const EMPTY_FORM: ShippingForm = {
  name: '',
  email: '',
  phone: '',
  nationalId: '',
  street: '',
  number: '',
  floor: '',
  apartment: '',
  city: '',
  province: '',
  postalCode: '',
};

/** The fields in the order of the form, so the first failing one takes the focus. */
export const FORM_FIELDS: readonly FormField[] = [
  'name',
  'email',
  'phone',
  'nationalId',
  'street',
  'number',
  'floor',
  'apartment',
  'city',
  'province',
  'postalCode',
];

// The rules of UC-13, as ordering.domain.CheckoutForm applies them.
const EMAIL = /^[\x21-\x3F\x41-\x7E]+@[\x21-\x3F\x41-\x7E]+$/;
const PHONE_SEPARATORS = /[\s+\-()]/g;
const PHONE = /^[0-9]{8,15}$/;
const NATIONAL_ID_SEPARATORS = /[.\s]/g;
const NATIONAL_ID = /^[0-9]{7,8}$/;
const POSTAL_CODE = /^(?:[0-9]{4}|[A-Z][0-9]{4}[A-Z]{3})$/;

/** Length in code points, as the server counts it. */
function lengthOf(value: string): number {
  return Array.from(value.trim()).length;
}

function within(value: string, min: number, max: number): boolean {
  const length = lengthOf(value);
  return length >= min && length <= max;
}

const RULES: Record<FormField, (value: string) => boolean> = {
  name: (value) => within(value, 1, 100),
  email: (value) => {
    const email = value.trim().toLowerCase();
    return email.length <= 254 && EMAIL.test(email);
  },
  phone: (value) => PHONE.test(value.replace(PHONE_SEPARATORS, '')),
  nationalId: (value) => NATIONAL_ID.test(value.replace(NATIONAL_ID_SEPARATORS, '')),
  street: (value) => within(value, 1, 100),
  number: (value) => within(value, 1, 10),
  floor: (value) => within(value, 0, 10),
  apartment: (value) => within(value, 0, 10),
  city: (value) => within(value, 1, 80),
  province: (value) => isProvince(value),
  postalCode: (value) => POSTAL_CODE.test(value.trim().toUpperCase()),
};

/** The fields that break a rule of UC-13, in the order of the form; none when step 1 can go on. */
export function invalidFields(form: ShippingForm): FormField[] {
  return FORM_FIELDS.filter((field) => !RULES[field](form[field]));
}

/** The names of INVALID_BUYER_DATA's fields are the form's own; anything else is ignored. */
export function toFormFields(names: readonly unknown[]): FormField[] {
  return FORM_FIELDS.filter((field) => names.includes(field));
}

export type PaymentProvider = PlaceOrderRequest['paymentProvider'];

/** The body of POST /api/checkout; optional floor and apartment are left out when blank. */
export function toPlaceOrderRequest(
  form: ShippingForm,
  province: Province,
  paymentProvider: PaymentProvider,
  acceptedTerms: boolean,
): PlaceOrderRequest {
  const { name, email, phone, nationalId, street, number, floor, apartment, city, postalCode } = form;
  return {
    buyer: { name, email, phone, nationalId },
    address: {
      street,
      number,
      ...(floor.trim() === '' ? {} : { floor }),
      ...(apartment.trim() === '' ? {} : { apartment }),
      city,
      province,
      postalCode,
    },
    paymentProvider,
    acceptedTerms,
  };
}
