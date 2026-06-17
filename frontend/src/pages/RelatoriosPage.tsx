import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { BarChart3, Download, Loader2, Filter } from 'lucide-react';
import api from '../api/client';
import { ValorMonetario } from '../components/ValorMonetario';
import GraficosRelatorio from '../components/GraficosRelatorio';
import { useAuthStore } from '../store/authStore';
import type { Loja } from '../types';

type StatusFiltro = 'TODOS' | 'PAGO' | 'PENDENTE';

export default function RelatoriosPage() {
  const { usuario } = useAuthStore();
  const [de, setDe] = useState('');
  const [ate, setAte] = useState('');
  const [tipo, setTipo] = useState('TODOS');
  const [lojaId, setLojaId] = useState('');
  const [status, setStatus] = useState<StatusFiltro>('TODOS');

  const { data: lojas = [] } = useQuery({
    queryKey: ['lojas', usuario?.empresaId],
    queryFn: () => api.get('/lojas').then(r => r.data),
  });

  const { data: relatorio, isLoading } = useQuery({
    queryKey: ['relatorios', de, ate, tipo, lojaId, status],
    queryFn: () => api.get('/relatorios', {
      params: {
        de, ate, tipo,
        lojaId: lojaId || undefined,
        status: status === 'TODOS' ? undefined : status,
      },
    }).then(r => r.data),
    enabled: !!de && !!ate,
  });

  const [baixando, setBaixando] = useState(false);

  const handleDownload = async () => {
    setBaixando(true);
    try {
      const token = localStorage.getItem('token');
      const params = new URLSearchParams({ de, ate });
      if (lojaId) params.set('lojaId', lojaId);
      const res = await fetch(`/api/relatorios/exportar?${params}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `relatorio-${de}-a-${ate}.xlsx`;
      a.click();
      URL.revokeObjectURL(url);
    } finally {
      setBaixando(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto px-4 py-6">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <BarChart3 className="w-6 h-6 text-[#0c4a6e]" />
          <h1 className="text-xl font-bold text-slate-900">Relatórios</h1>
        </div>
        {relatorio && (
          <button
            onClick={handleDownload}
            disabled={baixando}
            className="flex items-center gap-2 bg-[#0c4a6e] hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9]
                       text-white text-sm font-medium px-4 py-2 rounded-xl transition-colors shadow-sm"
          >
            {baixando ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
            {baixando ? 'Baixando...' : 'Download Excel'}
          </button>
        )}
      </div>

      <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-6">
        <div className="flex flex-wrap gap-4 mb-6">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">De</label>
            <input type="date" value={de} onChange={e => setDe(e.target.value)}
              className="px-3 py-2 border border-slate-200 rounded-lg text-sm" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Até</label>
            <input type="date" value={ate} onChange={e => setAte(e.target.value)}
              className="px-3 py-2 border border-slate-200 rounded-lg text-sm" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Loja</label>
            <select value={lojaId} onChange={e => setLojaId(e.target.value)}
              className="px-3 py-2 border border-slate-200 rounded-lg text-sm min-w-[160px]">
              <option value="">Todas as lojas</option>
              {lojas.map((loja: Loja) => (
                <option key={loja.id} value={loja.id}>{loja.nome}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Tipo</label>
            <select value={tipo} onChange={e => setTipo(e.target.value)}
              className="px-3 py-2 border border-slate-200 rounded-lg text-sm">
              <option value="TODOS">Todos</option>
              <option value="BOLETO">Boletos</option>
              <option value="PIX">PIX</option>
              <option value="CHEQUE">Cheques</option>
            </select>
          </div>
        </div>

        <div className="mb-6">
          <div className="flex items-center gap-2 mb-3">
            <Filter className="w-4 h-4 text-slate-500" />
            <span className="text-xs font-medium text-slate-500">Status</span>
          </div>
          <div className="flex gap-2">
            {(['TODOS', 'PAGO', 'PENDENTE'] as const).map((s) => (
              <button
                key={s}
                onClick={() => setStatus(s)}
                className={`px-4 py-1.5 text-sm font-medium rounded-lg transition-colors ${
                  status === s
                    ? s === 'PAGO'
                      ? 'bg-green-100 text-green-700 ring-2 ring-green-500'
                      : s === 'PENDENTE'
                        ? 'bg-yellow-100 text-yellow-700 ring-2 ring-yellow-500'
                        : 'bg-[#e0f2fe] text-[#0c4a6e] ring-2 ring-[#0ea5e9]'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                {s === 'TODOS' ? 'Totais' : s === 'PAGO' ? 'Pagos' : 'Pendentes'}
              </button>
            ))}
          </div>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin w-6 h-6 border-4 border-[#0c4a6e] border-t-transparent rounded-full" />
          </div>
        ) : relatorio ? (
          <div className="space-y-4">
            <div className="text-2xl font-bold text-slate-900">
              Total: <ValorMonetario valor={relatorio.totalGeral || 0} />
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              {(['boletos', 'pix', 'cheques'] as const).map((t) => (
                <div key={t} className="bg-slate-50 rounded-xl p-4">
                  <h3 className="text-sm font-medium text-slate-500 uppercase mb-2">{t}</h3>
                  <p className="text-lg font-bold text-slate-900">
                    {relatorio[t]?.total || 0} itens
                  </p>
                  <p className="text-sm text-slate-600">
                    <ValorMonetario valor={relatorio[t]?.valor || 0} />
                  </p>
                  <div className="flex flex-wrap gap-x-3 gap-y-1 mt-2 text-xs">
                    {relatorio[t]?.pago != null && relatorio[t].pago > 0 && (
                      <span className="text-green-600">Pago: {relatorio[t].pago}</span>
                    )}
                    {relatorio[t]?.compensado != null && relatorio[t].compensado > 0 && (
                      <span className="text-green-600">Comp.: {relatorio[t].compensado}</span>
                    )}
                    {relatorio[t]?.pendente != null && relatorio[t].pendente > 0 && (
                      <span className="text-yellow-600">Pend.: {relatorio[t].pendente}</span>
                    )}
                    {relatorio[t]?.vencido != null && relatorio[t].vencido > 0 && (
                      <span className="text-red-500">Venc.: {relatorio[t].vencido}</span>
                    )}
                    {relatorio[t]?.devolvido != null && relatorio[t].devolvido > 0 && (
                      <span className="text-red-500">Devolv.: {relatorio[t].devolvido}</span>
                    )}
                    {relatorio[t]?.cancelado != null && relatorio[t].cancelado > 0 && (
                      <span className="text-slate-400">Canc.: {relatorio[t].cancelado}</span>
                    )}
                  </div>
                </div>
              ))}
            </div>

            <GraficosRelatorio relatorio={relatorio} tipo={tipo} />
          </div>
        ) : (
          <p className="text-slate-400 text-center py-8">Selecione um período para gerar o relatório</p>
        )}
      </div>
    </div>
  );
}
