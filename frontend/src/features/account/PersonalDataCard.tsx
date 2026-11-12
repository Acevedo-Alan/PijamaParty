import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type KeyboardEvent, type SyntheticEvent } from 'react';
import { es } from '../../i18n/es.ts';
import { changePhone, type Customer } from './accountApi.ts';
import { Group } from './Group.tsx';
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
 * "Datos personales" of "Datos y acceso" (T-E7-62, T-E7-63): the name and the email read only; "Agregar teléfono" or
 * "Editar teléfono" is an action row (UC-40, T-E4-18) that turns the phone into its field, labelled by the row's own
 * "Teléfono". Saving updates the signed-in customer, so the summary and the checkout show the new phone at once.
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
      <Group.Section title={messages.personalTitle}>
        <Group.Card>
          <Group.Field label={es.account.summary.name}>{customer.name}</Group.Field>
          <Group.Field label={es.account.email}>{customer.email}</Group.Field>
          {editing ? (
            <Group.Row>
              {/* The row's own "Teléfono" labels the field, so it is said once and clicking it focuses it. */}
              <label htmlFor={fieldId} className="group-field-label">
                {es.account.summary.phone}
              </label>
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
            </Group.Row>
          ) : (
            <>
              <Group.Field label={es.account.summary.phone}>
                {customer.phone ?? <Group.Missing>{es.account.summary.noPhone}</Group.Missing>}
              </Group.Field>
              <Group.ActionButton
                ref={editRef}
                icon="phone"
                onClick={() => {
                  setClosed(null);
                  setEditing(true);
                }}
              >
                {customer.phone === null ? messages.addPhoneLabel : messages.editPhoneLabel}
              </Group.ActionButton>
            </>
          )}
        </Group.Card>
        <p className="account-data-saved" ref={savedRef} tabIndex={-1} role="status">
          {closed === 'saved' ? messages.phoneSaved : ''}
        </p>
      </Group.Section>
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
