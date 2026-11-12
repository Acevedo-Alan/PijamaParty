import { useId, type ComponentProps, type ReactNode } from 'react';
import { Link, type LinkProps } from 'react-router';
import { accountIcons, type AccountIcon } from './accountIcons.tsx';

/**
 * T-E7-63: the grouped lists of the account pages, after Apple's grouped tables. A section is a small caption over a
 * white card whose rows are split by hairlines; each kind of row is its own component (composition, not modes):
 * a field reads, an action row is the whole row as a button or a link, with its icon and a chevron.
 */

function Chevron() {
  return (
    <svg className="group-chevron" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path d="m9.5 6 6 6-6 6" />
    </svg>
  );
}

interface SectionProps {
  title: string;
  children: ReactNode;
}

/** The caption, then the card. The caption names the section for assistive technology. */
function Section({ title, children }: SectionProps) {
  const id = useId();
  return (
    <section className="group" aria-labelledby={id}>
      <h2 id={id} className="group-caption">
        {title}
      </h2>
      {children}
    </section>
  );
}

/** The white card of a section; its direct children are the rows. */
function Card({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={className === undefined ? 'group-card' : `group-card ${className}`}>{children}</div>;
}

/** A read-only row: the label above its value. */
function Field({ label, children }: { label: ReactNode; children: ReactNode }) {
  return (
    <dl className="group-row group-field">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </dl>
  );
}

/** What an empty field says instead of its value. */
function Missing({ children }: { children: string }) {
  return <span className="group-missing">{children}</span>;
}

/** Rows that belong together inside a card (a provider's state and its action), named as a group; no box of its own. */
function Rows({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="group-rows" role="group" aria-label={label}>
      {children}
    </div>
  );
}

/** A row holding anything else (a form, a confirmation), with the card's padding. */
function Row({ children }: { children: ReactNode }) {
  return <div className="group-row">{children}</div>;
}

type ButtonRowProps = Omit<ComponentProps<'button'>, 'className' | 'type' | 'children'> & {
  icon: AccountIcon;
  children: ReactNode;
};

type LinkRowProps = Omit<LinkProps, 'className' | 'children'> & { icon: AccountIcon; children: ReactNode };

function rowContent(icon: AccountIcon, children: ReactNode, chevron: boolean) {
  return (
    <>
      <span className="group-icon">{accountIcons[icon]}</span>
      <span className="group-label">{children}</span>
      {chevron && <Chevron />}
    </>
  );
}

/** An action in the store's rose, as every action row (T-E7-63): "Agregar teléfono", "Vincular", "Editar". */
function ActionButton({ icon, children, ...button }: ButtonRowProps) {
  return (
    <button type="button" className="group-row group-action" {...button}>
      {rowContent(icon, children, true)}
    </button>
  );
}

/** "Eliminar…": in --danger and without a chevron, as it leads nowhere. */
function DestructiveButton({ icon, children, ...button }: ButtonRowProps) {
  return (
    <button type="button" className="group-row group-action group-destructive" {...button}>
      {rowContent(icon, children, false)}
    </button>
  );
}

/**
 * An action that just did its work and waits before it can again (T-E7-63): a check and its text in --success, no
 * chevron, and not a button, so there is nothing to press twice.
 */
function Done({ children }: { children: ReactNode }) {
  return <p className="group-row group-action group-done">{rowContent('check', children, false)}</p>;
}

/** A link in the store's rose: "Ver todos mis pedidos", "Administrar direcciones". */
function ActionLink({ icon, children, ...link }: LinkRowProps) {
  return (
    <Link className="group-row group-action" {...link}>
      {rowContent(icon, children, true)}
    </Link>
  );
}

/** A whole row that opens something, with free content in --ink and a chevron: an order of "Pedidos recientes". */
function RowLink({ children, ...link }: Omit<LinkProps, 'className' | 'children'> & { children: ReactNode }) {
  return (
    <Link className="group-row group-row-link" {...link}>
      <span className="group-row-main">{children}</span>
      <Chevron />
    </Link>
  );
}

/** The small text under a card. */
function Footnote({ children }: { children: ReactNode }) {
  return <p className="group-footnote">{children}</p>;
}

export const Group = {
  Section,
  Card,
  Field,
  Missing,
  Rows,
  Row,
  ActionButton,
  DestructiveButton,
  Done,
  ActionLink,
  RowLink,
  Footnote,
};
