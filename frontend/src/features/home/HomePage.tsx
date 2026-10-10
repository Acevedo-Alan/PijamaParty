import { es } from '../../i18n/es.ts';

/** Placeholder home until E1 builds the catalog. */
export function HomePage() {
  return (
    <main className="wrap">
      <h1>{es.home.title}</h1>
      <p>{es.home.comingSoon}</p>
    </main>
  );
}
