import { describe, expect, it } from 'vitest';
import {
  countFilters,
  NO_FILTERS,
  parseCriteria,
  toSearchParams,
  withoutText,
  type ListingCriteria,
} from './listingCriteria.ts';

function parse(query: string): ListingCriteria {
  return parseCriteria(new URLSearchParams(query));
}

describe('listing criteria in the URL (UC-02)', () => {
  it('given no query, when read, then there are no filters and the sort is newest', () => {
    expect(parse('')).toEqual({ filters: NO_FILTERS, sort: 'NEWEST' });
  });

  it('given every filter, when read, then each maps to the API values and prices go from pesos to cents', () => {
    const criteria = parse(
      'talle=M&talle=L&color=Rosa+viejo&material=Algod%C3%B3n&temporada=verano&temporada=media-estacion' +
        '&publico=infantil&precio-desde=12000&precio-hasta=25000&orden=mayor-precio',
    );

    expect(criteria).toEqual({
      filters: {
        sizes: ['M', 'L'],
        colors: ['Rosa viejo'],
        materials: ['Algodón'],
        seasons: ['SUMMER', 'MID_SEASON'],
        audiences: ['KIDS'],
        minPriceCents: 1_200_000,
        maxPriceCents: 2_500_000,
      },
      sort: 'PRICE_DESC',
    });
  });

  it('given criteria, when written and read back, then they are the same', () => {
    const criteria: ListingCriteria = {
      filters: {
        sizes: ['S/M'],
        colors: ['Rosa & blanco'],
        materials: [],
        seasons: ['WINTER'],
        audiences: ['WOMEN', 'MEN'],
        minPriceCents: 0,
      },
      sort: 'PRICE_ASC',
    };

    const query = toSearchParams(criteria);

    expect(query.toString()).toBe(
      'talle=S%2FM&color=Rosa+%26+blanco&temporada=invierno&publico=mujer&publico=hombre&precio-desde=0&orden=menor-precio',
    );
    expect(parseCriteria(query)).toEqual(criteria);
  });

  it('given no filters and the newest sort, when written, then the query is empty', () => {
    expect(toSearchParams({ filters: NO_FILTERS, sort: 'NEWEST' }).toString()).toBe('');
  });

  it('given unknown or malformed values, when read, then they are ignored', () => {
    const criteria = parse('temporada=otono&publico=WOMEN&orden=nombre&talle=&precio-desde=12.5&precio-hasta=-3');

    expect(criteria).toEqual({ filters: NO_FILTERS, sort: 'NEWEST' });
  });

  it('given a minimum above the maximum, when read, then the price range is ignored', () => {
    expect(parse('precio-desde=30000&precio-hasta=10000').filters).toEqual(NO_FILTERS);
  });

  it('given repeated values or more than 20, when read, then each counts once and only the first 20 stay', () => {
    const sizes = Array.from({ length: 25 }, (_, index) => `talle=T${String(index)}`).join('&');

    const criteria = parse(`${sizes}&talle=T0&temporada=verano&temporada=verano`);

    expect(criteria.filters.sizes).toHaveLength(20);
    expect(criteria.filters.sizes[0]).toBe('T0');
    expect(criteria.filters.seasons).toEqual(['SUMMER']);
  });

  it('given chosen values and a price range, when counted, then each value counts and the range counts once', () => {
    expect(countFilters({ ...NO_FILTERS, sizes: ['M', 'L'], seasons: ['SUMMER'], minPriceCents: 1, maxPriceCents: 2 }))
      .toBe(4);
  });
});

describe('search criteria in the URL (UC-26)', () => {
  it('given a text, when read, then it is trimmed and the default sort is relevance', () => {
    expect(parse('q=++pijama+rosa++&talle=M')).toEqual({
      filters: { ...NO_FILTERS, sizes: ['M'] },
      sort: 'RELEVANCE',
      text: 'pijama rosa',
    });
    expect(parse('q=+++').text).toBeUndefined();
  });

  it('given a text longer than 100 characters, when read, then only the first 100 stay', () => {
    expect(parse(`q=${'ñ'.repeat(150)}`).text).toBe('ñ'.repeat(100));
  });

  it('given a search sorted by newest, when written and read back, then the sort stays and relevance leaves no trace', () => {
    const newest: ListingCriteria = { filters: NO_FILTERS, sort: 'NEWEST', text: 'pijama' };

    expect(toSearchParams(newest).toString()).toBe('q=pijama&orden=novedades');
    expect(parseCriteria(toSearchParams(newest))).toEqual(newest);
    expect(toSearchParams({ ...newest, sort: 'RELEVANCE' }).toString()).toBe('q=pijama');
  });

  it('given a listing that is not a search, when its text is dropped, then a relevance sort becomes newest', () => {
    expect(withoutText(parse('q=pijama&orden=menor-precio'))).toEqual({ filters: NO_FILTERS, sort: 'PRICE_ASC' });
    expect(withoutText(parse('q=pijama'))).toEqual({ filters: NO_FILTERS, sort: 'NEWEST' });
  });
});
