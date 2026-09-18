import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Plus, Store, Pencil, Trash2, ChevronRight, LogOut, Calendar, ShoppingCart } from 'lucide-react';
import { useState } from 'react';
import api from '../api/client';
import { useAuthStore } from '../store/authStore';
import { Loja } from '../types';
import { ModalCriarLoja } from '../components/ModalCriarLoja';
import { SkeletonCard } from '../components/Skeleton';

export const CORES_LOJA = [
  { hex: '#0c4a6e', nome: 'Petróleo' },
  { hex: '#065f46', nome: 'Verde' },
  { hex: '#7c3aed', nome: 'Roxo' },
  { hex: '#b91c1c', nome: 'Vermelho' },
  { hex: '#c2410c', nome: 'Laranja' },
  { hex: '#0e7490', nome: 'Teal' },
  { hex: '#1f2937', nome: 'Escuro' },
  { hex: '#92400e', nome: 'Marrom' },
];

export default function SelecionarLojaPage() {
  const navigate = useNavigate();
  const { usuario, setLojaAtiva, logout } = useAuthStore();
  const queryClient = useQueryClient();

  const handleLogout = () => {
    queryClient.clear();
    logout();
    navigate('/login');
  };
  const [modalAberto, setModalAberto] = useState(false);
  const [lojaEditando, setLojaEditando] = useState<Loja | null>(null);
  /** Loja já escolhida, esperando o usuário dizer Financeiro ou PDV. */
  const [lojaEscolhida, setLojaEscolhida] = useState<Loja | null>(null);

  const { data: lojas = [], isLoading } = useQuery({
    queryKey: ['lojas', usuario?.empresaId],
    queryFn: () => api.get('/lojas').then(r => r.data),
  });

  const excluirMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/lojas/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['lojas'] }),
  });

  // O flag do módulo vem de /assinaturas/minha, que o AvisoAssinatura já
  // consulta com esta mesma chave — então isto reaproveita o cache do
  // react-query em vez de pedir de novo.
  const { data: assinatura } = useQuery<{ pdvHabilitado: boolean }>({
    queryKey: ['minha-assinatura'],
    queryFn: () => api.get('/assinaturas/minha').then((r) => r.data),
    enabled: usuario != null && usuario.perfil !== 'MASTER',
  });
  const temPdv = assinatura?.pdvHabilitado === true;

  const handleSelecionarLoja = (loja: Loja) => {
    setLojaAtiva(loja.id);
    // Sem o módulo não há escolha a fazer: uma tela com um único caminho seria
    // só um clique a mais para todo cliente que não tem PDV. Com o módulo, a
    // bifurcação aparece — inclusive para quem tem uma loja só, que é o mesmo
    // caminho para todo mundo, sem caso especial.
    if (temPdv) {
      setLojaEscolhida(loja);
    } else {
      navigate('/agenda');
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-50">
        <header className="bg-white border-b border-slate-200 px-6 py-4 pt-[calc(1rem+env(safe-area-inset-top))]">
          <div className="max-w-4xl mx-auto flex items-center gap-3">
            <div className="w-9 h-9 bg-slate-200 rounded-xl animate-pulse" />
            <div className="space-y-1.5">
              <div className="h-4 w-40 bg-slate-200 rounded animate-pulse" />
              <div className="h-3 w-24 bg-slate-200 rounded animate-pulse" />
            </div>
          </div>
        </header>
        <main className="max-w-4xl mx-auto px-4 py-8">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {Array.from({ length: 3 }).map((_, i) => <SkeletonCard key={i} />)}
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      {/* O recuo do topo é obrigatório aqui: o index.html usa viewport-fit=cover,
          que joga o conteúdo por baixo da barra de status do iPhone. Sem ele o
          botão Sair fica colado no ícone de wifi. O padrão do projeto já tratava
          safe-area-inset-bottom (Layout, index.css) — faltava o topo. */}
      <header className="bg-white border-b border-slate-200 px-6 py-4 pt-[calc(1rem+env(safe-area-inset-top))]">
        <div className="max-w-4xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-[#0c4a6e] rounded-xl flex items-center justify-center">
              <Store className="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 className="font-bold text-slate-900 text-base leading-tight">
                Dia de Pagar
              </h1>
              <p className="text-xs text-slate-500">{usuario?.nome}</p>
            </div>
          </div>
          <button
            onClick={handleLogout}
            className="flex items-center gap-2 text-slate-500 hover:text-slate-700 text-sm transition-colors px-3 py-2 rounded-lg hover:bg-slate-100"
          >
            <LogOut className="w-4 h-4" />
            <span className="hidden sm:inline">Sair</span>
          </button>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-4 py-8">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="text-xl font-bold text-slate-900">Suas lojas</h2>
            <p className="text-sm text-slate-500 mt-0.5">
              Selecione uma loja para ver a agenda de pagamentos
            </p>
          </div>
          {usuario?.perfil === 'ADMIN' && (
            <button
              onClick={() => { setLojaEditando(null); setModalAberto(true); }}
              className="flex items-center gap-2 bg-[#0c4a6e] hover:bg-[#0a3d5c] text-white text-sm font-medium px-4 py-2 rounded-xl transition-colors shadow-sm"
            >
              <Plus className="w-4 h-4" />
              Nova loja
            </button>
          )}
        </div>

        {lojas.length === 0 ? (
          <div className="text-center py-16">
            <Store className="w-12 h-12 text-slate-300 mx-auto mb-3" />
            <p className="text-slate-500 font-medium">Nenhuma loja cadastrada ainda</p>
            <p className="text-slate-400 text-sm mt-1">
              Clique em "Nova loja" para começar
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {lojas.map((loja: Loja) => (
              <LojaCard
                key={loja.id}
                loja={loja}
                perfil={usuario?.perfil}
                onSelecionar={() => handleSelecionarLoja(loja)}
                onEditar={() => { setLojaEditando(loja); setModalAberto(true); }}
                onExcluir={() => {
                  if (confirm(`Excluir a loja "${loja.nome}"?`)) {
                    excluirMutation.mutate(loja.id);
                  }
                }}
              />
            ))}
          </div>
        )}
      </main>

      {modalAberto && (
        <ModalCriarLoja
          loja={lojaEditando}
          onFechar={() => setModalAberto(false)}
          onSalvo={() => {
            setModalAberto(false);
            queryClient.invalidateQueries({ queryKey: ['lojas'] });
          }}
        />
      )}

      {lojaEscolhida && (
        <EscolherModulo
          loja={lojaEscolhida}
          onFechar={() => setLojaEscolhida(null)}
          onEscolher={(destino) => navigate(destino)}
        />
      )}
    </div>
  );
}

