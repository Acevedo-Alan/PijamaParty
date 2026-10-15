import { useMutation } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type SyntheticEvent } from 'react';
import { es } from '../../i18n/es.ts';
import { requestWithdrawal, type WithdrawalAnswer } from './withdrawalApi.ts';
import './withdrawal.css';

interface WithdrawalForm {
  orderNumber: string;
  email: string;
}

/** Why there is no code, or nothing yet. */
function problemOf(answer: WithdrawalAnswer | undefined, failed: boolean): string | null {
  if (failed) {
    return es.withdrawal.error;
  }
  switch (answer?.status) {
    case 'noMatchingOrder':
      return es.withdrawal.noMatchingOrder;
    case 'notWithdrawable':
      return es.withdrawal.notWithdrawable;
    case 'tooManyAttempts':
      return es.withdrawal.tooManyAttempts;
    default:
      return null;
  }
}

/**
 * UC-29 (T-E3-25): the withdrawal button, without an account. The order number and the purchase email give a
 * procedure code, also emailed; the server checks both and never says which one did not match.
 */
export function WithdrawalPage() {
  const withdrawal = useMutation({
    mutationFn: ({ orderNumber, email }: WithdrawalForm) => requestWithdrawal(orderNumber, email),
  });
  const [form, setForm] = useState<WithdrawalForm>({ orderNumber: '', email: '' });
  const done = useRef<HTMLDivElement>(null);
  const id = useId();
  const answer = withdrawal.data;
  const code = answer?.status === 'requested' || answer?.status === 'alreadyRequested' ? answer.code : null;

  useEffect(() => {
    if (code !== null) {
      done.current?.focus();
    }
  }, [code]);

  function send(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    withdrawal.mutate({ orderNumber: form.orderNumber.trim(), email: form.email.trim() });
  }

  function change(field: keyof WithdrawalForm, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  const incomplete = form.orderNumber.trim() === '' || form.email.trim() === '';
  return (
    <main className="wrap withdrawal">
      <title>{`${es.withdrawal.title} | ${es.store.name}`}</title>
      <h1>{es.withdrawal.title}</h1>
      <p className="withdrawal-intro">{es.withdrawal.intro}</p>
      {code !== null ? (
        <div className="withdrawal-done" ref={done} tabIndex={-1} role="status">
          <p>{answer?.status === 'alreadyRequested' ? es.withdrawal.alreadyRequested : es.withdrawal.requested}</p>
          <p className="withdrawal-code">{code}</p>
          <p>{es.withdrawal.keepIt}</p>
        </div>
      ) : (
        <form className="withdrawal-form" onSubmit={send} noValidate>
          <label htmlFor={`${id}-number`}>{es.withdrawal.orderNumber}</label>
          <input
            id={`${id}-number`}
            name="orderNumber"
            inputMode="numeric"
            autoComplete="off"
            maxLength={20}
            placeholder={es.withdrawal.orderNumberHint}
            required
            value={form.orderNumber}
            onChange={(event) => {
              change('orderNumber', event.target.value);
            }}
          />
          <label htmlFor={`${id}-email`}>{es.withdrawal.email}</label>
          <input
            id={`${id}-email`}
            name="email"
            type="email"
            inputMode="email"
            autoComplete="email"
            maxLength={254}
            required
            value={form.email}
            onChange={(event) => {
              change('email', event.target.value);
            }}
          />
          <button type="submit" className="btn" disabled={withdrawal.isPending || incomplete}>
            {withdrawal.isPending ? es.withdrawal.sending : es.withdrawal.send}
          </button>
          <p className="withdrawal-error" role="alert">
            {problemOf(answer, withdrawal.isError)}
          </p>
        </form>
      )}
    </main>
  );
}
