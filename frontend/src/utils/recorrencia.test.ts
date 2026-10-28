import { describe, it, expect } from 'vitest';
import { gerarDatas } from './recorrencia';

describe('gerarDatas', () => {
  it('todo dia 27 a partir de novembro, 3 vezes, atravessando o ano', () => {
    expect(gerarDatas(27, '2026-11', 3)).toEqual(['2026-11-27', '2026-12-27', '2027-01-27']);
  });

  it('dia 31 cai no último dia dos meses mais curtos, inclusive fevereiro', () => {
    expect(gerarDatas(31, '2027-01', 4)).toEqual(['2027-01-31', '2027-02-28', '2027-03-31', '2027-04-30']);
  });

  it('dia 29 em fevereiro de ano bissexto', () => {
    expect(gerarDatas(29, '2028-02', 1)).toEqual(['2028-02-29']);
  });

  it('sem dia, mês ou quantidade válidos não gera nada', () => {
    expect(gerarDatas(0, '2026-11', 3)).toEqual([]);
    expect(gerarDatas(27, '', 3)).toEqual([]);
    expect(gerarDatas(27, '2026-11', 0)).toEqual([]);
  });
});
