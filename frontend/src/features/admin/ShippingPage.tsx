import { useQueryClient } from '@tanstack/react-query';
import { useId, useState, type SubmitEvent } from 'react';
import { es } from '../../i18n/es.ts';
import { centsToPesosInput, parsePesos } from './adminInput.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import { replaceZones, setFreeShippingThreshold, type Province, type ShippingConfiguration } from './shippingApi.ts';
import { SHIPPING_KEY, useShipping } from './useShipping.ts';

const messages = es.admin.shippingPage;

/** Cents as the field shows them; empty for none. */
function pesosText(cents: number | null): string {
  return cents === null ? '' : centsToPesosInput(cents);
}

interface FormProps {
  configuration: ShippingConfiguration;
  /** Keeps the page's copy in step with what the server stored. */
  onSaved: (configuration: ShippingConfiguration) => void;
}

/** A cost field per province; an empty one means no shipping there. They are saved together. */
function ZonesForm({ configuration, onSaved }: FormProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const [costs, setCosts] = useState<Record<string, string>>(() =>
    Object.fromEntries(configuration.zones.map((zone) => [zone.province, pesosText(zone.costCents)])),
  );
  const [invalid, setInvalid] = useState<Province[]>([]);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setNotice(null);
    const zones: { province: Province; costCents: number }[] = [];
    const unreadable: Province[] = [];
    for (const { province } of configuration.zones) {
      const text = (costs[province] ?? '').trim();
      if (text === '') {
        continue;
      }
      const cents = parsePesos(text);
      if (cents === null) {
        unreadable.push(province);
      } else {
        zones.push({ province, costCents: cents });
      }
    }
    if (unreadable.length > 0) {
      setInvalid(unreadable);
      setNotice({ kind: 'error', text: messages.invalidZones });
      return;
    }
    setSaving(true);
    try {
      const answer = await replaceZones(zones);
      if (answer.status === 'invalid') {
        setInvalid(answer.provinces);
        setNotice({ kind: 'error', text: messages.invalidZones });
        return;
      }
      setInvalid([]);
      setCosts(
        Object.fromEntries(answer.configuration.zones.map((zone) => [zone.province, pesosText(zone.costCents)])),
      );
      setNotice({ kind: 'ok', text: es.admin.saved });
      onSaved(answer.configuration);
    } catch {
      setNotice({ kind: 'error', text: es.admin.actionError });
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="admin-form" onSubmit={(event) => void submit(event)}>
      <p id={hintId} className="field-hint">
        {messages.zonesHint}
      </p>
      {configuration.zones.map(({ province }) => {
        const fieldId = `${id}-${province}`;
        const errorId = `${fieldId}-error`;
        const isInvalid = invalid.includes(province);
        return (
          <div className="field" key={province}>
            <label htmlFor={fieldId}>{es.provinces[province]}</label>
            <input
              id={fieldId}
              value={costs[province] ?? ''}
              inputMode="decimal"
              autoComplete="off"
              aria-invalid={isInvalid}
              aria-describedby={isInvalid ? `${hintId} ${errorId}` : hintId}
              onChange={(event) => {
                setCosts({ ...costs, [province]: event.target.value });
              }}
            />
            {isInvalid && (
              <p id={errorId} className="field-error">
                {messages.invalidCost}
              </p>
            )}
          </div>
        );
      })}
      <Feedback notice={notice} />
      <div className="admin-actions">
        <button type="submit" className="btn" disabled={saving}>
          {messages.saveZones}
        </button>
      </div>
    </form>
  );
}

/** The purchase amount from which shipping is free; empty for none. */
function ThresholdForm({ configuration, onSaved }: FormProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const [threshold, setThreshold] = useState(() => pesosText(configuration.freeShippingThresholdCents));
  const [invalid, setInvalid] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setNotice(null);
    const text = threshold.trim();
    const cents = text === '' ? null : parsePesos(text);
    if (text !== '' && cents === null) {
      setInvalid(true);
      setNotice({ kind: 'error', text: messages.invalidThreshold });
      return;
    }
    setSaving(true);
    try {
      const answer = await setFreeShippingThreshold(cents);
      if (answer.status === 'invalid') {
        setInvalid(true);
        setNotice({ kind: 'error', text: messages.invalidThreshold });
        return;
      }
      setInvalid(false);
      setThreshold(pesosText(answer.configuration.freeShippingThresholdCents));
      setNotice({ kind: 'ok', text: es.admin.saved });
      onSaved(answer.configuration);
    } catch {
      setNotice({ kind: 'error', text: es.admin.actionError });
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="admin-form" onSubmit={(event) => void submit(event)}>
      <div className="field">
        <label htmlFor={id}>{messages.threshold}</label>
        <input
          id={id}
          value={threshold}
          inputMode="decimal"
          autoComplete="off"
          aria-invalid={invalid}
          aria-describedby={invalid ? `${hintId} ${errorId}` : hintId}
          onChange={(event) => {
            setThreshold(event.target.value);
          }}
        />
        <p id={hintId} className="field-hint">
          {messages.thresholdHint}
        </p>
        {invalid && (
          <p id={errorId} className="field-error">
            {messages.invalidThreshold}
          </p>
        )}
      </div>
      <Feedback notice={notice} />
      <div className="admin-actions">
        <button type="submit" className="btn" disabled={saving}>
          {messages.saveThreshold}
        </button>
      </div>
    </form>
  );
}

/**
 * UC-12 at /admin/envios: the shipping cost of each of the 24 provinces, saved together, and the purchase amount from
 * which shipping is free.
 */
export function ShippingPage() {
  const shipping = useShipping();
  const queryClient = useQueryClient();
  const zonesTitleId = useId();
  const freeShippingTitleId = useId();

  function saved(configuration: ShippingConfiguration) {
    queryClient.setQueryData(SHIPPING_KEY, configuration);
  }

  return (
    <>
      <title>{`${messages.title} | ${es.admin.title}`}</title>
      <h1>{messages.title}</h1>
      {shipping.isPending && <p role="status">{es.admin.loading}</p>}
      {shipping.isError && (
        <div role="alert">
          <p>{es.admin.loadError}</p>
          <button type="button" className="btn" onClick={() => void shipping.refetch()}>
            {es.admin.retry}
          </button>
        </div>
      )}
      {shipping.data !== undefined && (
        <>
          <section className="admin-section" aria-labelledby={zonesTitleId}>
            <h2 id={zonesTitleId}>{messages.zonesTitle}</h2>
            <ZonesForm configuration={shipping.data} onSaved={saved} />
          </section>
          <section className="admin-section" aria-labelledby={freeShippingTitleId}>
            <h2 id={freeShippingTitleId}>{messages.freeShippingTitle}</h2>
            <ThresholdForm configuration={shipping.data} onSaved={saved} />
          </section>
        </>
      )}
    </>
  );
}
