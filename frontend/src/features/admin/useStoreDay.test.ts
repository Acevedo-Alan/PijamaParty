import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useStoreDay } from './useStoreDay.ts';

describe('useStoreDay (T-E8-23)', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('given a screen open at 23:59 in Argentina, when midnight passes, then today becomes the next day', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-06T02:59:30Z')); // 23:59:30 of the 5th in Argentina (UTC-3)
    const { result } = renderHook(() => useStoreDay());
    expect(result.current).toBe('2026-11-05');

    act(() => {
      vi.advanceTimersByTime(60_000);
    });

    expect(result.current).toBe('2026-11-06');
  });
});
