import type { ReactNode } from 'react';

/**
 * T-E7-63: the line drawings of the account's action rows, in the header's style (24 px box, 1.6 px stroke set by
 * account.css). All decorative: each row's text names it.
 */
function Icon({ children }: { children: ReactNode }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      {children}
    </svg>
  );
}

export const accountIcons = {
  phone: (
    <Icon>
      <path d="M7 3.5h3l1.5 4-2 1.5a10 10 0 0 0 5.5 5.5l1.5-2 4 1.5v3a2 2 0 0 1-2 2A15.5 15.5 0 0 1 5 5.5a2 2 0 0 1 2-2Z" />
    </Icon>
  ),
  key: (
    <Icon>
      <circle cx="8" cy="14" r="4" />
      <path d="m11 11 8-8M16 6l2.5 2.5M14 8l2 2" />
    </Icon>
  ),
  mail: (
    <Icon>
      <rect x="3.5" y="5.5" width="17" height="13" rx="2" />
      <path d="m4 7 8 6 8-6" />
    </Icon>
  ),
  link: (
    <Icon>
      <path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1" />
    </Icon>
  ),
  unlink: (
    <Icon>
      <path d="M14.5 6.5 16 5a3.5 3.5 0 0 1 5 5l-1.5 1.5M9.5 17.5 8 19a3.5 3.5 0 0 1-5-5l1.5-1.5M5 5l14 14" />
    </Icon>
  ),
  download: (
    <Icon>
      <path d="M12 4v11m-4.5-4.5L12 15l4.5-4.5M5 19.5h14" />
    </Icon>
  ),
  trash: (
    <Icon>
      <path d="M4.5 7h15M10 4h4M6.5 7l1 12.5h9l1-12.5M10 11v5M14 11v5" />
    </Icon>
  ),
  star: (
    <Icon>
      <path d="m12 4 2.4 5 5.3.6-4 3.6 1.1 5.3L12 15.8l-4.8 2.7 1.1-5.3-4-3.6 5.3-.6Z" />
    </Icon>
  ),
  pencil: (
    <Icon>
      <path d="M4.5 19.5 5.5 15 15 5.5a2.1 2.1 0 0 1 3 3L8.5 18Zm9-12.5 3 3" />
    </Icon>
  ),
  check: (
    <Icon>
      <path d="m5 12.5 4.5 4.5L19 7.5" />
    </Icon>
  ),
  plus: (
    <Icon>
      <path d="M12 5v14M5 12h14" />
    </Icon>
  ),
  parcel: (
    <Icon>
      <path d="M4 8 12 4l8 4v8l-8 4-8-4V8Zm0 0 8 4 8-4M12 12v8" />
    </Icon>
  ),
  truck: (
    <Icon>
      <path d="M3 6.5h11v9H3zM14 9.5h4l3 3v3h-7M7.5 18.5a1.8 1.8 0 1 0 0-.1M17.5 18.5a1.8 1.8 0 1 0 0-.1" />
    </Icon>
  ),
  pin: (
    <Icon>
      <path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11Z" />
      <circle cx="12" cy="10" r="2.3" />
    </Icon>
  ),
  person: (
    <Icon>
      <circle cx="12" cy="8.5" r="3.5" />
      <path d="M5 19.5c1.2-3.3 3.8-5 7-5s5.8 1.7 7 5" />
    </Icon>
  ),
  copy: (
    <Icon>
      <rect x="8.5" y="8.5" width="11" height="11" rx="2" />
      <path d="M15.5 8.5V6.5a2 2 0 0 0-2-2h-7a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h2" />
    </Icon>
  ),
  external: (
    <Icon>
      <path d="M13.5 4.5h6v6M19.5 4.5 11 13M17 14v4.5a1 1 0 0 1-1 1H5.5a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1H10" />
    </Icon>
  ),
};

export type AccountIcon = keyof typeof accountIcons;
