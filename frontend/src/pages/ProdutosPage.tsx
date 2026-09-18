import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Package, Plus, Pencil, Trash2, ShoppingCart, AlertTriangle, BarChart3 } from 'lucide-react';
import api from '../api/client';
import { useAuthStore } from '../store/authStore';
import { useMutationToast } from '../hooks/useMutationToast';
import { useToastStore } from '../store/toastStore';
import { Produto } from '../types';
import { ValorMonetario } from '../components/ValorMonetario';
import { SkeletonCard } from '../components/Skeleton';
import { mensagemDoErro } from '../utils/erroApi';

/** Quantidade abaixo disto ganha aviso visual. Não bloqueia nada. */
const ESTOQUE_BAIXO = 5;

export default function ProdutosPage() {
  const lojaAtiva = useAuthStore((s) => s.lojaAtiva);
  const usuario = useAuthStore((s) => s.usuario);
  const queryClient = useQueryClient();
  const [modalAberto, setModalAberto] = useState(false);
  const [editando, setEditando] = useState<Produto | null>(null);

  const podeEscrever = usuario?.perfil === 'ADMIN' || usuario?.perfil === 'OPERADOR';

  const { data: produtos = [], isLoading } = useQuery<Produto[]>({
    queryKey: ['produtos', lojaAtiva],
    queryFn: () => api.get('/produtos', { params: { lojaId: lojaAtiva } }).then((r) => r.data),
    enabled: !!lojaAtiva,
  });

  const desativar = useMutationToast({
    mutationFn: (id: string) => api.delete(`/produtos/${id}`),
    successMessage: 'Produto removido da lista',
    errorMessage: 'Não foi possível remover o produto',
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['produtos'] }),
  });

  if (!lojaAtiva) {
    return (
      <div className="text-center py-16">
        <Package className="w-12 h-12 text-slate-300 mx-auto mb-3" />
        <p className="text-slate-500 font-medium">Escolha uma loja primeiro</p>
        <Link to="/lojas" className="text-[#0c4a6e] text-sm font-medium mt-2 inline-block">
          Ir para as lojas
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto">
      <AbasPdv atual="produtos" />

      <div className="flex items-center justify-between mb-5">
        <div>
          <h2 className="text-xl font-bold text-slate-900">Estoque</h2>
          <p className="text-sm text-slate-500 mt-0.5">
            {produtos.length === 0 ? 'Nenhum produto ainda' : `${produtos.length} produto(s)`}
          </p>
        </div>
        {podeEscrever && (
          <button
            onClick={() => { setEditando(null); setModalAberto(true); }}
            className="flex items-center gap-2 bg-[#0c4a6e] hover:bg-[#0a3d5c] text-white text-sm font-medium px-4 py-2.5 rounded-xl transition-colors shadow-sm"
          >
            <Plus className="w-4 h-4" />
            Novo produto
          </button>
        )}
      </div>

      {isLoading ? (
        <div className="space-y-3">{Array.from({ length: 3 }).map((_, i) => <SkeletonCard key={i} />)}</div>
      ) : produtos.length === 0 ? (
        <div className="text-center py-16 bg-white rounded-2xl border border-slate-100">
          <Package className="w-12 h-12 text-slate-300 mx-auto mb-3" />
          <p className="text-slate-500 font-medium">Nenhum produto cadastrado</p>
          <p className="text-slate-400 text-sm mt-1">
            Cadastre o que você vende para começar a registrar vendas
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {produtos.map((p) => (
            <div key={p.id} className="bg-white rounded-2xl border border-slate-100 shadow-sm p-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <p className="font-semibold text-slate-900 truncate">{p.nome}</p>
                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-1.5 text-sm">
                    <span className={p.quantidade < ESTOQUE_BAIXO ? 'text-amber-600 font-medium flex items-center gap-1' : 'text-slate-600'}>
                      {p.quantidade < ESTOQUE_BAIXO && <AlertTriangle className="w-3.5 h-3.5" />}
                      {formatarQuantidade(p.quantidade)} em estoque
                    </span>
                    <span className="text-slate-400">
                      custo <ValorMonetario valor={p.precoCusto} className="text-slate-600" />
                    </span>
                    <span className="text-slate-400">
                      venda <ValorMonetario valor={p.precoVenda} className="text-emerald-700 font-medium" />
                    </span>
                  </div>
                </div>

                {podeEscrever && (
                  <div className="flex gap-1 flex-shrink-0">
                    <button
                      onClick={() => { setEditando(p); setModalAberto(true); }}
                      className="p-2.5 rounded-lg hover:bg-slate-100"
                      aria-label={`Editar ${p.nome}`}
                    >
                      <Pencil className="w-4 h-4 text-slate-500" />
                    </button>
                    <button
                      onClick={() => {
                        if (confirm(`Remover "${p.nome}" da lista?\n\nAs vendas já registradas continuam no histórico.`)) {
                          desativar.mutate(p.id);
                        }
                      }}
                      className="p-2.5 rounded-lg hover:bg-red-50"
                      aria-label={`Remover ${p.nome}`}
                    >
                      <Trash2 className="w-4 h-4 text-red-400" />
                    </button>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {modalAberto && (
        <ModalProduto
          produto={editando}
          lojaId={lojaAtiva}
          onFechar={() => setModalAberto(false)}
          onSalvo={() => {
            setModalAberto(false);
            queryClient.invalidateQueries({ queryKey: ['produtos'] });
          }}
        />
      )}
    </div>
  );
}

/** Mostra 40 em vez de 40,000, mas preserva 2,5 quando a fração importa. */
export function formatarQuantidade(q: number) {
  return Number.isInteger(q) ? String(q) : q.toLocaleString('pt-BR');
}

/** Alternância entre as telas do PDV, dentro do próprio módulo. */
export function AbasPdv({ atual }: { atual: 'venda' | 'produtos' | 'relatorio' }) {
  const base = 'flex-1 flex items-center justify-center gap-1.5 py-2.5 rounded-lg text-sm font-medium transition-colors';
  const ativo = 'bg-white text-slate-900 shadow-sm';
  return (
    <div className="flex gap-1 bg-slate-100 p-1 rounded-xl mb-5">
      <Link to="/venda" className={`${base} ${atual === 'venda' ? ativo : 'text-slate-500'}`}>
        <ShoppingCart className="w-4 h-4" />
        Vender
      </Link>
      <Link to="/produtos" className={`${base} ${atual === 'produtos' ? ativo : 'text-slate-500'}`}>
        <Package className="w-4 h-4" />
        Estoque
      </Link>
      <Link to="/venda/relatorio" className={`${base} ${atual === 'relatorio' ? ativo : 'text-slate-500'}`}>
        <BarChart3 className="w-4 h-4" />
        Relatório
      </Link>
    </div>
  );
}

function ModalProduto({
  produto, lojaId, onFechar, onSalvo,
}: {
  produto: Produto | null;
  lojaId: string;
  onFechar: () => void;
  onSalvo: () => void;
}) {
  const [nome, setNome] = useState(produto?.nome ?? '');
  const [quantidade, setQuantidade] = useState(String(produto?.quantidade ?? ''));
  const [precoCusto, setPrecoCusto] = useState(String(produto?.precoCusto ?? ''));
  const [precoVenda, setPrecoVenda] = useState(String(produto?.precoVenda ?? ''));
  const addToast = useToastStore((s) => s.addToast);

  const salvar = useMutationToast({
    mutationFn: () => {
      const corpo = {
        nome: nome.trim(),
        quantidade: Number(quantidade || 0),
        precoCusto: Number(precoCusto || 0),
        precoVenda: Number(precoVenda || 0),
      };
      return produto
        ? api.put(`/produtos/${produto.id}`, corpo)
        : api.post('/produtos', { ...corpo, lojaId });
    },
    successMessage: produto ? 'Produto atualizado' : 'Produto cadastrado',
    onSuccess: onSalvo,
    // O erro do servidor chega com a mensagem que interessa ("Já existe um
    // produto com esse nome nesta loja"), e ela é mais útil que um texto nosso.
    onError: (erro) => addToast('error', mensagemDoErro(erro, 'Não foi possível salvar o produto')),
  });

  const margem = Number(precoVenda) > 0
    ? ((Number(precoVenda) - Number(precoCusto)) / Number(precoVenda)) * 100
    : null;

  return (
    <div className="fixed inset-0 bg-slate-900/50 flex items-end sm:items-center justify-center z-50 p-0 sm:p-4">
      <div className="bg-white w-full sm:max-w-md rounded-t-2xl sm:rounded-2xl p-5 pb-[calc(1.25rem+env(safe-area-inset-bottom))] max-h-[90vh] overflow-y-auto">
        <h3 className="text-lg font-bold text-slate-900 mb-4">
          {produto ? 'Editar produto' : 'Novo produto'}
        </h3>

        <form
          onSubmit={(e) => { e.preventDefault(); salvar.mutate(); }}
          className="space-y-4"
        >
          <Campo label="Nome">
            <input
              value={nome}
              onChange={(e) => setNome(e.target.value)}
              required
              maxLength={300}
              autoFocus
              placeholder="Gelo 5kg"
              className="w-full px-4 py-3 border border-slate-200 rounded-xl text-base focus:ring-2 focus:ring-[#0c4a6e] focus:border-transparent outline-none"
            />
          </Campo>

          <Campo
            label="Quantidade em estoque"
            dica={produto ? 'Corrigir aqui ajusta o estoque, e o ajuste fica registrado' : undefined}
          >
            <input
              value={quantidade}
              onChange={(e) => setQuantidade(e.target.value)}
              required
              type="number"
              min="0"
              step="0.001"
              inputMode="decimal"
              placeholder="0"
              className="w-full px-4 py-3 border border-slate-200 rounded-xl text-base focus:ring-2 focus:ring-[#0c4a6e] focus:border-transparent outline-none"
            />
          </Campo>

          <div className="grid grid-cols-2 gap-3">
            <Campo label="Preço de custo">
              <input
                value={precoCusto}
                onChange={(e) => setPrecoCusto(e.target.value)}
                required
                type="number"
                min="0"
                step="0.01"
                inputMode="decimal"
                placeholder="0,00"
                className="w-full px-4 py-3 border border-slate-200 rounded-xl text-base focus:ring-2 focus:ring-[#0c4a6e] focus:border-transparent outline-none"
              />
            </Campo>
            <Campo label="Preço de venda">
              <input
                value={precoVenda}
                onChange={(e) => setPrecoVenda(e.target.value)}
                required
                type="number"
                min="0"
                step="0.01"
                inputMode="decimal"
                placeholder="0,00"
                className="w-full px-4 py-3 border border-slate-200 rounded-xl text-base focus:ring-2 focus:ring-[#0c4a6e] focus:border-transparent outline-none"
              />
            </Campo>
          </div>

          {margem !== null && (
            <p className="text-sm text-slate-500">
              Margem: <span className={margem >= 0 ? 'text-emerald-700 font-medium' : 'text-red-600 font-medium'}>
                {margem.toFixed(1).replace('.', ',')}%
              </span>
            </p>
          )}

          <div className="flex gap-3 pt-2">
            <button
              type="button"
              onClick={onFechar}
              className="flex-1 py-3 border border-slate-200 rounded-xl text-slate-600 font-medium"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={salvar.isPending}
              className="flex-1 py-3 bg-[#0c4a6e] hover:bg-[#0a3d5c] disabled:opacity-60 text-white rounded-xl font-medium"
            >
              {salvar.isPending ? 'Salvando...' : 'Salvar'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function Campo({ label, dica, children }: { label: string; dica?: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-sm font-medium text-slate-700 mb-1.5">{label}</label>
      {children}
      {dica && <p className="text-xs text-slate-400 mt-1">{dica}</p>}
    </div>
  );
}
