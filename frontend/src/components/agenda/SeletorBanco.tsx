import { useState } from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronRight, X } from 'lucide-react';
import { Banco } from '../../types';
import { detalheConta } from '../../utils/banco';

/**
 * Campo de banco do cheque. Mostra a conta escolhida em duas linhas (nome em
 * destaque, agência e conta embaixo) e, ao tocar, abre a lista — de baixo para
 * cima no celular, centralizada no computador. Um <select> nativo não serve:
 * no celular ele corta o texto e duas contas do mesmo banco ficam iguais.
 */
export function SeletorBanco({ bancos, value, onChange }: {
  bancos: Banco[];
  value: string;
  onChange: (id: string) => void;
}) {
  const [aberto, setAberto] = useState(false);
  const escolhido = bancos.find(b => b.id === value);

  return (
    <>
      <button
        type="button"
        onClick={() => setAberto(true)}
        className="w-full flex items-center gap-3 px-4 py-2.5 border border-slate-200 rounded-lg text-left focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]"
      >
        {escolhido ? <LinhasBanco banco={escolhido} /> : (
          <span className="flex-1 text-sm text-slate-400">Selecione o banco</span>
        )}
        <ChevronRight className="w-4 h-4 text-slate-400 flex-shrink-0" />
      </button>

      {/* Portal: o modal do cheque tem transform (animação), que prenderia um
          fixed dentro dele em vez de cobrir a tela. z-[60] fica acima da barra
          de navegação do celular (z-50). */}
      {aberto && createPortal(
        <div
          className="fixed inset-0 z-[60] flex items-end sm:items-center justify-center bg-black/40"
          onClick={(e) => { e.stopPropagation(); setAberto(false); }}
        >
          <div
            role="dialog"
            aria-label="Escolha a conta do cheque"
            className="bg-white w-full sm:max-w-md rounded-t-2xl sm:rounded-2xl flex flex-col max-h-[75dvh] pb-[env(safe-area-inset-bottom)]"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-5 pt-5 pb-3 shrink-0">
              <h3 className="font-bold text-slate-900">Escolha a conta do cheque</h3>
              <button type="button" aria-label="Fechar" onClick={() => setAberto(false)}
                      className="p-2 rounded-lg hover:bg-slate-100 text-slate-400">
                <X className="w-5 h-5" />
              </button>
            </div>

            {bancos.length === 0 ? (
              <p className="px-5 pb-6 text-sm text-slate-500">
                Nenhum banco cadastrado. Cadastre em Configurações → Bancos.
              </p>
            ) : (
              <div className="overflow-y-auto overscroll-contain divide-y divide-slate-100 border-t border-slate-100">
                {bancos.map(b => (
                  <button
                    key={b.id}
                    type="button"
                    onClick={() => { onChange(b.id); setAberto(false); }}
                    className={`w-full flex items-center gap-3 px-5 py-3.5 text-left hover:bg-slate-50 ${
                      b.id === value ? 'bg-sky-50' : ''
                    }`}
                  >
                    <LinhasBanco banco={b} />
                    {b.id === value && <Check className="w-5 h-5 text-[#0c4a6e] flex-shrink-0" />}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>,
        document.body,
      )}
    </>
  );
}

function LinhasBanco({ banco }: { banco: Banco }) {
  const detalhe = detalheConta(banco);
  return (
    <span className="flex-1 min-w-0">
      <span className="block text-sm font-medium text-slate-900 truncate">{banco.nome}</span>
      {detalhe && <span className="block text-xs text-slate-500 truncate">{detalhe}</span>}
    </span>
  );
}
