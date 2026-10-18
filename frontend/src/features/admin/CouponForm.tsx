import { useId, useState, type ReactNode, type SubmitEvent } from 'react';
import { es } from '../../i18n/es.ts';
import { centsToPesosInput, parsePesos } from './adminInput.ts';
import { CouponScopePicker } from './CouponScopePicker.tsx';
import type { CouponData, CouponField, CouponScope, CouponType, SaveAnswer } from './couponsApi.ts';
import { Feedback, type Notice } from './Feedback.tsx';

const messages = es.admin.couponForm;
const TYPES: readonly CouponType[] = ['PERCENTAGE', 'FIXED_AMOUNT'];
const WHOLE_CART: CouponScope = { productIds: [], categoryIds: [] };

/** The form as typed: amounts in pesos, empty texts for the optional fields. */
interface Draft {
  code: string;
  type: CouponType;
  value: string;
  validFrom: string;
  validUntil: string;
  maxUses: string;
  minimum: string;
  automatic: boolean;
  /** Kept while "Automático" is unticked, but only an automatic coupon sends it. */
  scope: CouponScope;
}

const EMPTY: Draft = {
  code: '',
  type: 'PERCENTAGE',
  value: '',
  validFrom: '',
  validUntil: '',
  maxUses: '',
  minimum: '',
  automatic: false,
  scope: WHOLE_CART,
};

function draftOf(coupon: CouponData): Draft {
  return {
    code: coupon.code,
    type: coupon.type,
    value: coupon.type === 'PERCENTAGE' ? String(coupon.discountValue) : centsToPesosInput(coupon.discountValue),
    validFrom: coupon.validFrom ?? '',
    validUntil: coupon.validUntil ?? '',
    maxUses: coupon.maxUses === null ? '' : String(coupon.maxUses),
    minimum: coupon.minimumPurchaseCents === null ? '' : centsToPesosInput(coupon.minimumPurchaseCents),
    automatic: coupon.automatic,
    scope: coupon.scope,
  };
}

/**
 * The coupon of a draft, or the fields that are not numbers. The ranges are the server's rules (UC-11), so they are
 * not checked here.
 */
function parseDraft(draft: Draft): { data: CouponData } | { invalid: CouponField[] } {
  const invalid: CouponField[] = [];
  const value = draft.type === 'PERCENTAGE' ? wholeNumber(draft.value) : parsePesos(draft.value);
  if (value === null) {
    invalid.push('DISCOUNT_VALUE');
  }
  const maxUses = draft.maxUses.trim() === '' ? null : wholeNumber(draft.maxUses);
  if (draft.maxUses.trim() !== '' && maxUses === null) {
    invalid.push('MAX_USES');
  }
  const minimum = draft.minimum.trim() === '' ? null : parsePesos(draft.minimum);
  if (draft.minimum.trim() !== '' && minimum === null) {
    invalid.push('MINIMUM_PURCHASE');
  }
  if (value === null || invalid.length > 0) {
    return { invalid };
  }
  return {
    data: {
      code: draft.code,
      type: draft.type,
      discountValue: value,
      validFrom: draft.validFrom === '' ? null : draft.validFrom,
      validUntil: draft.validUntil === '' ? null : draft.validUntil,
      maxUses,
      minimumPurchaseCents: minimum,
      automatic: draft.automatic,
      scope: draft.automatic ? draft.scope : WHOLE_CART,
    },
  };
}

/** A whole number without signs or separators, up to 9 digits; null otherwise. */
function wholeNumber(text: string): number | null {
  const trimmed = text.trim();
  return /^\d{1,9}$/.test(trimmed) ? Number(trimmed) : null;
}

/** A field with its label, an optional hint and, when it was refused, its message. */
function Field({
  label,
  hint,
  field,
  invalid,
  children,
}: {
  label: string;
  hint?: string;
  field: CouponField | null;
  invalid: readonly CouponField[];
  children: (props: { id: string; 'aria-invalid': boolean; 'aria-describedby'?: string }) => ReactNode;
}) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const isInvalid = field !== null && invalid.includes(field);
  const describedBy = [hint === undefined ? null : hintId, isInvalid ? errorId : null]
    .filter((part) => part !== null)
    .join(' ');
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children({ id, 'aria-invalid': isInvalid, ...(describedBy === '' ? {} : { 'aria-describedby': describedBy }) })}
      {hint !== undefined && (
        <p id={hintId} className="field-hint">
          {hint}
        </p>
      )}
      {isInvalid && (
        <p id={errorId} className="field-error">
          {messages.errors[field]}
        </p>
      )}
    </div>
  );
}

interface CouponFormProps {
  /** The coupon being edited; null to create one. */
  initial: CouponData | null;
  submitLabel: string;
  /** Saves the coupon; the form shows what the server refused. */
  onSubmit: (coupon: CouponData) => Promise<SaveAnswer>;
  /** After a save: a new coupon empties the form. */
  onSaved: () => void;
  onCancel?: () => void;
}

/**
 * Code, type, value, validity, maximum uses and minimum purchase of a coupon (UC-11), in one column. A new coupon may
 * be automatic (UC-23), which cannot change later; an automatic coupon also has its scope.
 */
