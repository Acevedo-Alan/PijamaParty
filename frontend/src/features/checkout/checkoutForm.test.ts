import { describe, expect, it } from 'vitest';
import {
  EMPTY_FORM,
  invalidFields,
  toFormFields,
  toPlaceOrderRequest,
  withAddress,
  withContactOf,
  withoutAddress,
  type ShippingForm,
} from './checkoutForm.ts';

const VALID: ShippingForm = {
  name: 'Ana Pérez',
  email: 'ana@example.com',
  phone: '+54 (342) 456-7890',
  nationalId: '30.123.456',
  street: 'San Martín',
  number: '1234',
  floor: '',
  apartment: '',
  city: 'Santa Fe',
  province: 'SANTA_FE',
  postalCode: 's3000abc',
};

describe('checkout form rules (UC-13)', () => {
  it('given every field as UC-13 asks, when validated, then none fails', () => {
    expect(invalidFields(VALID)).toEqual([]);
  });

  it('given an empty form, when validated, then every required field fails in the order of the form', () => {
    expect(invalidFields(EMPTY_FORM)).toEqual([
      'name',
      'email',
      'phone',
      'nationalId',
      'street',
      'number',
      'city',
      'province',
      'postalCode',
    ]);
  });

  it.each(['3012345', '30123456', '30.123.456', ' 30 123 456 '])('given the DNI %j, when validated, then it passes', (nationalId) => {
    expect(invalidFields({ ...VALID, nationalId })).toEqual([]);
  });

  it.each(['301234', '301234567', '30-123-456', '30a23456'])(
    'given a national ID with an invalid format (%j), when validated, then it is rejected',
    (nationalId) => {
      expect(invalidFields({ ...VALID, nationalId })).toEqual(['nationalId']);
    },
  );

  it.each([['1234567'], ['1234567890123456'], ['342 abc 4567']])('given the phone %j, when validated, then it fails', (phone) => {
    expect(invalidFields({ ...VALID, phone })).toEqual(['phone']);
  });

  it.each(['3000', 'S3000ABC', ' s3000abc '])('given the postal code %j, when validated, then it passes', (postalCode) => {
    expect(invalidFields({ ...VALID, postalCode })).toEqual([]);
  });

  it.each(['300', '30000', 'S3000AB', '3000ABC'])('given the postal code %j, when validated, then it fails', (postalCode) => {
    expect(invalidFields({ ...VALID, postalCode })).toEqual(['postalCode']);
  });

  it.each(['ana', 'ana@', '@example.com', 'ana maria@example.com', 'ana@example@com'])(
    'given the email %j, when validated, then it fails',
    (email) => {
      expect(invalidFields({ ...VALID, email })).toEqual(['email']);
    },
  );

  it('given texts over their limits, when validated, then each one fails', () => {
    const form = {
      ...VALID,
      name: 'a'.repeat(101),
      number: '12345678901',
      floor: '12345678901',
      apartment: '12345678901',
      city: 'c'.repeat(81),
    };

    expect(invalidFields(form)).toEqual(['name', 'number', 'floor', 'apartment', 'city']);
  });

  it('given a name of 100 characters with emojis, when validated, then they count as the server counts them', () => {
    expect(invalidFields({ ...VALID, name: '🌙'.repeat(100) })).toEqual([]);
  });

  it('given the fields of INVALID_BUYER_DATA, when read, then only the form fields are kept, in its order', () => {
    expect(toFormFields(['postalCode', 'unknown', 'name', 3])).toEqual(['name', 'postalCode']);
  });
});

describe('checkout request (UC-14)', () => {
  it('given a form without floor or apartment, when sent, then it goes as typed and without them', () => {
    expect(toPlaceOrderRequest({ ...VALID, floor: '  ' }, 'SANTA_FE', 'MERCADOPAGO', true, false)).toEqual({
      buyer: { name: 'Ana Pérez', email: 'ana@example.com', phone: '+54 (342) 456-7890', nationalId: '30.123.456' },
      address: { street: 'San Martín', number: '1234', city: 'Santa Fe', province: 'SANTA_FE', postalCode: 's3000abc' },
      paymentProvider: 'MERCADOPAGO',
      acceptedTerms: true,
    });
  });

  it('given a floor and an apartment, when sent, then both go', () => {
    const request = toPlaceOrderRequest({ ...VALID, floor: '2', apartment: 'B' }, 'SANTA_FE', 'MERCADOPAGO', true, false);

    expect(request.address).toEqual(expect.objectContaining({ floor: '2', apartment: 'B' }));
  });
});

describe('prefilled checkout (UC-21)', () => {
  const customer = { id: 'ana', email: 'ana@example.com', name: 'Ana Pérez', phone: '3424567890' };
  const address = {
    id: 'home',
    street: 'Rivadavia',
    number: '550',
    floor: null,
    apartment: 'B',
    city: 'Rosario',
    province: 'SANTA_FE' as const,
    postalCode: 'S2000ABC',
    primary: true,
  };

  it('given fields already typed, when the customer prefills them, then only the empty ones take its data', () => {
    expect(withContactOf({ ...EMPTY_FORM, email: 'otra@example.com' }, customer)).toEqual({
      ...EMPTY_FORM,
      name: 'Ana Pérez',
      email: 'otra@example.com',
      phone: '3424567890',
    });
    expect(withContactOf(EMPTY_FORM, { ...customer, phone: null }).phone).toBe('');
  });

  it('given a saved address, when chosen and then left for another, then it fills the address and empties it again', () => {
    const chosen = withAddress(VALID, address);

    expect(chosen).toEqual({
      ...VALID,
      street: 'Rivadavia',
      number: '550',
      floor: '',
      apartment: 'B',
      city: 'Rosario',
      postalCode: 'S2000ABC',
    });
    expect(withoutAddress(chosen)).toEqual({
      ...EMPTY_FORM,
      name: VALID.name,
      email: VALID.email,
      phone: VALID.phone,
      nationalId: VALID.nationalId,
    });
  });

  it('given the address to save, when sent, then the request asks for it', () => {
    expect(toPlaceOrderRequest(VALID, 'SANTA_FE', 'MERCADOPAGO', true, true)).toEqual(
      expect.objectContaining({ saveAddress: true }),
    );
  });
});
