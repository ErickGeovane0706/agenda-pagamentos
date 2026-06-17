import { useState } from 'react';
import { X, Filter, RotateCcw } from 'lucide-react';
import { FiltrosAgenda } from '../../types';

interface Props {
  abertos: boolean;
  filtros: FiltrosAgenda;
  onFechar: () => void;
  onAplicar: (filtros: FiltrosAgenda) => void;
}

export function ModalFiltros({ abertos, filtros, onFechar, onAplicar }: Props) {
  const [de, setDe] = useState(filtros.de ?? '');
  const [ate, setAte] = useState(filtros.ate ?? '');
  const [status, setStatus] = useState(filtros.status ?? '');
  const [nome, setNome] = useState(filtros.nome ?? '');

  if (!abertos) return null;

  const aplicar = () => {
    onAplicar({
      de: de || undefined,
      ate: ate || undefined,
      status: status || undefined,
      nome: nome || undefined,
    });
    onFechar();
  };

  const limpar = () => {
    setDe('');
    setAte('');
    setStatus('');
    setNome('');
    onAplicar({});
    onFechar();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/30" onClick={onFechar} />
      <div className="relative bg-white rounded-2xl shadow-xl border border-slate-100 w-full max-w-md mx-4 p-6">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-2">
            <Filter className="w-5 h-5 text-[#0c4a6e]" />
            <h2 className="text-lg font-bold text-slate-900">Filtros</h2>
          </div>
          <button onClick={onFechar} className="p-1 rounded-lg hover:bg-slate-100 text-slate-400">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">De</label>
              <input type="date" value={de} onChange={e => setDe(e.target.value)}
                className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Até</label>
              <input type="date" value={ate} onChange={e => setAte(e.target.value)}
                className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm" />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Status</label>
            <select value={status} onChange={e => setStatus(e.target.value)}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm">
              <option value="">Todos</option>
              <option value="PENDENTE">Pendente</option>
              <option value="PAGO">Pago</option>
              <option value="VENCIDO">Vencido</option>
              <option value="CANCELADO">Cancelado</option>
              <option value="COMPENSADO">Compensado</option>
              <option value="DEVOLVIDO">Devolvido</option>
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Nome do fornecedor</label>
            <input type="text" value={nome} onChange={e => setNome(e.target.value)}
              placeholder="Buscar por nome..."
              className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm" />
          </div>
        </div>

        <div className="flex items-center justify-between mt-6 pt-4 border-t border-slate-100">
          <button onClick={limpar}
            className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 px-3 py-2 rounded-lg hover:bg-slate-100 transition-colors">
            <RotateCcw className="w-4 h-4" />
            Limpar
          </button>
          <button onClick={aplicar}
            className="bg-[#0c4a6e] hover:bg-[#0a3d5c] text-white text-sm font-medium px-6 py-2 rounded-xl transition-colors shadow-sm">
            Aplicar
          </button>
        </div>
      </div>
    </div>
  );
}
