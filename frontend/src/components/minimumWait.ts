/** E7 motion: a button's progress bar shows at least this long, so it is seen even on a quick answer. */
export const PROGRESS_MIN_MS = 600;

/** The answer of `work`, given no sooner than `ms` after the call. */
export async function atLeast<T>(work: Promise<T>, ms: number): Promise<T> {
  const pause = new Promise<void>((resolve) => {
    setTimeout(resolve, ms);
  });
  try {
    return await work;
  } finally {
    await pause;
  }
}
