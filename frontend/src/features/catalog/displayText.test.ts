import { describe, expect, it } from 'vitest';
import { displayProductName, displaySize } from './displayText.ts';

describe('the names and sizes the store shows (T-E7-71)', () => {
  it.each([
    ['TOALLON KUROMI', 'Toallon Kuromi'],
    ['Top y short adulto modal EXCLUSIVO - LILO Y STITCH', 'Top y Short Adulto Modal Exclusivo - Lilo y Stitch'],
    ['Pijama Manga Larga + Pantalón Largo - FULL STITCH', 'Pijama Manga Larga + Pantalón Largo - Full Stitch'],
    ['Pijama infantil 2 piezas corto LOS SIMPSONS', 'Pijama Infantil 2 Piezas Corto Los Simpsons'],
    ['Bata con Capucha - ANIMAL PRINT', 'Bata con Capucha - Animal Print'],
    ['MOCHILA K-POP CON USB', 'Mochila K-Pop con USB'],
    ['Pijama Manga Corta - y amigos', 'Pijama Manga Corta - Y Amigos'],
  ])('given "%s", when shown, then it reads "%s"', (typed, shown) => {
    expect(displayProductName(typed)).toBe(shown);
  });

  it('given acronyms, codes and inner capitals, when shown, then they stay as typed', () => {
    expect(displayProductName('Sunga infantil AFA')).toBe('Sunga Infantil AFA');
    expect(displayProductName('Lona Playera 150x150 BT21')).toBe('Lona Playera 150x150 BT21');
    expect(displayProductName('MEDIAS 3/4 DOBLE ALGODON')).toBe('Medias 3/4 Doble Algodon');
    expect(displayProductName('Conjunto Deportivo Rayo McQueen')).toBe('Conjunto Deportivo Rayo McQueen');
    expect(displayProductName('Conjunto pijama plush infantil- MINECRAFT(8)')).toBe('Conjunto Pijama Plush Infantil- Minecraft(8)');
  });

  it('given extra spaces, when shown, then words are one space apart', () => {
    expect(displayProductName('  PIJAMA   ROSA ')).toBe('Pijama Rosa');
  });

  it('given "Unico" in any case or accent, when shown, then it reads "Único"', () => {
    for (const typed of ['Unico', 'unico', 'UNICO', 'Único', ' único ']) {
      expect(displaySize(typed)).toBe('Único');
    }
  });

  it('given letter sizes in lowercase, when shown, then they are capitals, and other sizes stay', () => {
    expect(displaySize('s')).toBe('S');
    expect(displaySize('xl')).toBe('XL');
    expect(displaySize('xxxl')).toBe('XXXL');
    expect(displaySize('12')).toBe('12');
    expect(displaySize('Infantil/Adulto')).toBe('Infantil/Adulto');
  });
});
