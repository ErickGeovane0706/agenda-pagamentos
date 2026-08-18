import { ChevronLeft, ChevronRight } from 'lucide-react';
import { clsx } from 'clsx';

interface PaginationProps {
  page: number;
  totalPages: number;
  total: number;
  pageSize: number;
  onChange: (page: number) => void;
}

/* Quais numeros aparecem na barra. Sempre a primeira, a ultima, e a atual com
   um vizinho de cada lado — no maximo 5 numeros. Sem isto a barra crescia um
   botao por pagina: uma loja com 300 registros gerava 20 botoes, ~850px, e o
   transbordo empurrava a largura do documento inteiro (a tela "esticava" no
   celular). Agora a largura e constante, tenha a loja 20 paginas ou 200.

   O limite de 6 e largura, nao estetica: 6 numeros + as duas setas de 44px
   cabem nos ~328px uteis de uma tela de 360px; 7 ja nao cabem. */
function paginasVisiveis(page: number, totalPages: number): (number | 'gap')[] {
  if (totalPages <= 6) return Array.from({ length: totalPages }, (_, i) => i);

  // Prende a janela nas bordas para que os 5 numeros nunca virem 3 ou 4 — a
  // barra mudar de largura durante a navegacao e o que se quer evitar.
  const inicio = Math.max(1, Math.min(page - 1, totalPages - 4));
  const fim = Math.min(totalPages - 2, Math.max(page + 1, 3));

  const itens: (number | 'gap')[] = [0];
  if (inicio > 1) itens.push('gap');
  for (let i = inicio; i <= fim; i++) itens.push(i);
  if (fim < totalPages - 2) itens.push('gap');
  itens.push(totalPages - 1);
  return itens;
}

export function Pagination({ page, totalPages, total, onChange }: PaginationProps) {
  if (totalPages <= 1) return null;

  const anteriorDesabilitado = page <= 0;
  const proximaDesabilitada = page >= totalPages - 1;

  return (
    <div className="flex flex-col items-center gap-2 px-4 py-3 bg-white border-t border-slate-100 sm:flex-row sm:justify-between sm:gap-0">
      <span className="text-xs text-slate-500">
        {total} registro{total !== 1 ? 's' : ''}
      </span>
      <div className="flex items-center gap-1">
        {/* Setas de 44px no celular: o alvo de toque recomendado. Quem navega
            de pagina em pagina passa o dia inteiro nelas. */}
        <button
          onClick={() => onChange(page - 1)}
          disabled={anteriorDesabilitado}
          aria-label="Pagina anterior"
          className={clsx(
            'flex items-center justify-center w-11 h-11 rounded-lg transition-colors sm:w-8 sm:h-8',
            anteriorDesabilitado
              ? 'text-slate-300 cursor-not-allowed'
              : 'text-slate-500 hover:bg-slate-100'
          )}
        >
          <ChevronLeft className="w-5 h-5 sm:w-4 sm:h-4" />
        </button>
        {paginasVisiveis(page, totalPages).map((item, i) =>
          item === 'gap' ? (
            <span key={`gap-${i}`} className="px-1 text-sm text-slate-400 select-none">
              {'\u2026'}
            </span>
          ) : (
            <button
              key={item}
              onClick={() => onChange(item)}
              aria-current={item === page ? 'page' : undefined}
              className={clsx(
                'min-w-[32px] h-8 rounded-lg text-sm font-medium transition-colors',
                item === page
                  ? 'bg-[#0c4a6e] text-white'
                  : 'text-slate-600 hover:bg-slate-100'
              )}
            >
              {item + 1}
            </button>
          )
        )}
        <button
          onClick={() => onChange(page + 1)}
          disabled={proximaDesabilitada}
          aria-label="Proxima pagina"
          className={clsx(
            'flex items-center justify-center w-11 h-11 rounded-lg transition-colors sm:w-8 sm:h-8',
            proximaDesabilitada
              ? 'text-slate-300 cursor-not-allowed'
              : 'text-slate-500 hover:bg-slate-100'
          )}
        >
          <ChevronRight className="w-5 h-5 sm:w-4 sm:h-4" />
        </button>
      </div>
    </div>
  );
}
