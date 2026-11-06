import { useQuery } from '@tanstack/react-query';
import { Search } from 'lucide-react';
import { useEffect, useId, useRef, useState, type ReactNode, type SyntheticEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { PanelDialog } from '../../components/motion/PanelDialog.tsx';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { fetchCustomers } from './customersApi.ts';
import { fetchOrders } from './ordersApi.ts';
import { fetchProductSummaries, NO_PRODUCT_FILTER } from './productsApi.ts';

const messages = esAdmin.search;

/** What each list accepts as its search (UC-18, UC-06, UC-34): 2 to 100 characters, counted as the server counts them. */
const MIN_LENGTH = 2;
const MAX_LENGTH = 100;
const SHOWN = 5;
const DEBOUNCE_MS = 250;

function searchable(typed: string): boolean {
  const length = Array.from(typed.trim()).length;
  return length >= MIN_LENGTH && length <= MAX_LENGTH;
}

function useDebounced(value: string): string {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebounced(value);
    }, DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
    };
  }, [value]);
  return debounced;
}

/** True when the key press happens in a field of the page other than the search box, where Ctrl+K is the user's. */
function typingElsewhere(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)) &&
    !target.hasAttribute('data-admin-search')
  );
}

interface Match {
  id: string;
  to: string;
  label: ReactNode;
}

/** One group of results: loading, failed, none, or up to five links. */
function Group({ title, matches, pending, failed }: {
  title: string;
  matches: readonly Match[] | undefined;
  pending: boolean;
  failed: boolean;
}) {
  const titleId = useId();
  return (
    <section aria-labelledby={titleId} className="admin-search-group">
      <h3 id={titleId}>{title}</h3>
      {pending && <p className="admin-search-note">{messages.searching}</p>}
      {failed && <p className="admin-search-note" role="alert">{messages.failed}</p>}
      {matches !== undefined && matches.length === 0 && <p className="admin-search-note">{messages.none}</p>}
      {matches !== undefined && matches.length > 0 && (
        <ul>
          {matches.map(({ id, to, label }) => (
            <li key={id}>
              <Link to={to} data-admin-search-result>
                {label}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/**
 * UC-39: the box and the three groups of results. Mounted only while the dialog is open, so what was typed is gone
 * when it closes (search text is personal data, R-05: never kept).
 */
function SearchBody({ onChoose }: { onChoose: () => void }) {
  const [typed, setTyped] = useState('');
  const q = useDebounced(typed.trim());
  const ready = searchable(q) && q === typed.trim();
  const navigate = useNavigate();
  const box = useRef<HTMLInputElement>(null);
  const inputId = useId();

  // After the dialog is open: it would otherwise focus its first control, the close button.
  useEffect(() => {
    box.current?.focus();
  }, []);

  const orders = useQuery({
    queryKey: ['admin', 'search', 'orders', q],
    queryFn: ({ signal }) => fetchOrders({ status: null, q, from: null, to: null }, 0, signal),
    enabled: searchable(q),
    staleTime: 30_000,
  });
  const products = useQuery({
    queryKey: ['admin', 'search', 'products', q],
    queryFn: ({ signal }) => fetchProductSummaries({ ...NO_PRODUCT_FILTER, q }, 0, signal),
    enabled: searchable(q),
    staleTime: 30_000,
  });
  const customers = useQuery({
    queryKey: ['admin', 'search', 'customers', q],
    queryFn: ({ signal }) => fetchCustomers({ q, from: null, to: null }, 0, signal),
    enabled: searchable(q),
    staleTime: 30_000,
  });

  const orderMatches = orders.data?.items.slice(0, SHOWN).map(({ id, number, buyerName }) => ({
    id,
    to: `/admin/pedidos/${id}`,
    label: `#${String(number)} · ${buyerName}`,
  }));
  const productMatches = products.data?.items.slice(0, SHOWN).map(({ id, name, published }) => ({
    id,
    to: `/admin/productos/${id}`,
    label: published ? name : `${name} (${messages.hidden})`,
  }));
  const customerMatches = customers.data?.items.slice(0, SHOWN).map(({ id, name, email }) => ({
    id,
    to: `/admin/clientes/${id}`,
    label: `${name} · ${email}`,
  }));

  // Enter goes to the first match, in the order the groups show.
  function openFirst(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const first = [orderMatches, productMatches, customerMatches].find((group) => group !== undefined && group.length > 0)?.[0];
    if (first !== undefined) {
      onChoose();
      void navigate(first.to);
    }
  }

  return (
    <form role="search" onSubmit={openFirst} className="admin-search-body">
      <label htmlFor={inputId} className="visually-hidden">
        {messages.label}
      </label>
      <input
        ref={box}
        id={inputId}
        data-admin-search=""
        type="search"
        name="q"
        autoComplete="off"
        spellCheck={false}
        maxLength={MAX_LENGTH}
        placeholder={messages.placeholder}
        value={typed}
        onChange={(event) => {
          setTyped(event.target.value);
        }}
      />
      <div
        onClick={(event) => {
          if (event.target instanceof Element && event.target.closest('[data-admin-search-result]') !== null) {
            onChoose();
          }
        }}
      >
        {typed.trim() === '' || !searchable(typed) ? (
          <p className="admin-search-note" role="status">
            {typed.trim() === '' ? messages.hint : messages.tooShort}
          </p>
        ) : (
          <>
            <Group title={messages.groups.orders} matches={orderMatches} pending={ready ? orders.isPending : true} failed={orders.isError} />
            <Group title={messages.groups.products} matches={productMatches} pending={ready ? products.isPending : true} failed={products.isError} />
            <Group title={messages.groups.customers} matches={customerMatches} pending={ready ? customers.isPending : true} failed={customers.isError} />
          </>
        )}
      </div>
    </form>
  );
}

/**
 * UC-39: the search button of the top bar and the dialog it opens, also with Ctrl+K (Cmd+K on Mac). It searches
 * orders, products and customers with the lists' own search; nothing of it is stored.
 */
export function AdminSearch() {
  const [open, setOpen] = useState(false);
  const titleId = useId();
  const opener = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && !typingElsewhere(event.target)) {
        event.preventDefault();
        setOpen(true);
      }
    }
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('keydown', onKey);
    };
  }, []);

  function close() {
    setOpen(false);
  }

  return (
    <>
      <button
        ref={opener}
        type="button"
        className="admin-search-btn"
        aria-haspopup="dialog"
        aria-keyshortcuts="Control+K Meta+K"
        aria-label={messages.open}
        onClick={() => {
          setOpen(true);
        }}
      >
        <Search aria-hidden="true" />
        <span>{messages.open}</span>
        <kbd aria-hidden="true">Ctrl K</kbd>
      </button>
      <PanelDialog open={open} from="bottom" className="admin-search-panel" labelledBy={titleId} onDismiss={close}
        onClosed={() => {
          opener.current?.focus();
        }}
      >
        <div className="admin-menu-head">
          <h2 id={titleId}>{messages.title}</h2>
          <button type="button" className="icon-btn" aria-label={esAdmin.closeSearch} onClick={close}>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M6 6l12 12M18 6 6 18" />
            </svg>
          </button>
        </div>
        <SearchBody onChoose={close} />
      </PanelDialog>
    </>
  );
}
