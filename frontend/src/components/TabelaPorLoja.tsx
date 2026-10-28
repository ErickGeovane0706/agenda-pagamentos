import { ValorMonetario } from './ValorMonetario';

export interface ResumoLoja {
  lojaId: string;
  nome: string;
  valorPago: number;
  valorAPagar: number;
  valorEmAtraso: number;
}

/**
 * Com "Todas as lojas", o total somado não diz de qual loja é cada valor.
 * Uma linha por loja, já ordenada pelo backend do maior a pagar para o menor;
 * tocar no nome abre o relatório só daquela loja.
 */
export function TabelaPorLoja({ porLoja, lojas, onEscolher }: {
  porLoja: ResumoLoja[];
  lojas: { id: string; nome: string }[];
  onEscolher: (lojaId: string) => void;
}) {
  const totalPago = porLoja.reduce((s, l) => s + l.valorPago, 0);
  const totalAPagar = porLoja.reduce((s, l) => s + l.valorAPagar, 0);
  const totalEmAtraso = porLoja.reduce((s, l) => s + l.valorEmAtraso, 0);
  const semLancamento = lojas.filter(l => !porLoja.some(p => p.lojaId === l.id)).map(l => l.nome);

  const valorOuTraco = (v: number) => (v > 0 ? <ValorMonetario valor={v} /> : '—');

  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-4">
      <h3 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-3">Por loja</h3>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-xs text-slate-500">
            <th className="text-left font-medium pb-2">Loja</th>
            <th className="text-right font-medium pb-2">Pago</th>
            <th className="text-right font-medium pb-2">A pagar</th>
          </tr>
        </thead>
        <tbody>
          {porLoja.map(l => (
            <tr key={l.lojaId} onClick={() => onEscolher(l.lojaId)}
              className="border-t border-slate-100 cursor-pointer hover:bg-slate-50">
              <td className="py-2 pr-2">
                <button className="text-left font-medium text-[#0c4a6e]">{l.nome}</button>
              </td>
              <td className="py-2 text-right text-slate-600 tabular-nums whitespace-nowrap">{valorOuTraco(l.valorPago)}</td>
              <td className="py-2 text-right tabular-nums whitespace-nowrap">
                <div className="font-semibold text-slate-900">{valorOuTraco(l.valorAPagar)}</div>
                {l.valorEmAtraso > 0 && (
                  <div className="text-xs text-red-600"><ValorMonetario valor={l.valorEmAtraso} /> em atraso</div>
                )}
              </td>
            </tr>
          ))}
          <tr className="border-t-2 border-slate-200 font-bold text-slate-900">
            <td className="py-2">Total</td>
            <td className="py-2 text-right tabular-nums whitespace-nowrap">{valorOuTraco(totalPago)}</td>
            <td className="py-2 text-right tabular-nums whitespace-nowrap">
              <div>{valorOuTraco(totalAPagar)}</div>
              {totalEmAtraso > 0 && (
                <div className="text-xs font-normal text-red-600"><ValorMonetario valor={totalEmAtraso} /> em atraso</div>
              )}
            </td>
          </tr>
        </tbody>
      </table>
      {semLancamento.length > 0 && (
        <p className="text-xs text-slate-400 mt-3">Sem lançamento no período: {semLancamento.join(', ')}</p>
      )}
    </div>
  );
}
