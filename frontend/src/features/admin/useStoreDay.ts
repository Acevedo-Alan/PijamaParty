import { useEffect, useState } from 'react';
import { storeDay } from '../../i18n/formatDate.ts';

const CHECK_EVERY_MS = 60_000;

/**
 * Today in the store's zone (UC-33), kept current: a screen left open past midnight, with the 5-minute refresh on,
 * moves on to the new day instead of keeping the labels and date limits of the day it opened (T-E8-23).
 */
export function useStoreDay(): string {
  const [today, setToday] = useState(() => storeDay(new Date()));
  useEffect(() => {
    const timer = setInterval(() => {
      setToday(storeDay(new Date()));
    }, CHECK_EVERY_MS);
    return () => {
      clearInterval(timer);
    };
  }, []);
  return today;
}
