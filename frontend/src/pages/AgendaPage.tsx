import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  FileText, QrCode, CheckSquare,
  ArrowLeft, Plus, Filter, Store
} from 'lucide-react';
import api from '../api/client';
import { useAuthStore } from '../store/authStore';
import { TabelaBoletos } from '../components/agenda/TabelaBoletos';
import { TabelaPix } from '../components/agenda/TabelaPix';
import { TabelaCheques } from '../components/agenda/TabelaCheques';
import { ModalFiltros } from '../components/agenda/ModalFiltros';
import type { FiltrosAgenda } from '../types';

type Aba = 'boletos' | 'pix' | 'cheques';

export default function AgendaPage() {
  const navigate = useNavigate();
  const { lojaAtiva } = useAuthStore();
  const [abaAtiva, setAbaAtiva] = useState<Aba>('boletos');
  const [filtros, setFiltros] = useState<FiltrosAgenda>({});
  const [modalFiltrosAberto, setModalFiltrosAberto] = useState(false);
  const temFiltros = !!filtros.de || !!filtros.ate || !!filtros.status || !!filtros.nome;

  const { data: loja } = useQuery({
    queryKey: ['loja', lojaAtiva],
    queryFn: () => api.get(`/lojas/${lojaAtiva}`).then(r => r.data),
    enabled: !!lojaAtiva,
  });

  if (!lojaAtiva) {
    navigate('/lojas');
    return null;
  }

  const abas: { id: Aba; label: string; icon: React.ReactNode }[] = [
    { id: 'boletos',  label: 'Boletos', icon: <FileText className="w-4 h-4" /> },
    { id: 'pix',      label: 'PIX',     icon: <QrCode className="w-4 h-4" /> },
    { id: 'cheques',  label: 'Cheques', icon: <CheckSquare className="w-4 h-4" /> },
  ];

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <header className="bg-white border-b border-slate-200 sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-4 py-3 flex items-center gap-3">
          <button
            onClick={() => navigate('/lojas')}
            className="p-2 rounded-lg hover:bg-slate-100 text-slate-500 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>

          <div
            className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0"
            style={{ backgroundColor: loja?.cor || '#0c4a6e' }}
          >
            <Store className="w-4 h-4 text-white" />
          </div>
          <div className="flex-1">
            <h1 className="font-bold text-slate-900 text-sm leading-tight">
              {loja?.nome || '...'}
            </h1>
            <p className="text-xs text-slate-400">Agenda de pagamentos</p>
          </div>

          <button
            onClick={() => setModalFiltrosAberto(true)}
            className={`flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-lg border transition-colors ${
              temFiltros
                ? 'bg-amber-50 border-amber-300 text-amber-700'
                : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'
            }`}
          >
            <Filter className="w-3 h-3" />
            {temFiltros ? 'Filtros ativos' : 'Filtros'}
          </button>
        </div>

        <div className="max-w-7xl mx-auto px-4">
          <div className="flex gap-1">
            {abas.map(aba => (
              <button
                key={aba.id}
                onClick={() => setAbaAtiva(aba.id)}
                className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium rounded-t-lg transition-colors ${
                  abaAtiva === aba.id
                    ? 'bg-[#0c4a6e] text-white'
                    : 'text-slate-500 hover:text-slate-700'
                }`}
              >
                {aba.icon}
                {aba.label}
              </button>
            ))}
          </div>
        </div>
      </header>

      <main className="flex-1 max-w-7xl mx-auto w-full px-4 py-6">
        {abaAtiva === 'boletos' && (
          <TabelaBoletos lojaId={lojaAtiva} filtros={filtros} />
        )}
        {abaAtiva === 'pix' && (
          <TabelaPix lojaId={lojaAtiva} filtros={filtros} />
        )}
        {abaAtiva === 'cheques' && (
          <TabelaCheques lojaId={lojaAtiva} filtros={filtros} />
        )}

        <ModalFiltros
          abertos={modalFiltrosAberto}
          filtros={filtros}
          onFechar={() => setModalFiltrosAberto(false)}
          onAplicar={(f) => setFiltros(f)}
        />
      </main>
    </div>
  );
}
