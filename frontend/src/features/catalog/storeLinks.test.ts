import { describe, expect, it } from 'vitest';
import { es } from '../../i18n/es.ts';
import { NO_FILTERS } from './listingCriteria.ts';
import { storeLinkLabel } from './storeLinks.ts';

describe('the header link of a listing (UC-31, T-E7-76)', () => {
  it('given exactly a header link\'s filter, when named, then it is that link', () => {
    expect(storeLinkLabel({ ...NO_FILTERS, audiences: ['MEN'] })).toBe(es.header.audiences.MEN);
    expect(storeLinkLabel({ ...NO_FILTERS, seasons: ['WINTER'] })).toBe(es.header.winter);
  });

  it('given no filter or any other combination, when named, then there is no link', () => {
    expect(storeLinkLabel(NO_FILTERS)).toBeUndefined();
    expect(storeLinkLabel({ ...NO_FILTERS, audiences: ['MEN'], sizes: ['M'] })).toBeUndefined();
    expect(storeLinkLabel({ ...NO_FILTERS, audiences: ['MEN', 'WOMEN'] })).toBeUndefined();
    expect(storeLinkLabel({ ...NO_FILTERS, seasons: ['SUMMER'] })).toBeUndefined();
  });
});
