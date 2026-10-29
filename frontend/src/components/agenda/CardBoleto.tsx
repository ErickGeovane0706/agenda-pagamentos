import { Copy, Check, Pencil, Trash2 } from 'lucide-react';
import { format, isPast, parseISO } from 'date-fns';
import { Boleto, StatusBoleto } from '../../types';
import { BadgeStatus } from '../BadgeStatus';
import { ValorMonetario } from '../ValorMonetario';
import { BotaoPagar } from './BotaoPagar';
import { clsx } from 'clsx';
import { BotaoAnexos } from './ModalAnexos';

/** "Doc 000123 · NF/Pedido NF 4521/1" — só o que o boleto tiver; nada se não tiver nenhum. */
export function LinhaDocumento({ numeroDocumento, notaFiscalPedido }: { numeroDocumento?: string; notaFiscalPedido?: string }) {
  const partes = [
    numeroDocumento && `Doc ${numeroDocumento}`,
    notaFiscalPedido && `NF/Pedido ${notaFiscalPedido}`,
  ].filter(Boolean);
  if (partes.length === 0) return null;
  return <p className="text-xs text-slate-500 truncate">{partes.join(' · ')}</p>;
}

export function CardBoleto({
  boleto,
  onCopiar,
  onEditar,
  onExcluir,
  onAnexos,
  onMudarStatus,
  copiado,
}: {
  boleto: Boleto;
  onCopiar: (codigo: string, id: string) => void;
  onEditar: () => void;
  onExcluir: () => void;
  onAnexos: () => void;
  onMudarStatus: (status: StatusBoleto) => void;
  copiado: string | null;
}) {
  const vencido = boleto.status === 'PENDENTE' &&
    isPast(parseISO(boleto.vencimento + 'T23:59:59'));

  return (
    <div className="bg-white rounded-2xl border border-slate-100 border-l-[#0c4a6e] border-l-4 shadow-sm overflow-hidden">
      <div className="flex items-center justify-between px-4 py-3 bg-slate-50 border-b border-slate-100">
        <span className="font-bold text-slate-800 text-base truncate flex-1 mr-2">
          {boleto.fornecedor}
        </span>
        <button
          onClick={() => onMudarStatus(boleto.status === 'PENDENTE' ? 'PAGO' : 'PENDENTE')}
        >
          <BadgeStatus status={boleto.status} className="text-sm px-3 py-1" />
        </button>
      </div>

      <div className="px-4 py-3 space-y-2">
        <LinhaDocumento numeroDocumento={boleto.numeroDocumento} notaFiscalPedido={boleto.notaFiscalPedido} />
        <div className="flex items-center justify-between">
          <ValorMonetario valor={boleto.valor} className="text-lg font-bold text-[#0c4a6e]" />
          <span className={clsx(
            'text-sm font-medium',
            vencido ? 'text-red-600' : 'text-slate-600'
          )}>
            {format(parseISO(boleto.vencimento), 'dd/MM/yyyy')}
            {vencido && ' (vencido)'}
          </span>
        </div>

        {boleto.codigoBarras && (
          <BotaoPagar codigo={boleto.codigoBarras} className="w-1/2 mx-auto py-2.5 text-sm" />
        )}
      </div>

      <div className="flex items-center justify-between px-4 py-2.5 border-t border-slate-50">
        <div className="flex gap-1.5">
          <BotaoAnexos qtd={boleto.qtdAnexos} onClick={onAnexos} className="w-11 h-11" />
          {boleto.codigoBarras && (
            <button
              onClick={() => onCopiar(boleto.codigoBarras!, boleto.id)}
              title="Copiar código de barras"
              className={clsx(
                'flex items-center justify-center w-11 h-11 rounded-lg transition-colors',
                copiado === boleto.id
                  ? 'bg-emerald-100 text-emerald-700'
                  : 'bg-slate-100 hover:bg-slate-200 text-slate-500'
              )}
            >
              {copiado === boleto.id ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
            </button>
          )}
        </div>
        <div className="flex gap-2">
          <button
            onClick={onEditar}
            className="flex items-center justify-center w-11 h-11 rounded-lg bg-[#e0f2fe] hover:bg-[#e0f2fe] text-[#0c4a6e]"
          >
            <Pencil className="w-4 h-4" />
          </button>
          <button
            onClick={() => { if (confirm('Excluir?')) onExcluir(); }}
            className="flex items-center justify-center w-11 h-11 rounded-lg bg-red-100 hover:bg-red-200 text-red-600"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
