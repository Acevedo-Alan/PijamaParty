import { describe, expect, it } from 'vitest';
import { suggestEmail } from './emailSuggestion.ts';

describe('misspelled email domain (UC-13, T-E3-24)', () => {
  it.each([
    ['ana@gmial.com', 'ana@gmail.com'],
    ['ana@gmail.con', 'ana@gmail.com'],
    ['ana@gmai.com', 'ana@gmail.com'],
    ['ana@hotmal.com', 'ana@hotmail.com'],
    ['ana@hotmail.com.ra', 'ana@hotmail.com.ar'],
    ['ana@yahoo.com.ra', 'ana@yahoo.com.ar'],
    ['ana@outlok.com', 'ana@outlook.com'],
    ['ana@outlook.com.a', 'ana@outlook.com.ar'],
    ['ana@icluod.com', 'ana@icloud.com'],
    ['ana@liev.com', 'ana@live.com'],
  ])('given %j, when checked, then %j is suggested', (email, suggestion) => {
    expect(suggestEmail(email)).toBe(suggestion);
  });

  it('given a misspelled domain typed with spaces and capitals, when checked, then the suggestion is trimmed and in lower case', () => {
    expect(suggestEmail('  Ana.Perez@GMIAL.com ')).toBe('ana.perez@gmail.com');
  });

  it.each(['ana@gmail.com', 'ana@hotmail.com.ar', 'ana@outlook.com.ar', 'ana@yahoo.com', 'ana@icloud.com'])(
    'given the common domain of %j, when checked, then nothing is suggested',
    (email) => {
      expect(suggestEmail(email)).toBeNull();
    },
  );

  it.each(['ana@example.com', 'ana@santafe.gov.ar', 'ana@fibertel.com.ar', 'ana@gmx.com'])(
    'given %j, more than 2 edits from every common domain, when checked, then nothing is suggested',
    (email) => {
      expect(suggestEmail(email)).toBeNull();
    },
  );

  it.each(['', 'ana', 'ana@', '@gmial.com', 'ana @gmial.com'])(
    'given %j, which breaks the email rule, when checked, then nothing is suggested',
    (email) => {
      expect(suggestEmail(email)).toBeNull();
    },
  );
});
