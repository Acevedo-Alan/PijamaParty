import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type SyntheticEvent } from 'react';
import { Navigate } from 'react-router';
import { es } from '../../i18n/es.ts';
import { PROVINCES } from '../checkout/checkoutForm.ts';
import { SIGN_IN_PATH } from './AccountLink.tsx';
import {
  ADDRESSES_KEY,
  addAddress,
  deleteAddress,
  editAddress,
  fetchAddresses,
  type SaveAddressAnswer,
  type SavedAddress,
} from './addressBookApi.ts';
import {
  EMPTY_ADDRESS,
  invalidAddressFields,
  savedFields,
  toAddressFields,
  toAddressForm,
  toFormFields,
  type AddressFormField,
  type AddressFormValues,
} from './addressForm.ts';
import { CUSTOMER_KEY, useCustomer } from './useCustomer.ts';
import './account.css';

const messages = es.account.addresses;

/** The most addresses a book holds (UC-20); the server refuses an 11th. */
const MAX_ADDRESSES = 10;

/** "San Martín 1234, piso 3, depto. B": how the list and the buttons name an address. */
function addressLine(address: SavedAddress): string {
  const parts = [`${address.street} ${address.number}`];
  if (address.floor !== null) {
    parts.push(es.checkout.floorPart(address.floor));
  }
  if (address.apartment !== null) {
    parts.push(es.checkout.apartmentPart(address.apartment));
  }
  return parts.join(', ');
}

type Editing = { kind: 'new' } | { kind: 'edit'; address: SavedAddress };

/**
 * UC-20 (T-E4-05): the signed-in customer's address book at /cuenta/direcciones. It adds, edits, deletes after a
 * confirmation and marks one address as primary; a visitor who is not signed in goes to sign in.
 */
