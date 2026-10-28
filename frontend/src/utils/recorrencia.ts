/**
 * Datas "todo dia X, a partir do mês Y, N vezes", em 'yyyy-MM-dd'. Em mês que
 * não tem o dia (31 em abril, 30 em fevereiro), cai no último dia do mês.
 */
export function gerarDatas(dia: number, inicio: string, quantidade: number): string[] {
  const [ano, mes] = inicio.split('-').map(Number);
  if (!dia || !ano || !mes || quantidade < 1) return [];

  return Array.from({ length: quantidade }, (_, i) => {
    const a = ano + Math.floor((mes - 1 + i) / 12);
    const m = ((mes - 1 + i) % 12) + 1;
    const ultimoDia = new Date(a, m, 0).getDate();
    const d = Math.min(dia, ultimoDia);
    return `${a}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
  });
}
