import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useRef, useState, type SubmitEvent } from 'react';
import { Button } from '../../components/ui/Button.tsx';
import { Card, CardContent, CardHeader, CardTitle } from '../../components/ui/Card.tsx';
import { Field, FieldInput, FieldLabel } from '../../components/ui/Field.tsx';
import { es } from '../../i18n/es.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { useShippingRates } from '../help/useShippingRates.ts';
import { STORE_CONTACT_KEY } from '../help/useStoreContact.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import {
  fetchStoreSettings,
  saveStoreSettings,
  type SettingsDraft,
  type SettingsField,
  type StoreSettings,
} from './storeSettingsApi.ts';
import { useUnsavedWarning } from './useUnsavedWarning.ts';

const messages = esAdmin.storeSettingsPage;

const settingsKey = ['admin', 'storeSettings'] as const;

/** The limits of UC-36, so a field never takes more than the server does. */
const MAX_LENGTH: Record<Exclude<SettingsField, 'lowStockThreshold'>, number> = {
  email: 254,
  whatsAppNumber: 25,
  openingHours: 80,
  address: 120,
  instagramUrl: 200,
  facebookUrl: 200,
  tiktokUrl: 200,
  announcement: 80,
};

/** How each text is typed: the keyboard a phone shows for it. */
const INPUT_TYPE: Partial<Record<SettingsField, { type: string; inputMode?: 'tel' | 'url' | 'email' | 'numeric' }>> = {
  email: { type: 'email', inputMode: 'email' },
  whatsAppNumber: { type: 'tel', inputMode: 'tel' },
  instagramUrl: { type: 'url', inputMode: 'url' },
  facebookUrl: { type: 'url', inputMode: 'url' },
  tiktokUrl: { type: 'url', inputMode: 'url' },
};

function draftOf(settings: StoreSettings): SettingsDraft {
  return { ...settings, lowStockThreshold: String(settings.lowStockThreshold) };
}

/**
 * The header strip as the store shows it (UC-31, UC-36): the announcement, or else "Envío gratis desde $X", then the
 * carrier. Still, not running: the store's ticker moves.
 */
function StripPreview({ announcement }: { announcement: string }) {
  const threshold = useShippingRates().data?.freeShippingThresholdCents ?? null;
  const typed = announcement.trim();
  const first = typed !== '' ? typed : threshold === null ? null : es.header.freeShippingFrom(formatMoney(threshold));
  return (
    <div className="flex flex-col gap-1.5">
      <p className="m-0 text-[13px] font-semibold text-foreground">{messages.previewTitle}</p>
      <ul className="m-0 flex list-none flex-wrap gap-x-4 gap-y-1 rounded-md bg-accent px-3 py-2.5 text-[13px] font-semibold text-accent-foreground">
        {first !== null && <li className="min-w-0 break-words">{first}</li>}
        <li>{es.header.ticker.carrier}</li>
      </ul>
    </div>
  );
}

interface EditorProps {
  settings: StoreSettings;
  onChanged: (settings: StoreSettings) => void;
}