export function AddressBookPage() {
  const customer = useCustomer();
  const queryClient = useQueryClient();
  const addresses = useQuery({
    queryKey: ADDRESSES_KEY,
    queryFn: ({ signal }) => fetchAddresses(signal),
    enabled: customer !== undefined && customer !== null,
  });
  const [editing, setEditing] = useState<Editing | null>(null);
  const [confirming, setConfirming] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const noticeRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    if (notice !== null) {
      noticeRef.current?.focus();
    }
  }, [notice]);

  function signedOut() {
    queryClient.setQueryData(CUSTOMER_KEY, null);
  }

  function done(message: string) {
    setEditing(null);
    setConfirming(null);
    setNotice(message);
    void queryClient.invalidateQueries({ queryKey: ADDRESSES_KEY });
  }

  const makePrimary = useMutation({
    mutationFn: (address: SavedAddress) => editAddress(address.id, savedFields(address), true),
    onSuccess: (answer) => {
      if (answer.status === 'signedOut') {
        signedOut();
      } else {
        done(answer.status === 'saved' ? messages.primaryChanged : messages.notFound);
      }
    },
    onError: () => {
      setNotice(messages.error);
    },
  });

  const remove = useMutation({
    mutationFn: deleteAddress,
    onSuccess: (answer) => {
      if (answer.status === 'signedOut') {
        signedOut();
      } else {
        done(answer.status === 'deleted' ? messages.removed : messages.notFound);
      }
    },
    onError: () => {
      setNotice(messages.removeError);
    },
  });

  if (customer === null || addresses.data === null) {
    return <Navigate to={SIGN_IN_PATH} replace />;
  }

  const list = addresses.data;
  const busy = makePrimary.isPending || remove.isPending;

  return (
    <main className="wrap account-page address-book">
      <title>{`${messages.title} | ${es.store.name}`}</title>
      <h1>{messages.title}</h1>
      <p className="account-intro">{messages.intro}</p>
      <p className="address-notice" ref={noticeRef} tabIndex={-1} role="status">
        {notice}
      </p>
      {addresses.isError && (
        <div className="address-load-error" role="alert">
          <p>{messages.loadError}</p>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => {
              void addresses.refetch();
            }}
          >
            {messages.retry}
          </button>
        </div>
      )}
      {list === undefined ? (
        !addresses.isError && <p className="account-intro">{messages.loading}</p>
      ) : (
        <>
          {list.length === 0 && editing === null && <p className="account-intro">{messages.empty}</p>}
          <ul className="address-list">
            {list.map((address) =>
              editing?.kind === 'edit' && editing.address.id === address.id ? (
                <li key={address.id} className="address-card">
                  <AddressEditor
                    editing={editing}
                    canChoosePrimary={!address.primary}
                    onCancel={() => {
                      setEditing(null);
                    }}
                    onDone={done}
                    onSignedOut={signedOut}
                  />
                </li>
              ) : (
                <li key={address.id} className="address-card">
                  <p className="address-line">
                    {addressLine(address)}
                    {address.primary && <span className="address-primary">{messages.primary}</span>}
                  </p>
                  <p className="address-place">
                    {`${address.city}, ${es.provinces[address.province]} (${address.postalCode})`}
                  </p>
                  {confirming === address.id ? (
                    <div className="address-confirm" role="group" aria-label={messages.remove(addressLine(address))}>
                      <p>{messages.confirmRemove}</p>
                      <div className="address-actions">
                        <button
                          type="button"
                          className="btn"
                          disabled={busy}
                          onClick={() => {
                            remove.mutate(address.id);
                          }}
                        >
                          {messages.confirmRemoveYes}
                        </button>
                        <button
                          type="button"
                          className="btn btn-secondary"
                          onClick={() => {
                            setConfirming(null);
                          }}
                        >
                          {messages.cancel}
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div className="address-actions">
                      <button
                        type="button"
                        className="address-action"
                        aria-label={messages.edit(addressLine(address))}
                        disabled={busy}
                        onClick={() => {
                          setNotice(null);
                          setConfirming(null);
                          setEditing({ kind: 'edit', address });
                        }}
                      >
                        {messages.editShort}
                      </button>
                      {!address.primary && (
                        <button
                          type="button"
                          className="address-action"
                          aria-label={messages.makePrimary(addressLine(address))}
                          disabled={busy}
                          onClick={() => {
                            makePrimary.mutate(address);
                          }}
                        >
                          {messages.makePrimaryShort}
                        </button>
                      )}
                      <button
                        type="button"
                        className="address-action"
                        aria-label={messages.remove(addressLine(address))}
                        disabled={busy}
                        onClick={() => {
                          setNotice(null);
                          setConfirming(address.id);
                        }}
                      >
                        {messages.removeShort}
                      </button>
                    </div>
                  )}
                </li>
              ),
            )}
          </ul>
          {editing?.kind === 'new' ? (
            <div className="address-card">
              <AddressEditor
                editing={editing}
                canChoosePrimary={list.length > 0}
                onCancel={() => {
                  setEditing(null);
                }}
                onDone={done}
                onSignedOut={signedOut}
              />
            </div>
          ) : list.length >= MAX_ADDRESSES ? (
            <p className="account-intro">{messages.full}</p>
          ) : (
            editing === null && (
              <button
                type="button"
                className="btn"
                onClick={() => {
                  setNotice(null);
                  setConfirming(null);
                  setEditing({ kind: 'new' });
                }}
              >
                {messages.add}
              </button>
            )
          )}
        </>
      )}
    </main>
  );
}

interface EditorProps {
  editing: Editing;
  /** Whether the checkbox to make it the primary shows: not for the first address, nor for the primary itself. */
  canChoosePrimary: boolean;
  onCancel: () => void;
  onDone: (message: string) => void;
  onSignedOut: () => void;
}

