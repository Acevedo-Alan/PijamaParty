/** Leaves the store for a provider, which comes back to it (UC-27). Its own module so tests can watch it. */
export function goToProvider(url: string): void {
  window.location.assign(url);
}
