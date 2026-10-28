import { describe, expect, it } from 'vitest';
import { capitalizeWords } from './capitalizeWords.ts';

describe('capitalizeWords (UC-13, UC-14)', () => {
  it.each([
    ['alan', 'Alan'],
    ['santa fe', 'Santa Fe'],
    ['maría josé', 'María José'],
    ['maría-josé', 'María-José'],
    ['san josé de la esquina', 'San José de la Esquina'],
    ['de la cruz', 'De la Cruz'],
    ['villa  del rosario', 'Villa  del Rosario'],
    ['McDonald', 'McDonald'],
    ['ROSARIO', 'ROSARIO'],
    ['', ''],
  ])('given "%s", when shown, then it reads "%s"', (typed, shown) => {
    expect(capitalizeWords(typed)).toBe(shown);
  });
});