function AddressEditor({ editing, canChoosePrimary, onCancel, onDone, onSignedOut }: EditorProps) {
  const [form, setForm] = useState<AddressFormValues>(
    editing.kind === 'edit' ? toAddressForm(editing.address) : EMPTY_ADDRESS,
  );
  const [primary, setPrimary] = useState(false);
  const [invalid, setInvalid] = useState<readonly AddressFormField[]>([]);
  const [refusal, setRefusal] = useState<string | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const id = useId();

  useEffect(() => {
    heading.current?.focus();
  }, []);

  function focusField(field: AddressFormField) {
    formRef.current?.querySelector<HTMLElement>(`[name="${field}"]`)?.focus();
  }

  function answered(answer: SaveAddressAnswer) {
    switch (answer.status) {
      case 'saved':
        onDone(messages.saved);
        break;
      case 'invalid': {
        const fields = toFormFields(answer.fields);
        setInvalid(fields);
        if (fields[0] !== undefined) {
          focusField(fields[0]);
        }
        break;
      }
      case 'full':
        setRefusal(messages.full);
        break;
      case 'notFound':
        onDone(messages.notFound);
        break;
      case 'signedOut':
        onSignedOut();
        break;
    }
  }

  const save = useMutation({
    mutationFn: (sent: { form: AddressFormValues; primary: boolean }) => {
      if (sent.form.province === '') {
        throw new Error('A province is chosen before sending');
      }
      const fields = toAddressFields(sent.form, sent.form.province);
      return editing.kind === 'edit'
        ? editAddress(editing.address.id, fields, sent.primary)
        : addAddress(fields, sent.primary);
    },
    onSuccess: answered,
    onError: () => {
      setRefusal(messages.error);
    },
  });

  function change(field: AddressFormField, value: string) {
    const next = { ...form, [field]: value };
    setForm(next);
    if (invalid.includes(field) && !invalidAddressFields(next).includes(field)) {
      setInvalid(invalid.filter((each) => each !== field));
    }
  }

  function send(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    setRefusal(null);
    const failing = invalidAddressFields(form);
    setInvalid(failing);
    if (failing[0] !== undefined) {
      focusField(failing[0]);
      return;
    }
    save.mutate({ form, primary });
  }

  function field(name: Exclude<AddressFormField, 'province'>, autoComplete: string, maxLength: number) {
    const fieldId = `${id}-${name}`;
    const isInvalid = invalid.includes(name);
    const optional = name === 'floor' || name === 'apartment';
    return (
      <div className={isInvalid ? 'address-field error' : 'address-field'}>
        <label htmlFor={fieldId}>
          {es.checkout.fields[name]}
          {optional && <span className="address-optional"> ({es.checkout.optional})</span>}
        </label>
        <input
          id={fieldId}
          name={name}
          value={form[name]}
          autoComplete={autoComplete}
          maxLength={maxLength}
          required={!optional}
          aria-invalid={isInvalid}
          aria-describedby={isInvalid ? `${fieldId}-error` : undefined}
          onChange={(event) => {
            change(name, event.target.value);
          }}
        />
        {isInvalid && (
          <p id={`${fieldId}-error`} className="address-field-error">
            {es.checkout.errors[name]}
          </p>
        )}
      </div>
    );
  }

  const provinceId = `${id}-province`;
  const provinceInvalid = invalid.includes('province');

  return (
    <form className="address-form" ref={formRef} onSubmit={send} noValidate>
      <h2 ref={heading} tabIndex={-1}>
        {editing.kind === 'edit' ? messages.editTitle : messages.newTitle}
      </h2>
      <div className="address-row address-row-street">
        {field('street', 'address-line1', 100)}
        {field('number', 'off', 10)}
      </div>
      <div className="address-row">
        {field('floor', 'off', 10)}
        {field('apartment', 'off', 10)}
      </div>
      <div className="address-row">
        {field('city', 'address-level2', 80)}
        {field('postalCode', 'postal-code', 8)}
      </div>
      <div className={provinceInvalid ? 'address-field error' : 'address-field'}>
        <label htmlFor={provinceId}>{es.checkout.fields.province}</label>
        <select
          id={provinceId}
          name="province"
          value={form.province}
          autoComplete="address-level1"
          required
          aria-invalid={provinceInvalid}
          aria-describedby={provinceInvalid ? `${provinceId}-error` : undefined}
          onChange={(event) => {
            change('province', event.target.value);
          }}
        >
          <option value="">{es.checkout.chooseProvince}</option>
          {PROVINCES.map((province) => (
            <option key={province} value={province}>
              {es.provinces[province]}
            </option>
          ))}
        </select>
        {provinceInvalid && (
          <p id={`${provinceId}-error`} className="address-field-error">
            {es.checkout.errors.province}
          </p>
        )}
      </div>
      {canChoosePrimary && (
        <label className="address-check">
          <input
            type="checkbox"
            checked={primary}
            onChange={(event) => {
              setPrimary(event.target.checked);
            }}
          />
          {messages.primaryCheckbox}
        </label>
      )}
      <p className="account-error" role="alert">
        {refusal}
      </p>
      <div className="address-actions">
        <button type="submit" className="btn" disabled={save.isPending}>
          {save.isPending ? messages.saving : messages.save}
        </button>
        <button type="button" className="btn btn-secondary" onClick={onCancel}>
          {messages.cancel}
        </button>
      </div>
    </form>
  );
}
