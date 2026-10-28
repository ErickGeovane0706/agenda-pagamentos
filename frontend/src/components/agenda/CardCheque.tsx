import { Pencil, Trash2 } from 'lucide-react';
import { format, isPast, parseISO } from 'date-fns';
import { Cheque, StatusCheque } from '../../types';
import { BadgeStatus } from '../BadgeStatus';
import { ValorMonetario } from '../ValorMonetario';
import { clsx } from 'clsx';
import { BotaoAnexos } from './ModalAnexos';
import { detalheConta } from '../../utils/banco';

export function CardCheque({
  cheque,
  onEditar,
  onExcluir,
  onAnexos,
  onMudarStatus,
}: {
  cheque: Cheque;
  onEditar: () => void;
  onExcluir: () => void;
  onAnexos: () => void;
  onMudarStatus: (status: StatusCheque) => void;
}) {
  const vencido = cheque.status === 'PENDENTE' &&
    isPast(parseISO(cheque.vencimento + 'T23:59:59'));
  const contaBanco = detalheConta({ agencia: cheque.bancoAgencia, conta: cheque.bancoConta });

  const proximoStatus = (atual: StatusCheque): StatusCheque => {
    if (atual === 'PENDENTE') return 'COMPENSADO';
    if (atual === 'COMPENSADO') return 'DEVOLVIDO';
    if (atual === 'DEVOLVIDO') return 'PENDENTE';
    return atual;
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-100 border-l-[#0284c7] border-l-4 shadow-sm overflow-hidden">
      <div className="flex items-center justify-between px-4 py-3 bg-slate-50 border-b border-slate-100">
        <span className="font-bold text-slate-800 text-base truncate flex-1 mr-2">
          {cheque.fornecedor}
        </span>
        <button
          onClick={() => onMudarStatus(proximoStatus(cheque.status))}
        >
          <BadgeStatus status={cheque.status} className="text-sm px-3 py-1" />
        </button>
      </div>

      <div className="px-4 py-3 space-y-2">
        <div className="flex items-center justify-between">
          <ValorMonetario valor={cheque.valor} className="text-lg font-bold text-[#0c4a6e]" />
          <span className={clsx(
            'text-sm font-medium',
            vencido ? 'text-red-600' : 'text-slate-600'
          )}>
            {format(parseISO(cheque.vencimento), 'dd/MM/yyyy')}
            {vencido && ' (vencido)'}
          </span>
        </div>

        <div className="flex items-start justify-between gap-3 text-sm">
          {cheque.bancoNome && (
            <div className="min-w-0">
              <p className="text-slate-600 truncate">
                <span className="text-slate-400">Banco:</span> {cheque.bancoNome}
              </p>
              {contaBanco && <p className="text-xs text-slate-500">{contaBanco}</p>}
            </div>
          )}
          {cheque.numeroCheque && (
            <span className="text-slate-700 font-mono shrink-0">
              #{cheque.numeroCheque}
            </span>
          )}
        </div>
      </div>

      <div className="flex items-center justify-between px-4 py-2.5 border-t border-slate-50">
        <div className="flex gap-1.5">
          <BotaoAnexos qtd={cheque.qtdAnexos} onClick={onAnexos} className="w-11 h-11" />
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