/**
 * A bifurcação do §4 do plano: dentro da loja, Financeiro (o que já existia) ou
 * PDV (o módulo novo). Só é montada quando a empresa tem o módulo liberado.
 */
function EscolherModulo({
  loja, onFechar, onEscolher,
}: {
  loja: Loja;
  onFechar: () => void;
  onEscolher: (destino: string) => void;
}) {
  return (
    <div className="fixed inset-0 bg-slate-900/50 flex items-end sm:items-center justify-center z-50 p-0 sm:p-4">
      <div className="bg-white w-full sm:max-w-md rounded-t-2xl sm:rounded-2xl p-5 pb-[calc(1.25rem+env(safe-area-inset-bottom))]">
        <div className="flex items-center gap-3 mb-1">
          <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
               style={{ backgroundColor: loja.cor + '20' }}>
            <Store className="w-5 h-5" style={{ color: loja.cor }} />
          </div>
          <div className="min-w-0">
            <p className="font-bold text-slate-900 truncate">{loja.nome}</p>
            <p className="text-xs text-slate-500">O que você quer fazer?</p>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-3 mt-5">
          <button
            onClick={() => onEscolher('/agenda')}
            className="flex items-center gap-4 p-4 border border-slate-200 rounded-xl hover:border-[#0c4a6e] hover:bg-slate-50 text-left transition-colors"
          >
            <div className="w-11 h-11 rounded-xl bg-[#0c4a6e]/10 flex items-center justify-center flex-shrink-0">
              <Calendar className="w-5 h-5 text-[#0c4a6e]" />
            </div>
            <div className="min-w-0">
              <p className="font-semibold text-slate-900">Financeiro</p>
              <p className="text-xs text-slate-500">Boletos, cheques e PIX a pagar</p>
            </div>
            <ChevronRight className="w-4 h-4 text-slate-300 ml-auto flex-shrink-0" />
          </button>

          <button
            onClick={() => onEscolher('/venda')}
            className="flex items-center gap-4 p-4 border border-slate-200 rounded-xl hover:border-emerald-600 hover:bg-emerald-50/50 text-left transition-colors"
          >
            <div className="w-11 h-11 rounded-xl bg-emerald-600/10 flex items-center justify-center flex-shrink-0">
              <ShoppingCart className="w-5 h-5 text-emerald-700" />
            </div>
            <div className="min-w-0">
              <p className="font-semibold text-slate-900">Vendas e estoque</p>
              <p className="text-xs text-slate-500">Registrar venda e controlar produtos</p>
            </div>
            <ChevronRight className="w-4 h-4 text-slate-300 ml-auto flex-shrink-0" />
          </button>
        </div>

        <button
          onClick={onFechar}
          className="w-full mt-4 py-3 text-slate-500 font-medium text-sm"
        >
          Voltar
        </button>
      </div>
    </div>
  );
}

function LojaCard({
  loja, perfil, onSelecionar, onEditar, onExcluir
}: {
  loja: Loja;
  perfil?: string;
  onSelecionar: () => void;
  onEditar: () => void;
  onExcluir: () => void;
}) {
  return (
    <div className="group relative bg-white rounded-2xl shadow-sm border border-slate-100 hover:shadow-md hover:-translate-y-0.5 transition-all duration-200 overflow-hidden">
      <div className="h-2 w-full" style={{ backgroundColor: loja.cor }} />

      {perfil === 'ADMIN' && (
        <div className="absolute top-4 right-3 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
          <button
            onClick={(e) => { e.stopPropagation(); onEditar(); }}
            className="p-2 rounded-lg bg-white/90 hover:bg-slate-100 shadow-sm"
          >
            <Pencil className="w-3.5 h-3.5 text-slate-500" />
          </button>
          <button
            onClick={(e) => { e.stopPropagation(); onExcluir(); }}
            className="p-2 rounded-lg bg-white/90 hover:bg-red-50 shadow-sm"
          >
            <Trash2 className="w-3.5 h-3.5 text-red-400" />
          </button>
        </div>
      )}

      <button
        onClick={onSelecionar}
        className="w-full text-left p-5 flex items-center gap-4"
      >
        <div
          className="w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0"
          style={{ backgroundColor: loja.cor + '20' }}
        >
          <Store className="w-6 h-6" style={{ color: loja.cor }} />
        </div>

        <div className="flex-1 min-w-0">
          <p className="font-semibold text-slate-900 truncate">{loja.nome}</p>
          {loja.cnpj && (
            <p className="text-xs text-slate-400 mt-0.5">{loja.cnpj}</p>
          )}
          {loja.descricao && (
            <p className="text-xs text-slate-500 truncate mt-0.5">{loja.descricao}</p>
          )}
        </div>

        <ChevronRight className="w-4 h-4 text-slate-300 flex-shrink-0" />
      </button>
    </div>
  );
}
