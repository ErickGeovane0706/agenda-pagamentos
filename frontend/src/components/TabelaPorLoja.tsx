import { ValorMonetario } from './ValorMonetario';

export interface ResumoLoja {
  lojaId: string;
  nome: string;
  valorPago: number;
  valorAPagar: number;
  valorEmAtraso: number;
}

const COR = { pago: '#2fbf71', pendente: '#f5a04a', vencido: '#ec5d5d' };

const SITUACAO = {
  vencido: { badge: 'Tem vencido', faixa: 'from-[#ec5d5d] to-[#f58a6b]', classes: 'bg-[#fdeeee] text-[#c23a3a]' },
  pendente: { badge: 'Pendente', faixa: 'from-[#f5a04a] to-[#f7c070]', classes: 'bg-[#fff4e7] text-[#b86a12]' },
  emDia: { badge: 'Em dia', faixa: 'from-[#2fbf71] to-[#63d99a]', classes: 'bg-[#e8f8ef] text-[#16844a]' },
};

function Valores({ pago, pendente, vencido, escuro }: { pago: number; pendente: number; vencido: number; escuro?: boolean }) {
  const itens = [
    { label: 'Pago', cor: COR.pago, valor: pago },
    { label: 'Pendente', cor: COR.pendente, valor: pendente },
    { label: 'Vencido', cor: COR.vencido, valor: vencido },
  ];
  return (
    <div className="grid grid-cols-3 gap-2">
      {itens.map(i => (
        <div key={i.label} className="min-w-0 tabular-nums">
          <small className={`flex items-center gap-1.5 text-[11px] mb-0.5 ${escuro ? 'text-white/60' : 'text-[#7a8499]'}`}>
            <span className="w-1.5 h-1.5 rounded-full" style={{ background: i.cor }} />
            {i.label}
          </small>
          {i.valor > 0
            ? <ValorMonetario valor={i.valor} className={`font-bold whitespace-nowrap ${escuro ? 'text-[15px]' : 'text-[12.5px]'}`} />
            : <span className={`font-semibold ${escuro ? 'text-white/40' : 'text-[#b8bfcc]'}`}>—</span>}
        </div>
      ))}
    </div>
  );
}

function Anel({ pct }: { pct: number }) {
  const r = 21;
  const c = 2 * Math.PI * r;
  return (
    <div className="relative w-[52px] h-[52px] shrink-0">
      <svg viewBox="0 0 52 52" className="w-full h-full -rotate-90">
        <circle cx="26" cy="26" r={r} fill="none" stroke="#eceff4" strokeWidth="5" />
        <circle cx="26" cy="26" r={r} fill="none" stroke={COR.pago} strokeWidth="5" strokeLinecap="round"
          strokeDasharray={`${(c * pct) / 100} ${c}`} />
      </svg>
      <span className="absolute inset-0 grid place-items-center text-[11.5px] font-extrabold tabular-nums">{pct}%</span>
    </div>
  );
}

/**
 * Com "Todas as lojas", o total somado não diz de qual loja é cada valor.
 * Um cartão por loja (já ordenadas pelo backend do maior a pagar para o menor),
 * com a situação, o quanto já foi pago e a barra na mesma escala para todas.
 * O cartão inteiro é o botão que abre o relatório só daquela loja.
 */
export function TabelaPorLoja({ porLoja, lojas, onEscolher }: {
  porLoja: ResumoLoja[];
  lojas: { id: string; nome: string }[];
  onEscolher: (lojaId: string) => void;
}) {
  const maior = Math.max(0, ...porLoja.map(l => l.valorPago + l.valorAPagar));
  const largura = (v: number) => `${maior > 0 ? (v / maior) * 100 : 0}%`;
  const total = porLoja.reduce((s, l) => ({
    pago: s.pago + l.valorPago,
    pendente: s.pendente + l.valorAPagar - l.valorEmAtraso,
    vencido: s.vencido + l.valorEmAtraso,
  }), { pago: 0, pendente: 0, vencido: 0 });
  const semLancamento = lojas.filter(l => !porLoja.some(p => p.lojaId === l.id)).map(l => l.nome);

  return (
    <div className="text-[#14213d]">
      <div className="px-1 mb-4">
        <h3 className="text-[21px] font-extrabold tracking-tight">Por loja</h3>
        <p className="text-[12.5px] text-[#7a8499] mt-0.5">Da que mais tem a pagar para a que menos tem. Toque na loja para ver só ela.</p>
      </div>

      <ul className="grid gap-3.5">
        {porLoja.map(l => {
          const pendente = l.valorAPagar - l.valorEmAtraso;
          const situacao = SITUACAO[l.valorEmAtraso > 0 ? 'vencido' : l.valorAPagar > 0 ? 'pendente' : 'emDia'];
          const pctPago = Math.round((l.valorPago / (l.valorPago + l.valorAPagar || 1)) * 100);
          return (
            <li key={l.lojaId}>
              <button
                onClick={() => onEscolher(l.lojaId)}
                aria-label={`${l.nome}, a pagar ${l.valorAPagar.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}`}
                className="relative block w-full text-left cursor-pointer bg-white border border-[#eceff4] rounded-[22px] p-[18px] sm:px-5 sm:pt-5 overflow-hidden
                           shadow-[0_1px_2px_rgba(20,33,61,.04),0_14px_30px_-20px_rgba(20,33,61,.25)]
                           hover:-translate-y-0.5 hover:shadow-[0_1px_2px_rgba(20,33,61,.05),0_20px_36px_-20px_rgba(20,33,61,.32)]
                           focus-visible:outline-2 focus-visible:outline-[#14213d] focus-visible:outline-offset-[3px]
                           transition motion-reduce:transition-none"
              >
                <span className={`absolute inset-x-0 top-0 h-1 bg-gradient-to-r ${situacao.faixa}`} />
                <div className="flex justify-between items-start gap-3">
                  <div className="min-w-0">
                    <div className="font-bold text-[15.5px] tracking-tight mb-[7px] truncate">{l.nome}</div>
                    <span className={`inline-block text-[11px] font-bold px-[9px] py-[3px] rounded-full ${situacao.classes}`}>{situacao.badge}</span>
                  </div>
                  <Anel pct={pctPago} />
                </div>
                <div className="text-[11.5px] text-[#7a8499] mt-4 mb-0.5">A pagar</div>
                <ValorMonetario valor={l.valorAPagar}
                  className={`block text-2xl sm:text-[26px] font-extrabold tracking-tight tabular-nums ${l.valorAPagar === 0 ? 'text-[#16844a]' : ''}`} />
                <div className="flex gap-0.5 h-2 rounded-full bg-[#eceff4] overflow-hidden my-3.5">
                  <i className="block h-full" style={{ width: largura(l.valorPago), background: COR.pago }} />
                  <i className="block h-full" style={{ width: largura(pendente), background: COR.pendente }} />
                  <i className="block h-full" style={{ width: largura(l.valorEmAtraso), background: COR.vencido }} />
                </div>
                <Valores pago={l.valorPago} pendente={pendente} vencido={l.valorEmAtraso} />
              </button>
            </li>
          );
        })}
      </ul>

      <div className="mt-4 bg-[#14213d] text-white rounded-[22px] px-[22px] py-5">
        <div className="text-xs font-semibold text-white/65 mb-3">Total de todas as lojas</div>
        <Valores {...total} escuro />
      </div>

      {semLancamento.length > 0 && (
        <p className="text-[11.5px] text-[#7a8499] mt-3 px-1">Sem lançamento no período: {semLancamento.join(', ')}</p>
      )}
    </div>
  );
}
