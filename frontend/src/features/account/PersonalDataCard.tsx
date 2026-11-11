import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type KeyboardEvent, type SyntheticEvent } from 'react';
import { es } from '../../i18n/es.ts';
import { changePhone, type Customer } from './accountApi.ts';
import { CUSTOMER_KEY } from './useCustomer.ts';

const messages = es.account.myAccount;

interface PersonalDataCardProps {
  customer: Customer;
  /** The session is gone: the page sends the visitor to sign in. */
  onSignedOut: () => void;
}

/** Where the focus goes when the phone's row stops editing: its status after a save, its button otherwise. */
type Closed = 'saved' | 'cancelled' | null;

/**
 * "Datos personales" of "Datos y acceso" (T-E7-62): its title, then the data in a white card, as the sign-in methods.
 * The name and the email read only; the phone has "Agregar" or "Editar" beside its value (UC-40, T-E4-18), which turns
 * the row into its field, labelled by the row's own "Teléfono". Saving updates the signed-in customer, so the summary
 * and the checkout show the new phone at once.
 */
export function PersonalDataCard({ customer, onSignedOut }: PersonalDataCardProps) {
  const fieldId = useId();
  const [editing, setEditing] = useState(false);
  const [closed, setClosed] = useState<Closed>(null);
  const savedRef = useRef<HTMLParagraphElement>(null);
  const editRef = useRef<HTMLButtonElement>(null);

  // After the row closes, the focus lands where the customer can go on: never on <body> (WCAG 2.4.3).
  useEffect(() => {
    if (closed === 'saved') {
      savedRef.current?.focus();
    } else if (closed === 'cancelled') {
      editRef.current?.focus();
    }
  }, [closed]);

  function close(how: Exclude<Closed, null>) {
    setEditing(false);
    setClosed(how);
  }

  return (
    <section aria-labelledby="personal-data-title">
      <h2 id="personal-data-title" className="my-account-heading">
        {messages.personalTitle}
      </h2>
      {/* The title outside and the data in a card, as "Formas de ingresar" and each of its methods. */}
      <dl className="account-data-list address-card">
        <div>
          <dt>{es.account.summary.name}</dt>
          <dd>{customer.name}</dd>
        </div>
        <div>
          <dt>{es.account.email}</dt>
          <dd>{customer.email}</dd>
        </div>
        <div>
          {/* While editing, the row's own "Teléfono" labels the field, so it is said once and clicking it focuses it. */}
          <dt>{editing ? <label htmlFor={fieldId}>{es.account.summary.phone}</label> : es.account.summary.phone}</dt>
          {editing ? (
            <dd>
              <PhoneForm
                fieldId={fieldId}
                current={customer.phone}
                onCancel={() => {
                  close('cancelled');
                }}
                onSaved={() => {
                  close('saved');
                }}
                onSignedOut={onSignedOut}
              />
            </dd>
          ) : (
            <dd className="account-data-value">
              {customer.phone ?? <span className="account-data-missing">{es.account.summary.noPhone}</span>}
              <button
                ref={editRef}
                type="button"
                className="address-action"
                aria-label={customer.phone === null ? messages.addPhoneLabel : messages.editPhoneLabel}
                onClick={() => {
                  setClosed(null);
                  setEditing(true);
                }}
              >
                {customer.phone === null ? messages.addPhone : messages.editPhone}
              </button>
            </dd>
          )}
        </div>
      </dl>
      <p className="account-data-saved" ref={savedRef} tabIndex={-1} role="status">
        {closed === 'saved' ? messages.phoneSaved : ''}
      </p>
    </section>
  );
}

interface PhoneFormProps {
  /** The id the row's "Teléfono" label points at. */
  fieldId: string;
  current: string | null;
  onCancel: () => void;
  onSaved: () => void;
  onSignedOut: () => void;
}

/**
 * The phone's field, as the checkout's ("Con código de área"); the server normalizes and checks it (UC-13). Escape
 * cancels as "Cancelar" does; while saving, neither can cancel, so the answer never takes the focus from elsewhere.
 */
function PhoneForm({ fieldId, current, onCancel, onSaved, onSignedOut }: PhoneFormProps) {
  const queryClient = useQueryClient();
  const [phone, setPhone] = useState(current ?? '');
  const field = useRef<HTMLInputElement>(null);
  const save = useMutation({
    mutationFn: changePhone,
    onSuccess: (answer) => {
      switch (answer.status) {
        case 'changed':
          queryClient.setQueryData<Customer | null>(CUSTOMER_KEY, (customer) =>
            customer == null ? customer : { ...customer, phone: answer.phone },
          );
          onSaved();
          break;
        case 'signedOut':
          onSignedOut();
          break;
        case 'invalidPhone':
          field.current?.focus();
          break;
      }
    },
    // A failed request leaves the customer on the field, with the message beside it.
    onError: () => {
      field.current?.focus();
    },
  });

  useEffect(() => {
    field.current?.focus();
  }, []);

  function send(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    save.mutate(phone);
  }

  function cancelOnEscape(event: KeyboardEvent<HTMLFormElement>) {
    if (event.key === 'Escape' && !save.isPending) {
      event.preventDefault();
      onCancel();
    }
  }

  const problem =
    save.data?.status === 'invalidPhone' ? messages.invalidPhone : save.isError ? messages.phoneError : null;

  return (
    <form className="account-form account-phone-form" onSubmit={send} onKeyDown={cancelOnEscape} noValidate>
      <input
        ref={field}
        id={fieldId}
        name="phone"
        type="tel"
        autoComplete="tel"
        aria-describedby={`${fieldId}-hint ${fieldId}-problem`}
        aria-invalid={save.data?.status === 'invalidPhone' ? true : undefined}
        value={phone}
        onChange={(event) => {
          setPhone(event.target.value);
        }}
      />
      <p id={`${fieldId}-hint`} className="account-hint">
        {messages.phoneHint}
      </p>
      {/* Always there, so the alert is announced when its text arrives. */}
      <p id={`${fieldId}-problem`} className="account-error" role="alert">
        {problem}
      </p>
      <div className="address-actions">
        <button type="submit" className="btn" disabled={save.isPending}>
          {save.isPending ? messages.savingPhone : messages.savePhone}
        </button>
        <button type="button" className="btn btn-secondary" disabled={save.isPending} onClick={onCancel}>
          {messages.cancel}
        </button>
      </div>
    </form>
  );
}