/** UC-36 steps 2 and 3: every value, saved at once from the version it was opened with. */
function SettingsEditor({ settings, onChanged }: EditorProps) {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<SettingsDraft>(() => draftOf(settings));
  // What was last loaded or saved: a field that differs from it is unsaved (UC-36, decided on 2026-11-04).
  const [saved, setSaved] = useState<SettingsDraft>(() => draftOf(settings));
  const form = useRef<HTMLFormElement>(null);
  const [invalid, setInvalid] = useState<SettingsField[]>([]);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const id = useId();
  const unsaved = (Object.keys(draft) as SettingsField[]).some((name) => draft[name] !== saved[name]);
  useUnsavedWarning(unsaved, messages.unsavedChanges);

  function changed(next: StoreSettings) {
    setDraft(draftOf(next));
    setSaved(draftOf(next));
    onChanged(next);
  }

  async function save(): Promise<Notice> {
    const answer = await saveStoreSettings(draft);
    switch (answer.status) {
      case 'invalid':
        setInvalid(answer.fields);
        focusFirst(answer.fields);
        return { kind: 'error', text: messages.invalid };
      case 'changed':
        // Another tab saved first: what it saved is shown, to review and save again.
        setInvalid([]);
        changed(await fetchStoreSettings());
        return { kind: 'error', text: messages.changed };
      case 'saved':
        setInvalid([]);
        changed(answer.settings);
        // The store reads them again on its next load; the summary uses the threshold.
        void queryClient.invalidateQueries({ queryKey: STORE_CONTACT_KEY });
        void queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
        return { kind: 'ok', text: esAdmin.saved };
    }
  }

  /** The first refused field, in the order of the form, takes the focus, so its error is read where it lands. */
  function focusFirst(fields: SettingsField[]) {
    const first = [...(form.current?.elements ?? [])].find(
      (element): element is HTMLInputElement =>
        element instanceof HTMLInputElement && fields.some((name) => name === element.name),
    );
    first?.focus();
  }

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) {
      return;
    }
    setNotice(null);
    setBusy(true);
    save()
      .then(setNotice)
      .catch(() => {
        setNotice({ kind: 'error', text: esAdmin.actionError });
      })
      .finally(() => {
        setBusy(false);
      });
  }

  const field = (name: SettingsField) => {
    const value = name === 'lowStockThreshold' ? draft.lowStockThreshold : draft[name];
    const input =
      name === 'lowStockThreshold'
        ? { type: 'text', inputMode: 'numeric' as const, maxLength: 5, className: 'max-w-32 tabular-nums' }
        : { type: 'text', ...INPUT_TYPE[name], maxLength: MAX_LENGTH[name] };
    return (
      <Field key={name} hint={messages.hints[name]} error={invalid.includes(name) ? messages.errors[name] : undefined}>
        <FieldLabel>{messages.labels[name]}</FieldLabel>
        <FieldInput
          {...input}
          name={name}
          value={value}
          autoComplete="off"
          spellCheck={name === 'announcement' || name === 'openingHours'}
          onChange={(event) => {
            setDraft({ ...draft, [name]: event.target.value });
          }}
        />
      </Field>
    );
  };

  return (
    // The server checks every rule (UC-36) and names each field it refuses; the browser's own checks would not.
    <form ref={form} className="flex max-w-2xl flex-col gap-4" noValidate onSubmit={submit}>
      <Card aria-labelledby={`${id}-contact`}>
        <CardHeader>
          <CardTitle id={`${id}-contact`}>{messages.contactTitle}</CardTitle>
        </CardHeader>
        <CardContent>{(['email', 'whatsAppNumber', 'openingHours', 'address'] as const).map(field)}</CardContent>
      </Card>
      <Card aria-labelledby={`${id}-social`}>
        <CardHeader>
          <CardTitle id={`${id}-social`}>{messages.socialTitle}</CardTitle>
        </CardHeader>
        <CardContent>{(['instagramUrl', 'facebookUrl', 'tiktokUrl'] as const).map(field)}</CardContent>
      </Card>
      <Card aria-labelledby={`${id}-strip`}>
        <CardHeader>
          <CardTitle id={`${id}-strip`}>{messages.stripTitle}</CardTitle>
        </CardHeader>
        <CardContent>
          {field('announcement')}
          <StripPreview announcement={draft.announcement} />
        </CardContent>
      </Card>
      <Card aria-labelledby={`${id}-stock`}>
        <CardHeader>
          <CardTitle id={`${id}-stock`}>{messages.stockTitle}</CardTitle>
        </CardHeader>
        <CardContent>{field('lowStockThreshold')}</CardContent>
      </Card>
      <Feedback notice={notice} />
      <div>
        {/* Focusable while it saves (aria-disabled), so a keyboard does not lose its place. */}
        <Button type="submit" variant="primary" aria-disabled={busy}>
          {busy ? esAdmin.saving : esAdmin.save}
        </Button>
      </div>
    </form>
  );
}

/** UC-36 at /admin/datos-de-la-tienda: the store's contact data, networks, header strip and low-stock threshold. */
export function StoreSettingsPage() {
  const queryClient = useQueryClient();
  const found = useQuery({ queryKey: settingsKey, queryFn: ({ signal }) => fetchStoreSettings(signal) });

  return (
    <>
      <title>{`${messages.title} | ${esAdmin.title}`}</title>
      <div className="flex flex-col gap-1">
        <h1>{messages.title}</h1>
        <p className="m-0 text-sm text-muted-foreground">{messages.intro}</p>
      </div>
      {found.isPending && <p role="status">{esAdmin.loading}</p>}
      {found.isError && (
        <div role="alert">
          <p>{esAdmin.loadError}</p>
          <Button onClick={() => void found.refetch()}>{esAdmin.retry}</Button>
        </div>
      )}
      {found.data !== undefined && (
        <SettingsEditor
          settings={found.data}
          onChanged={(settings) => {
            queryClient.setQueryData(settingsKey, settings);
          }}
        />
      )}
    </>
  );
}