export function CouponForm({ initial, submitLabel, onSubmit, onSaved, onCancel }: CouponFormProps) {
  const [draft, setDraft] = useState<Draft>(() => (initial === null ? EMPTY : draftOf(initial)));
  const [invalid, setInvalid] = useState<CouponField[]>([]);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [saving, setSaving] = useState(false);
  const automaticHintId = useId();

  function change(next: Partial<Draft>) {
    setDraft({ ...draft, ...next });
  }

  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setNotice(null);
    const parsed = parseDraft(draft);
    if ('invalid' in parsed) {
      setInvalid(parsed.invalid);
      setNotice({ kind: 'error', text: messages.invalid });
      return;
    }
    setSaving(true);
    try {
      const answer = await onSubmit(parsed.data);
      setInvalid(answer.status === 'invalid' ? answer.fields : []);
      switch (answer.status) {
        case 'saved':
          if (initial === null) {
            setDraft(EMPTY);
          }
          setNotice({ kind: 'ok', text: es.admin.saved });
          onSaved();
          break;
        case 'codeAlreadyUsed':
          setNotice({ kind: 'error', text: messages.codeUsed(answer.code) });
          break;
        case 'invalid':
          setNotice({ kind: 'error', text: messages.invalid });
          break;
        case 'notFound':
          setNotice({ kind: 'error', text: es.admin.couponList.notFound });
          break;
      }
    } catch {
      setNotice({ kind: 'error', text: es.admin.actionError });
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="admin-form" onSubmit={(event) => void submit(event)}>
      {initial === null && (
        <div>
          <label className="switch">
            <input
              type="checkbox"
              checked={draft.automatic}
              aria-describedby={automaticHintId}
              onChange={(event) => {
                change({ automatic: event.target.checked });
              }}
            />
            <span>{messages.automatic}</span>
          </label>
          <p id={automaticHintId} className="field-hint">
            {messages.automaticHint}
          </p>
        </div>
      )}
      <Field label={messages.code} hint={messages.codeHint} field="CODE" invalid={invalid}>
        {(aria) => (
          <input
            {...aria}
            value={draft.code}
            maxLength={20}
            autoCapitalize="characters"
            autoComplete="off"
            required
            onChange={(event) => {
              change({ code: event.target.value });
            }}
          />
        )}
      </Field>
      <Field label={messages.type} field={null} invalid={invalid}>
        {(aria) => (
          <select
            {...aria}
            value={draft.type}
            onChange={(event) => {
              change({ type: event.target.value as CouponType });
            }}
          >
            {TYPES.map((type) => (
              <option key={type} value={type}>
                {messages.types[type]}
              </option>
            ))}
          </select>
        )}
      </Field>
      <Field
        label={draft.type === 'PERCENTAGE' ? messages.percent : messages.amount}
        field="DISCOUNT_VALUE"
        invalid={invalid}
      >
        {(aria) => (
          <input
            {...aria}
            value={draft.value}
            inputMode={draft.type === 'PERCENTAGE' ? 'numeric' : 'decimal'}
            required
            onChange={(event) => {
              change({ value: event.target.value });
            }}
          />
        )}
      </Field>
      <Field label={messages.validFrom} hint={messages.optional} field="VALIDITY" invalid={invalid}>
        {(aria) => (
          <input
            {...aria}
            type="date"
            value={draft.validFrom}
            onChange={(event) => {
              change({ validFrom: event.target.value });
            }}
          />
        )}
      </Field>
      <Field label={messages.validUntil} hint={messages.optional} field={null} invalid={invalid}>
        {(aria) => (
          <input
            {...aria}
            type="date"
            value={draft.validUntil}
            onChange={(event) => {
              change({ validUntil: event.target.value });
            }}
          />
        )}
      </Field>
      <Field label={messages.maxUses} hint={messages.maxUsesHint} field="MAX_USES" invalid={invalid}>
        {(aria) => (
          <input
            {...aria}
            value={draft.maxUses}
            inputMode="numeric"
            onChange={(event) => {
              change({ maxUses: event.target.value });
            }}
          />
        )}
      </Field>
      <Field label={messages.minimum} hint={messages.minimumHint} field="MINIMUM_PURCHASE" invalid={invalid}>
        {(aria) => (
          <input
            {...aria}
            value={draft.minimum}
            inputMode="decimal"
            onChange={(event) => {
              change({ minimum: event.target.value });
            }}
          />
        )}
      </Field>
      {draft.automatic && (
        <CouponScopePicker
          scope={draft.scope}
          invalid={invalid.includes('SCOPE')}
          onChange={(scope) => {
            change({ scope });
          }}
        />
      )}
      <Feedback notice={notice} />
      <div className="admin-actions">
        <button type="submit" className="btn" disabled={saving}>
          {submitLabel}
        </button>
        {onCancel !== undefined && (
          <button type="button" className="btn btn-secondary" onClick={onCancel}>
            {es.admin.cancel}
          </button>
        )}
      </div>
    </form>
  );
}
