import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { ChevronUp, ChevronDown, Store, X } from 'lucide-react';
import api from '../api/client';
import { Loja } from '../types';

/**
 * O cliente escolhe a ordem em que as lojas aparecem. Setas em vez de arrastar:
 * arrastar no celular é impreciso, e quem usa é o patrão, não um técnico.
 * Nada é gravado até "Salvar ordem" — cancelar volta como estava.
 */
export function ModalOrdenarLojas({
  lojas: lojasIniciais, onFechar, onSalvo,
}: {
  lojas: Loja[];
  onFechar: () => void;
  onSalvo: () => void;
}) {
  const [lojas, setLojas] = useState(lojasIniciais);

  const mover = (de: number, para: number) => {
    const nova = [...lojas];
    [nova[de], nova[para]] = [nova[para], nova[de]];
    setLojas(nova);
  };

  const mutation = useMutation({
    mutationFn: () => api.put('/lojas/ordem', lojas.map(l => l.id)),
    onSuccess: onSalvo,
  });

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/40" onClick={onFechar}>
      <div
        className="bg-white w-full sm:max-w-lg rounded-t-2xl sm:rounded-2xl flex flex-col max-h-[90vh]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between p-6 pb-2 shrink-0">
          <h2 className="text-lg font-bold text-slate-900">Ordenar lojas</h2>
          <button onClick={onFechar} className="p-2 rounded-lg hover:bg-slate-100 text-slate-400">
            <X className="w-5 h-5" />
          </button>
        </div>
        <p className="px-6 pb-4 text-sm text-slate-500 shrink-0">
          Use as setas para escolher a ordem em que as lojas aparecem.
        </p>

        <ul className="px-6 pb-4 overflow-y-auto space-y-2">
          {lojas.map((loja, i) => (
            <li key={loja.id} className="flex items-center gap-3 p-3 border border-slate-200 rounded-xl">
              <Store className="w-4 h-4 flex-shrink-0" style={{ color: loja.cor }} />
              <span className="flex-1 min-w-0 text-sm font-medium text-slate-800 truncate">{loja.nome}</span>
              <button
                type="button"
                aria-label={`Subir ${loja.nome}`}
                disabled={i === 0}
                onClick={() => mover(i, i - 1)}
                className="p-2 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-30"
              >
                <ChevronUp className="w-4 h-4" />
              </button>
              <button
                type="button"
                aria-label={`Descer ${loja.nome}`}
                disabled={i === lojas.length - 1}
                onClick={() => mover(i, i + 1)}
                className="p-2 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-30"
              >
                <ChevronDown className="w-4 h-4" />
              </button>
            </li>
          ))}
        </ul>

        <div className="p-6 pt-4 shrink-0 border-t border-slate-100 pb-[calc(1.5rem+env(safe-area-inset-bottom))]">
          {mutation.isError && (
            <p className="text-red-500 text-sm mb-3">Não foi possível salvar a ordem. Tente de novo.</p>
          )}
          <div className="flex gap-3">
            <button
              type="button"
              onClick={onFechar}
              className="flex-1 px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium text-slate-600 hover:bg-slate-50 transition-colors"
            >
              Cancelar
            </button>
            <button
              type="button"
              disabled={mutation.isPending}
              onClick={() => mutation.mutate()}
              className="flex-1 px-4 py-2.5 bg-[#0c4a6e] hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9] text-white rounded-xl text-sm font-medium transition-colors"
            >
              {mutation.isPending ? 'Salvando...' : 'Salvar ordem'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
