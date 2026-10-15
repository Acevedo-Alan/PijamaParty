import { useEffect, useId, useRef, useState, type ChangeEvent, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';
import { BlurredImage } from '../../components/BlurredImage.tsx';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { trackAddPaymentInfo, trackBeginCheckout, type CheckoutForAnalytics } from '../analytics/checkoutEvents.ts';
import type { Cart, CartItem } from '../cart/cartApi.ts';
import { useCartPanel } from '../cart/CartPanelContext.tsx';
import { useCart, useRemoveCoupon } from '../cart/useCart.ts';
import { goToPayment } from '../orders/redirect.ts';
import type { PlaceOrderAnswer } from './checkoutApi.ts';
import {
  EMPTY_FORM,
  invalidFields,
  isProvince,
  PROVINCES,
  toFormFields,
  toPlaceOrderRequest,
  type FormField,
  type PaymentProvider,
  type ShippingForm,
} from './checkoutForm.ts';
import { suggestEmail } from './emailSuggestion.ts';
import { useEmptiedCart, usePaymentProviders, usePlaceOrder, useShippingCost } from './useCheckout.ts';
import './checkout.css';

type Step = 1 | 2 | 3;

/** What the checkout events carry of the cart (RNF-25): its AVAILABLE items by SKU and the server's total. */
function forAnalytics(cart: Cart): CheckoutForAnalytics {
  return {
    items: cart.items.flatMap(({ status, product, unitPriceCents, quantity }) =>
      status === 'AVAILABLE' && product !== null
        ? [{ sku: product.sku, name: product.name, size: product.size, color: product.color, unitPriceCents, quantity }]
        : [],
    ),
    totalCents: cart.totalCents,
    couponCode: cart.coupon?.status === 'APPLICABLE' ? cart.coupon.code : null,
  };
}

/** Whether every item can be bought as it is (UC-09): in stock and with no unacknowledged price change. */
function isReady(cart: Cart): boolean {
  return cart.items.every((item) => item.status === 'AVAILABLE' && item.previousUnitPriceCents === null);
}

/** Why the last confirmation did not place the order, when the page does not show it elsewhere. */
function refusalMessage(answer: PlaceOrderAnswer): string | null {
  switch (answer.status) {
    case 'emptyCart':
      return es.checkout.refused.emptyCart;
    case 'cartNotReady':
      return es.checkout.refused.cartNotReady;
    case 'couponNotApplicable':
      return es.checkout.refused.couponNotApplicable;
    case 'tooManyAttempts':
      return es.checkout.refused.tooManyAttempts;
    case 'paymentProviderNotEnabled':
      return es.checkout.refused.paymentProviderNotEnabled;
    case 'unknown':
      return es.checkout.refused.unknown;
    default:
      return null;
  }
}

/** MercadoPago is always enabled: it is the only option while the providers are being read or could not be. */
const DEFAULT_PROVIDERS: readonly PaymentProvider[] = ['MERCADOPAGO'];

/** The analytics name of a provider (RNF-25), as `payment_type`. */
const PAYMENT_TYPES: Record<PaymentProvider, string> = { MERCADOPAGO: 'MercadoPago', MODO: 'MODO' };

function Steps({ step }: { step: Step }) {
  return (
    <ol className="checkout-steps" aria-label={es.checkout.steps}>
      {([1, 2, 3] as const).map((each) => (
        <li
          key={each}
          className={each === step ? 'active' : each < step ? 'done' : undefined}
          aria-current={each === step ? 'step' : undefined}
        >
          {es.checkout.stepNames[each]}
        </li>
      ))}
    </ol>
  );
}

/** UC-09: items that cannot be bought as they are block the checkout; the cart panel fixes them. */
function CartNotice() {
  const panel = useCartPanel();
  return (
    <div className="checkout-notice" role="alert">
      <p>{es.checkout.cartNotReady}</p>
      <button type="button" className="btn btn-secondary" onClick={panel.open}>
        {es.checkout.reviewCart}
      </button>
    </div>
  );
}

interface FieldProps {
  field: FormField;
  form: ShippingForm;
  invalid: readonly FormField[];
  onChange: (field: FormField, value: string) => void;
  idOf: (field: FormField) => string;
  autoComplete: string;
  maxLength: number;
  inputMode?: 'text' | 'email' | 'tel' | 'numeric';
  type?: 'text' | 'email' | 'tel';
  hint?: string;
  required?: boolean;
  onBlur?: () => void;
  /** The id of a note under the field that also describes it, when it shows one. */
  describedBy?: string;
  /** Shown under the field, after its error. */
  children?: ReactNode;
}

function Field({
  field,
  form,
  invalid,
  onChange,
  idOf,
  autoComplete,
  maxLength,
  inputMode = 'text',
  type = 'text',
  hint,
  required = true,
  onBlur,
  describedBy,
  children,
}: FieldProps) {
  const id = idOf(field);
  const isInvalid = invalid.includes(field);
  const descriptions = [isInvalid ? `${id}-error` : null, describedBy ?? null].filter((each) => each !== null);
  return (
    <div className={isInvalid ? 'checkout-field error' : 'checkout-field'}>
      <label htmlFor={id}>
        {es.checkout.fields[field]}
        {!required && <span className="checkout-optional"> ({es.checkout.optional})</span>}
      </label>
      <input
        id={id}
        name={field}
        type={type}
        inputMode={inputMode}
        value={form[field]}
        autoComplete={autoComplete}
        maxLength={maxLength}
        placeholder={hint}
        required={required}
        aria-invalid={isInvalid}
        aria-describedby={descriptions.length > 0 ? descriptions.join(' ') : undefined}
        onChange={(event: ChangeEvent<HTMLInputElement>) => {
          onChange(field, event.target.value);
        }}
        onBlur={onBlur}
      />
      {isInvalid && (
        <p id={`${id}-error`} className="checkout-field-error">
          {es.checkout.errors[field]}
        </p>
      )}
      {children}
    </div>
  );
}

function SummaryItem({ item }: { item: CartItem }) {
  const { product } = item;
  return (
    <li className="checkout-summary-item">
      {product?.image != null ? (
        <BlurredImage
          src={product.image.src}
          srcSet={product.image.srcSet}
          sizes="44px"
          alt={es.cart.photoAlt(product.name, product.color)}
          {...(product.image.placeholder === undefined ? {} : { placeholder: product.image.placeholder })}
        />
      ) : (
        <div className="ph" aria-hidden="true" />
      )}
      <div>
        <p className="checkout-summary-name">{product?.name ?? es.cart.unknownProduct}</p>
        {product !== null && (
          <p className="checkout-summary-detail">{es.checkout.itemDetail(product.color, product.size, item.quantity)}</p>
        )}
      </div>
      <p className="checkout-summary-amount">{formatMoney(item.unitPriceCents * item.quantity)}</p>
    </li>
  );
}

function couponNote(cart: Cart): string | null {
  const { coupon } = cart;
  if (coupon === null || coupon.status === 'APPLICABLE') {
    return null;
  }
  return coupon.status === 'BELOW_MINIMUM'
    ? es.cart.couponBelowMinimum(formatMoney(coupon.missingCents))
    : es.cart.couponRefused[coupon.status];
}

interface Amounts {
  subtotalCents: number;
  discountCents: number;
  shippingCents: number | null;
  totalCents: number;
}

/** The second column: the items and the amounts as the server sent them; nothing is added up here. */
function Summary({ cart, amounts }: { cart: Cart; amounts: Amounts }) {
  const remove = useRemoveCoupon();
  const titleId = useId();
  const note = couponNote(cart);
  return (
    <aside className="checkout-summary" aria-labelledby={titleId}>
      <h2 id={titleId}>{es.checkout.summaryTitle}</h2>
      <ul className="checkout-summary-items">
        {cart.items
          .filter((item) => item.status === 'AVAILABLE')
          .map((item) => (
            <SummaryItem key={item.id} item={item} />
          ))}
      </ul>
      {cart.coupon !== null && (
        <div className="checkout-coupon">
          <div className="checkout-coupon-applied">
            <p>{es.cart.coupon(cart.coupon.code)}</p>
            <button
              type="button"
              className="link-btn"
              aria-label={es.cart.removeCouponLabel(cart.coupon.code)}
              disabled={remove.isPending}
              onClick={() => {
                remove.mutate();
              }}
            >
              {es.cart.removeCoupon}
            </button>
          </div>
          {note !== null && <p className="checkout-coupon-note">{note}</p>}
          <p className="checkout-error" role="alert">
            {remove.isError ? es.cart.couponRemoveError : null}
          </p>
        </div>
      )}
      <dl className="checkout-amounts">
        <div>
          <dt>{es.checkout.subtotal}</dt>
          <dd>{formatMoney(amounts.subtotalCents)}</dd>
        </div>
        {amounts.discountCents > 0 && (
          <div>
            <dt>{cart.coupon === null ? es.checkout.discount : es.checkout.discountWith(cart.coupon.code)}</dt>
            <dd>−{formatMoney(amounts.discountCents)}</dd>
          </div>
        )}
        <div>
          <dt>{es.checkout.shipping}</dt>
          <dd>
            {amounts.shippingCents === null
              ? es.checkout.shippingPending
              : amounts.shippingCents === 0
                ? es.checkout.free
                : formatMoney(amounts.shippingCents)}
          </dd>
        </div>
        <div className="checkout-total">
          <dt>{es.checkout.total}</dt>
          <dd>{formatMoney(amounts.totalCents)}</dd>
        </div>
      </dl>
    </aside>
  );
}

function ReviewBlock({ title, onEdit, children }: { title: string; onEdit: () => void; children: ReactNode }) {
  return (
    <section className="checkout-review-block">
      <div className="checkout-review-head">
        <h3>{title}</h3>
        <button type="button" className="link-btn" aria-label={es.checkout.editLabel(title)} onClick={onEdit}>
          {es.checkout.edit}
        </button>
      </div>
      {children}
    </section>
  );
}

function addressLine(form: ShippingForm): string {
  const parts = [`${form.street.trim()} ${form.number.trim()}`];
  if (form.floor.trim() !== '') {
    parts.push(es.checkout.floorPart(form.floor.trim()));
  }
  if (form.apartment.trim() !== '') {
    parts.push(es.checkout.apartmentPart(form.apartment.trim()));
  }
  return parts.join(', ');
}

/** The idempotency key of the last attempt whose outcome is unknown, and the body it went with. */
interface Attempt {
  body: string;
  key: string;
}

/** What should take the focus after the next render: the step's heading or a field. */
type FocusTarget = { kind: 'heading' } | { kind: 'field'; field: FormField } | { kind: 'terms' };

function CheckoutWizard({ cart, onLeave }: { cart: Cart; onLeave: () => void }) {
  const navigate = useNavigate();
  const place = usePlaceOrder();
  const emptied = useEmptiedCart();
  const [step, setStep] = useState<Step>(1);
  const [form, setForm] = useState<ShippingForm>(EMPTY_FORM);
  const [invalid, setInvalid] = useState<readonly FormField[]>([]);
  const [chosenProvider, setChosenProvider] = useState<PaymentProvider>('MERCADOPAGO');
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [termsError, setTermsError] = useState(false);
  const [refusal, setRefusal] = useState<string | null>(null);
  // The target waits in a ref until the render that shows it; the counter asks for that render.
  const focusTarget = useRef<FocusTarget | null>(null);
  const [focusRequests, setFocusRequests] = useState(0);
  const attempt = useRef<Attempt | null>(null);
  const paymentInfoSent = useRef(false);
  // T-E3-24: the email suggested under the field, and the typed email whose suggestion already stopped going on.
  const [suggestion, setSuggestion] = useState<string | null>(null);
  const suggestionStopped = useRef<string | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const prefix = useId();
  const idOf = (field: FormField) => `${prefix}-${field}`;
  const termsId = `${prefix}-terms`;
  const shipping = useShippingCost(form.province, cart);
  const providers = usePaymentProviders().data ?? DEFAULT_PROVIDERS;
  // A provider no longer offered falls back to MercadoPago.
  const provider = providers.includes(chosenProvider) ? chosenProvider : 'MERCADOPAGO';
  const ready = isReady(cart);

  useEffect(() => {
    const target = focusTarget.current;
    focusTarget.current = null;
    if (target === null) {
      return;
    }
    if (target.kind === 'heading') {
      heading.current?.focus();
    } else {
      document.getElementById(target.kind === 'terms' ? `${prefix}-terms` : `${prefix}-${target.field}`)?.focus();
    }
  }, [focusRequests, prefix]);

  function setFocusTarget(target: FocusTarget) {
    focusTarget.current = target;
    setFocusRequests((count) => count + 1);
  }

  function goTo(next: Step) {
    setStep(next);
    setRefusal(null);
    setFocusTarget({ kind: 'heading' });
  }

  function change(field: FormField, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
    setInvalid((current) => current.filter((each) => each !== field));
    if (field === 'email') {
      setSuggestion(null);
    }
  }

  function applySuggestion(email: string) {
    change('email', email);
    setFocusTarget({ kind: 'field', field: 'email' });
  }

  function changeProvince(event: ChangeEvent<HTMLSelectElement>) {
    const { value } = event.target;
    setForm((current) => ({ ...current, province: isProvince(value) ? value : '' }));
    setInvalid((current) => current.filter((each) => each !== 'province'));
  }

  function toPayment() {
    const failing = invalidFields(form);
    if (failing.length > 0) {
      setInvalid(failing);
      const [first] = failing;
      if (first !== undefined) {
        setFocusTarget({ kind: 'field', field: first });
      }
      return;
    }
    // Once per typed email: a second press goes on with it as typed.
    const suggested = suggestEmail(form.email);
    if (suggested !== null && suggestionStopped.current !== form.email) {
      suggestionStopped.current = form.email;
      setSuggestion(suggested);
      setFocusTarget({ kind: 'field', field: 'email' });
      return;
    }
    if (shipping.data?.status === 'noShipping') {
      setFocusTarget({ kind: 'field', field: 'province' });
      return;
    }
    if (ready && shipping.data?.status === 'quoted') {
      goTo(2);
    }
  }

  function toReview() {
    // RNF-25: the first time in the visit only.
    if (!paymentInfoSent.current) {
      paymentInfoSent.current = true;
      trackAddPaymentInfo(forAnalytics(cart), PAYMENT_TYPES[provider]);
    }
    goTo(3);
  }

  function settle(answer: PlaceOrderAnswer) {
    // Only an attempt without an answer repeats its key; any answer ends the attempt.
    if (answer.status !== 'unknown') {
      attempt.current = null;
    }
    switch (answer.status) {
      case 'placed':
        onLeave();
        emptied();
        if (answer.order.paymentUrl === null) {
          void navigate(`/pedido/${answer.order.trackingToken}`, { replace: true });
        } else {
          goToPayment(answer.order.paymentUrl);
        }
        return;
      case 'invalidBuyerData': {
        const fields = toFormFields(answer.fields);
        setInvalid(fields);
        setStep(1);
        const [first] = fields;
        setFocusTarget(first === undefined ? { kind: 'heading' } : { kind: 'field', field: first });
        return;
      }
      case 'noShipping':
        setStep(1);
        setFocusTarget({ kind: 'field', field: 'province' });
        return;
      case 'termsNotAccepted':
        setAcceptedTerms(false);
        setTermsError(true);
        setFocusTarget({ kind: 'terms' });
        return;
      case 'paymentProviderNotEnabled':
        setChosenProvider('MERCADOPAGO');
        setStep(2);
        setRefusal(refusalMessage(answer));
        setFocusTarget({ kind: 'heading' });
        return;
      default:
        setRefusal(refusalMessage(answer));
    }
  }

  function confirm() {
    if (!acceptedTerms) {
      setTermsError(true);
      setFocusTarget({ kind: 'terms' });
      return;
    }
    if (!isProvince(form.province)) {
      goTo(1);
      return;
    }
    const body = toPlaceOrderRequest(form, form.province, provider, true);
    const json = JSON.stringify(body);
    const current = attempt.current?.body === json ? attempt.current : { body: json, key: crypto.randomUUID() };
    attempt.current = current;
    setRefusal(null);
    place.mutate({ key: current.key, body }, { onSuccess: settle });
  }

  const quoted = shipping.data?.status === 'quoted' ? shipping.data.cost : null;
  const amounts: Amounts = quoted ?? {
    subtotalCents: cart.subtotalCents,
    discountCents: cart.discountCents,
    shippingCents: null,
    totalCents: cart.totalCents,
  };
  const fieldProps = { form, invalid, onChange: change, idOf };
  const provinceInvalid = invalid.includes('province');
  const noShipping = form.province !== '' && shipping.data?.status === 'noShipping';
  const provinceId = idOf('province');
  const suggestionId = `${idOf('email')}-suggestion`;

  return (
    <div className="checkout-layout">
      <div className="checkout-main">
        <Steps step={step} />
        {!ready && <CartNotice />}

        {step === 1 && (
          <section aria-labelledby={`${prefix}-heading`}>
            <h2 id={`${prefix}-heading`} ref={heading} tabIndex={-1}>
              {es.checkout.shippingTitle}
            </h2>
            <form
              noValidate
              onSubmit={(event) => {
                event.preventDefault();
                toPayment();
              }}
            >
              <Field field="name" {...fieldProps} autoComplete="name" maxLength={100} />
              <Field
                field="email"
                {...fieldProps}
                type="email"
                inputMode="email"
                autoComplete="email"
                maxLength={254}
                onBlur={() => {
                  setSuggestion(suggestEmail(form.email));
                }}
                {...(suggestion === null ? {} : { describedBy: suggestionId })}
              >
                <p id={suggestionId} className="checkout-note" aria-live="polite">
                  {suggestion !== null && (
                    <>
                      {es.checkout.emailSuggestion}{' '}
                      <button
                        type="button"
                        className="link-btn"
                        onClick={() => {
                          applySuggestion(suggestion);
                        }}
                      >
                        {suggestion}
                      </button>
                      ?
                    </>
                  )}
                </p>
              </Field>
              <div className="checkout-row">
                <Field
                  field="phone"
                  {...fieldProps}
                  type="tel"
                  inputMode="tel"
                  autoComplete="tel"
                  maxLength={25}
                  hint={es.checkout.phoneHint}
                />
                <Field field="nationalId" {...fieldProps} inputMode="numeric" autoComplete="off" maxLength={12} />
              </div>
              <div className="checkout-row checkout-row-street">
                <Field field="street" {...fieldProps} autoComplete="address-line1" maxLength={100} />
                <Field field="number" {...fieldProps} autoComplete="off" maxLength={10} />
              </div>
              <div className="checkout-row">
                <Field field="floor" {...fieldProps} autoComplete="off" maxLength={10} required={false} />
                <Field field="apartment" {...fieldProps} autoComplete="off" maxLength={10} required={false} />
              </div>
              <div className="checkout-row">
                <Field field="city" {...fieldProps} autoComplete="address-level2" maxLength={80} />
                <Field field="postalCode" {...fieldProps} autoComplete="postal-code" maxLength={8} />
              </div>
              <div className={provinceInvalid || noShipping ? 'checkout-field error' : 'checkout-field'}>
                <label htmlFor={provinceId}>{es.checkout.fields.province}</label>
                <select
                  id={provinceId}
                  name="province"
                  value={form.province}
                  autoComplete="address-level1"
                  required
                  aria-invalid={provinceInvalid || noShipping}
                  aria-describedby={`${provinceId}-status`}
                  onChange={changeProvince}
                >
                  <option value="">{es.checkout.chooseProvince}</option>
                  {PROVINCES.map((province) => (
                    <option key={province} value={province}>
                      {es.provinces[province]}
                    </option>
                  ))}
                </select>
                <p id={`${provinceId}-status`} className="checkout-field-error" aria-live="polite">
                  {provinceInvalid ? es.checkout.errors.province : noShipping ? es.checkout.noShipping : null}
                </p>
                {form.province !== '' && shipping.isFetching && (
                  <p className="checkout-note">{es.checkout.shippingLoading}</p>
                )}
                {shipping.isError && (
                  <p className="checkout-error" role="alert">
                    {es.checkout.shippingError}{' '}
                    <button type="button" className="link-btn" onClick={() => void shipping.refetch()}>
                      {es.checkout.retry}
                    </button>
                  </p>
                )}
              </div>
              <button type="submit" className="btn checkout-next" disabled={!ready}>
                {es.checkout.toPayment}
              </button>
            </form>
          </section>
        )}

        {step === 2 && (
          <section aria-labelledby={`${prefix}-heading`}>
            <h2 id={`${prefix}-heading`} ref={heading} tabIndex={-1}>
              {es.checkout.paymentTitle}
            </h2>
            <fieldset className="checkout-payment">
              <legend className="visually-hidden">{es.checkout.paymentTitle}</legend>
              {providers.map((each) => (
                <label key={each} className={each === provider ? 'checkout-pay-option active' : 'checkout-pay-option'}>
                  <input
                    type="radio"
                    name="paymentProvider"
                    value={each}
                    checked={each === provider}
                    onChange={() => {
                      setChosenProvider(each);
                      setRefusal(null);
                    }}
                  />
                  <span>
                    <span className={each === 'MODO' ? 'checkout-pay-name checkout-pay-modo' : 'checkout-pay-name'}>
                      {es.checkout.providers[each].name}
                    </span>
                    <span className="checkout-pay-hint">{es.checkout.providers[each].hint}</span>
                  </span>
                </label>
              ))}
            </fieldset>
            <p className="checkout-error" role="alert">
              {refusal}
            </p>
            <div className="checkout-actions">
              <button
                type="button"
                className="link-btn"
                onClick={() => {
                  goTo(1);
                }}
              >
                {es.checkout.back}
              </button>
              <button
                type="button"
                className="btn checkout-next"
                disabled={!ready}
                onClick={toReview}
              >
                {es.checkout.toReview}
              </button>
            </div>
          </section>
        )}

        {step === 3 && (
          <section aria-labelledby={`${prefix}-heading`}>
            <h2 id={`${prefix}-heading`} ref={heading} tabIndex={-1}>
              {es.checkout.reviewTitle}
            </h2>
            <ReviewBlock
              title={es.checkout.contactAndShipping}
              onEdit={() => {
                goTo(1);
              }}
            >
              <p>{form.name.trim()}</p>
              <p>{form.email.trim()}</p>
              <p>{form.phone.trim()}</p>
              <p>{es.checkout.nationalIdLine(form.nationalId.trim())}</p>
              <p>{addressLine(form)}</p>
              {isProvince(form.province) && (
                <p>{es.checkout.place(form.city.trim(), es.provinces[form.province], form.postalCode.trim().toUpperCase())}</p>
              )}
            </ReviewBlock>
            <ReviewBlock
              title={es.checkout.paymentMethod}
              onEdit={() => {
                goTo(2);
              }}
            >
              <p>{es.checkout.providers[provider].name}</p>
            </ReviewBlock>
            <div className={termsError ? 'checkout-terms error' : 'checkout-terms'}>
              <input
                id={termsId}
                type="checkbox"
                checked={acceptedTerms}
                aria-invalid={termsError}
                aria-describedby={termsError ? `${termsId}-error` : undefined}
                onChange={(event) => {
                  setAcceptedTerms(event.target.checked);
                  setTermsError(false);
                }}
              />
              <label htmlFor={termsId}>
                {es.checkout.termsBefore}
                <Link to="/terminos" target="_blank" rel="noopener">
                  {es.checkout.terms}
                </Link>
                {es.checkout.termsBetween}
                <Link to="/cambios-y-devoluciones" target="_blank" rel="noopener">
                  {es.checkout.returns}
                </Link>
              </label>
              {termsError && (
                <p id={`${termsId}-error`} className="checkout-field-error">
                  {es.checkout.termsRequired}
                </p>
              )}
            </div>
            <p className="checkout-error" role="alert">
              {refusal}
            </p>
            <div className="checkout-actions">
              <button
                type="button"
                className="link-btn"
                onClick={() => {
                  goTo(2);
                }}
              >
                {es.checkout.back}
              </button>
              <button
                type="button"
                className="btn checkout-next"
                disabled={!ready || place.isPending}
                onClick={confirm}
              >
                {place.isPending ? es.checkout.confirming : es.checkout.confirm}
              </button>
            </div>
          </section>
        )}
      </div>
      <Summary cart={cart} amounts={amounts} />
    </div>
  );
}

/** UC-13 (T-E3-11): the checkout at /checkout, in three steps, over the visitor's cart as the server prices it. */
export function CheckoutPage() {
  const cart = useCart();
  const [leaving, setLeaving] = useState(false);
  const checkoutBegun = useRef(false);

  // RNF-25: once per visit, as soon as the cart has something to buy.
  useEffect(() => {
    if (checkoutBegun.current || cart.data === undefined) {
      return;
    }
    const checkout = forAnalytics(cart.data);
    if (checkout.items.length > 0) {
      checkoutBegun.current = true;
      trackBeginCheckout(checkout);
    }
  }, [cart.data]);

  // Back from the provider with the page restored as it was left (back-forward cache): it shows the cart again.
  useEffect(() => {
    function restored(event: PageTransitionEvent) {
      if (event.persisted) {
        setLeaving(false);
      }
    }
    window.addEventListener('pageshow', restored);
    return () => {
      window.removeEventListener('pageshow', restored);
    };
  }, []);

  if (leaving) {
    return (
      <main className="wrap checkout">
        <p className="checkout-note" role="status">
          {es.checkout.openingPayment}
        </p>
      </main>
    );
  }

  return (
    <main className="wrap checkout">
      <title>{`${es.checkout.title} | ${es.store.name}`}</title>
      <Link to="/" className="checkout-escape">
        {es.checkout.backToStore}
      </Link>
      <h1>{es.checkout.title}</h1>
      {cart.isPending && (
        <p className="checkout-note" role="status">
          {es.checkout.loading}
        </p>
      )}
      {cart.isError && (
        <div className="empty-state" role="alert">
          <p>{es.checkout.loadError}</p>
          <button type="button" className="btn" onClick={() => void cart.refetch()}>
            {es.checkout.retry}
          </button>
        </div>
      )}
      {cart.data !== undefined && cart.data.items.length === 0 && (
        <div className="checkout-empty">
          <p>{es.checkout.empty}</p>
          <p>{es.checkout.emptyHint}</p>
          <Link to="/" className="btn">
            {es.checkout.goShopping}
          </Link>
        </div>
      )}
      {cart.data !== undefined && cart.data.items.length > 0 && (
        <CheckoutWizard
          cart={cart.data}
          onLeave={() => {
            setLeaving(true);
          }}
        />
      )}
    </main>
  );
}
