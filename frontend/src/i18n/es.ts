/**
 * User-facing texts (Spanish). Code, routes and identifiers stay in English; only these strings are shown to
 * customers (CLAUDE.md "Language"). Keep one entry per text so a future translation tool can take this file as is.
 */
export const es = {
  store: {
    name: 'Pijama Party Santa Fe',
  },
  home: {
    title: 'Pijama Party Santa Fe',
    comingSoon: 'Estamos preparando la nueva tienda.',
  },
  notFound: {
    title: 'No encontramos esta página',
    body: 'Puede que el enlace esté mal escrito o que la página ya no exista.',
    backToStore: 'Volver a la tienda',
  },
} as const;

export type Messages = typeof es;
