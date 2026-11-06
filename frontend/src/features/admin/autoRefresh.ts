import { useState } from 'react';

/**
 * "Actualizar cada 5 minutos" (UC-33, T-E8-20): off by default, remembered in this browser only. The key carries a
 * version: a later meaning of the stored value gets a new key, and the old one is simply never read again (T-E8-14).
 */
const KEY = 'pp-admin-auto-refresh:v1';

function browserStorage(): Storage | null {
  try {
    return localStorage;
  } catch {
    return null;
  }
}

function read(): boolean {
  try {
    return browserStorage()?.getItem(KEY) === 'on';
  } catch {
    return false;
  }
}

/** The switch's state; without storage (a private window, blocked site data) it still works, just not remembered. */
export function useAutoRefresh(): [boolean, (on: boolean) => void] {
  const [on, setOn] = useState(read);
  const change = (next: boolean) => {
    setOn(next);
    try {
      if (next) {
        browserStorage()?.setItem(KEY, 'on');
      } else {
        browserStorage()?.removeItem(KEY);
      }
    } catch {
      // Not remembered: the switch still works for this visit.
    }
  };
  return [on, change];
}
