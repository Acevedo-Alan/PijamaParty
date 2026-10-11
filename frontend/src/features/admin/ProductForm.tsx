import { useId, useState, type ReactNode, type SubmitEvent } from 'react';
import { es } from '../../i18n/es.ts';
import type { Audience, Season } from '../catalog/listingCriteria.ts';
import type { Category } from './categoriesApi.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import type { DetailsField, ProductDetails, SaveDetailsAnswer } from './productsApi.ts';

const messages = es.admin.productForm;
const SEASONS: readonly Season[] = ['SUMMER', 'MID_SEASON', 'WINTER'];
const AUDIENCES: readonly Audience[] = ['WOMEN', 'MEN', 'KIDS'];

/** A field with its label and, when the server refused it, its message (UC-06 `fields`). */
function Field({
  label,
  field,
  invalid,
  children,
}: {
  label: string;
  field: DetailsField;
  invalid: readonly DetailsField[];
  children: (props: { id: string; 'aria-invalid': boolean; 'aria-describedby'?: string }) => ReactNode;
}) {
  const id = useId();
  const errorId = `${id}-error`;
  const isInvalid = invalid.includes(field);
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children(isInvalid ? { id, 'aria-invalid': true, 'aria-describedby': errorId } : { id, 'aria-invalid': false })}
      {isInvalid && (
        <p id={errorId} className="field-error">
          {messages.errors[field]}
        </p>
      )}
    </div>
  );
}

interface ProductFormProps {
  categories: Category[];
  initial: ProductDetails | null;
  submitLabel: string;
  /** Saves the details; the form shows what the server refused. */
  onSubmit: (details: ProductDetails) => Promise<SaveDetailsAnswer>;
}

/** Name, description, material, season, audience and category of a product (UC-06), in one column. */
export function ProductForm({ categories, initial, submitLabel, onSubmit }: ProductFormProps) {
  const [details, setDetails] = useState<ProductDetails>(
    () =>
      initial ?? { categoryId: '', name: '', description: '', material: '', season: 'WINTER', audience: 'WOMEN' },
  );
  const [invalid, setInvalid] = useState<DetailsField[]>([]);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [saving, setSaving] = useState(false);

  function change(next: Partial<ProductDetails>) {
    setDetails({ ...details, ...next });
  }

  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setNotice(null);
    try {
      const answer = await onSubmit(details);
      setInvalid(answer.status === 'invalid' ? answer.fields : []);
      switch (answer.status) {
        case 'saved':
          setNotice({ kind: 'ok', text: es.admin.saved });
          break;
        case 'slugAlreadyUsed':
          setNotice({ kind: 'error', text: messages.slugUsed(answer.slug) });
          break;
        case 'invalid':
          setNotice({ kind: 'error', text: messages.invalid });
          break;
        case 'notFound':
          setNotice({ kind: 'error', text: es.admin.productEdit.notFound });
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
      <Field label={messages.name} field="NAME" invalid={invalid}>
        {(aria) => (
          <input
            {...aria}
            value={details.name}
            maxLength={120}
            required
            onChange={(event) => {
              change({ name: event.target.value });
            }}
          />
        )}
      </Field>
      <Field label={messages.description} field="DESCRIPTION" invalid={invalid}>
        {(aria) => (
          <textarea
            {...aria}
            value={details.description}
            maxLength={5000}
            rows={5}
            onChange={(event) => {
              change({ description: event.target.value });
            }}
          />
        )}
      </Field>
      <Field label={messages.material} field="MATERIAL" invalid={invalid}>
        {(aria) => (
          <input
            {...aria}
            value={details.material}
            maxLength={80}
            required
            onChange={(event) => {
              change({ material: event.target.value });
            }}
          />
        )}
      </Field>
      <Field label={messages.season} field="SEASON" invalid={invalid}>
        {(aria) => (
          <select
            {...aria}
            value={details.season}
            onChange={(event) => {
              change({ season: event.target.value as Season });
            }}
          >
            {SEASONS.map((season) => (
              <option key={season} value={season}>
                {es.filters.seasons[season]}
              </option>
            ))}
          </select>
        )}
      </Field>
      <Field label={messages.audience} field="AUDIENCE" invalid={invalid}>
        {(aria) => (
          <select
            {...aria}
            value={details.audience}
            onChange={(event) => {
              change({ audience: event.target.value as Audience });
            }}
          >
            {AUDIENCES.map((audience) => (
              <option key={audience} value={audience}>
                {es.filters.audiences[audience]}
              </option>
            ))}
          </select>
        )}
      </Field>
      <Field label={messages.category} field="CATEGORY" invalid={invalid}>
        {(aria) => (
          <select
            {...aria}
            value={details.categoryId}
            required
            onChange={(event) => {
              change({ categoryId: event.target.value });
            }}
          >
            <option value="" disabled>
              {messages.pickCategory}
            </option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.active ? category.name : messages.inactiveCategory(category.name)}
              </option>
            ))}
          </select>
        )}
      </Field>
      <Feedback notice={notice} />
      <button type="submit" className="btn" disabled={saving}>
        {submitLabel}
      </button>
    </form>
  );
}
