import { useQueryClient } from '@tanstack/react-query';
import { useId, useState, type SubmitEvent } from 'react';
import { es } from '../../i18n/es.ts';
import { Button } from '../../components/ui/Button.tsx';
import { Field, FieldInput, FieldLabel } from '../../components/ui/Field.tsx';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { centsToPesosInput, parsePesos } from './adminInput.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import { replaceZones, setFreeShippingThreshold, type Province, type ShippingConfiguration } from './shippingApi.ts';
import { SHIPPING_KEY, useShipping } from './useShipping.ts';

const messages = esAdmin.shippingPage;

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
      setNotice({ kind: 'ok', text: esAdmin.saved });
      onSaved(answer.configuration);
    } catch {
      setNotice({ kind: 'error', text: esAdmin.actionError });
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
        const errorId = `${id}-${province}-error`;
        const isInvalid = invalid.includes(province);
        return (
          <Field key={province} error={isInvalid ? messages.invalidCost : undefined}>
            <FieldLabel>{es.provinces[province]}</FieldLabel>
            {/* The one hint above the list describes every field. */}
            <FieldInput
              name={province}
              value={costs[province] ?? ''}
              inputMode="decimal"
              autoComplete="off"
              aria-describedby={isInvalid ? `${hintId} ${errorId}` : hintId}
              onChange={(event) => {
                setCosts({ ...costs, [province]: event.target.value });
              }}
            />
          </Field>
        );
      })}
      <Feedback notice={notice} />
      <div className="admin-actions">
        <Button variant="primary" type="submit" disabled={saving}>
          {messages.saveZones}
        </Button>
      </div>
    </form>
  );
}

/** The purchase amount from which shipping is free; empty for none. */
function ThresholdForm({ configuration, onSaved }: FormProps) {
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
      setNotice({ kind: 'ok', text: esAdmin.saved });
      onSaved(answer.configuration);
    } catch {
      setNotice({ kind: 'error', text: esAdmin.actionError });
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="admin-form" onSubmit={(event) => void submit(event)}>
      <Field hint={messages.thresholdHint} error={invalid ? messages.invalidThreshold : undefined}>
        <FieldLabel>{messages.threshold}</FieldLabel>
        <FieldInput
          name="threshold"
          value={threshold}
          inputMode="decimal"
          autoComplete="off"
          onChange={(event) => {
            setThreshold(event.target.value);
          }}
        />
      </Field>
      <Feedback notice={notice} />
      <div className="admin-actions">
        <Button variant="primary" type="submit" disabled={saving}>
          {messages.saveThreshold}
        </Button>
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
      <title>{`${messages.title} | ${esAdmin.title}`}</title>
      <h1>{messages.title}</h1>
      {shipping.isPending && <p role="status">{esAdmin.loading}</p>}
      {shipping.isError && (
        <div role="alert">
          <p>{esAdmin.loadError}</p>
          <Button onClick={() => void shipping.refetch()}>
            {esAdmin.retry}
          </Button>
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
