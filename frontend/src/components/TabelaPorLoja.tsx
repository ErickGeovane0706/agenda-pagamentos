import { ChevronRight } from 'lucide-react';
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
 * Uma barra por loja (já ordenadas pelo backend do maior a pagar para o menor),
 * no tamanho do que ela tem a pagar, com o atraso em vermelho dentro dela.
 * Tocar na loja abre o relatório só daquela loja.
 */
export function TabelaPorLoja({ porLoja, lojas, onEscolher }: {
  porLoja: ResumoLoja[];
  lojas: { id: string; nome: string }[];
  onEscolher: (lojaId: string) => void;
}) {
  const maior = Math.max(0, ...porLoja.map(l => l.valorAPagar));
  const totalPago = porLoja.reduce((s, l) => s + l.valorPago, 0);
  const totalAPagar = porLoja.reduce((s, l) => s + l.valorAPagar, 0);
  const totalEmAtraso = porLoja.reduce((s, l) => s + l.valorEmAtraso, 0);
  const semLancamento = lojas.filter(l => !porLoja.some(p => p.lojaId === l.id)).map(l => l.nome);
  const largura = (v: number) => (maior > 0 && v > 0 ? `${Math.max(2, (v / maior) * 100)}%` : '0%');

  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-4 sm:p-5">
      <div className="flex items-end justify-between gap-4 mb-4">
        <div>
          <h3 className="text-sm font-semibold text-slate-900">A pagar por loja</h3>
          <p className="text-xs text-slate-400">Toque numa loja para ver só ela</p>
        </div>
        <div className="flex gap-3 text-xs text-slate-500 shrink-0">
          <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-sm bg-sky-200" />A pagar</span>
          <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-sm bg-red-300" />Em atraso</span>
        </div>
      </div>

      <ul className="space-y-1">
        {porLoja.map(l => (
          <li key={l.lojaId}>
            <button
              onClick={() => onEscolher(l.lojaId)}
              className="group w-full flex items-center gap-3 rounded-lg p-1 -mx-1 text-left hover:bg-slate-50 transition-colors"
            >
              <div className="relative flex-1 min-w-0 h-10">
                <div className="absolute inset-y-0 left-0 flex rounded-md overflow-hidden transition-all"
                  style={{ width: largura(l.valorAPagar) }}>
                  <div className="h-full bg-red-300" style={{ width: `${l.valorAPagar > 0 ? (l.valorEmAtraso / l.valorAPagar) * 100 : 0}%` }} />
                  <div className="h-full flex-1 bg-sky-200 group-hover:bg-sky-300 transition-colors" />
                </div>
                <span className="absolute inset-y-0 left-3 right-2 flex items-center text-sm font-medium text-slate-800 truncate">
                  {l.nome}
                </span>
              </div>
              <div className="text-right tabular-nums whitespace-nowrap">
                <div className="text-sm font-semibold text-slate-900">
                  {l.valorAPagar > 0 ? <ValorMonetario valor={l.valorAPagar} /> : '—'}
                </div>
                <div className="text-xs">
                  {l.valorEmAtraso > 0
                    ? <span className="text-red-600"><ValorMonetario valor={l.valorEmAtraso} /> em atraso</span>
                    : <span className="text-slate-400">pago <ValorMonetario valor={l.valorPago} /></span>}
                </div>
              </div>
              <ChevronRight className="w-4 h-4 text-slate-300 group-hover:text-slate-500 shrink-0" />
            </button>
          </li>
        ))}
      </ul>

      <div className="flex items-start justify-between gap-4 border-t border-slate-100 mt-3 pt-3">
        <div>
          <div className="text-sm font-semibold text-slate-900">Total</div>
          <div className="text-xs text-slate-400">pago <ValorMonetario valor={totalPago} /></div>
        </div>
        <div className="text-right tabular-nums whitespace-nowrap pr-7">
          <div className="text-sm font-bold text-slate-900"><ValorMonetario valor={totalAPagar} /></div>
          {totalEmAtraso > 0 && (
            <div className="text-xs text-red-600"><ValorMonetario valor={totalEmAtraso} /> em atraso</div>
          )}
        </div>
      </div>

      {semLancamento.length > 0 && (
        <p className="text-xs text-slate-400 mt-3">Sem lançamento no período: {semLancamento.join(', ')}</p>
      )}
    </div>
  );
}
