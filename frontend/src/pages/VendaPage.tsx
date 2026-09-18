import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ShoppingCart, Plus, X, Check, Package, RotateCcw, Printer } from 'lucide-react';
import api from '../api/client';
import { useAuthStore } from '../store/authStore';
import { useMutationToast } from '../hooks/useMutationToast';
import { useToastStore } from '../store/toastStore';
import { Produto, Venda, Loja } from '../types';
import { ValorMonetario } from '../components/ValorMonetario';
import { AbasPdv, formatarQuantidade } from './ProdutosPage';
import { mensagemDoErro } from '../utils/erroApi';
import { imprimirCupom } from '../utils/cupom';

/** Uma linha do carrinho, antes de virar venda. */
interface LinhaCarrinho {
  produtoId: string;
  nome: string;
  estoque: number;
  quantidade: string;
  precoVenda: string;
}

export default function VendaPage() {
  const lojaAtiva = useAuthStore((s) => s.lojaAtiva);
  const usuario = useAuthStore((s) => s.usuario);
  const queryClient = useQueryClient();
  const addToast = useToastStore((s) => s.addToast);

  const [carrinho, setCarrinho] = useState<LinhaCarrinho[]>([]);
  const [clienteNome, setClienteNome] = useState('');
  const [seletorAberto, setSeletorAberto] = useState(false);
  /** Venda recém-registrada, aguardando o usuário decidir se imprime. */
  const [vendaParaCupom, setVendaParaCupom] = useState<Venda | null>(null);

  const podeVender = usuario?.perfil === 'ADMIN' || usuario?.perfil === 'OPERADOR';

  const { data: produtos = [] } = useQuery<Produto[]>({
    queryKey: ['produtos', lojaAtiva],
    queryFn: () => api.get('/produtos', { params: { lojaId: lojaAtiva } }).then((r) => r.data),
    enabled: !!lojaAtiva,
  });

  // O nome da loja entra no cupom. Vem da lista de lojas, que o react-query já
  // tem em cache desde a tela de seleção.
  const { data: lojas = [] } = useQuery<Loja[]>({
    queryKey: ['lojas', usuario?.empresaId],
    queryFn: () => api.get('/lojas').then((r) => r.data),
  });
  const nomeDaLoja = lojas.find((l) => l.id === lojaAtiva)?.nome ?? 'Minha loja';

  const { data: vendas = [] } = useQuery<Venda[]>({
    queryKey: ['vendas', lojaAtiva],
    queryFn: () => api.get('/vendas', { params: { lojaId: lojaAtiva } }).then((r) => r.data),
    enabled: !!lojaAtiva,
  });

  const finalizar = useMutationToast({
    mutationFn: () => api.post<Venda>('/vendas', {
      lojaId: lojaAtiva,
      clienteNome: clienteNome.trim() || null,
      itens: carrinho.map((l) => ({
        produtoId: l.produtoId,
        quantidade: Number(l.quantidade),
        precoVenda: Number(l.precoVenda),
      })),
    }),
    successMessage: 'Venda registrada',
    onSuccess: (resposta) => {
      setCarrinho([]);
      setClienteNome('');
      // Guarda a venda recém-feita para oferecer o cupom. O cupom NÃO sai
      // automático: impressão sem pedir desperdiça bobina quando o cliente não
      // quer papel, e o dono da loja é quem paga a bobina.
      setVendaParaCupom(resposta.data);
      // O estoque mudou, então a lista de produtos precisa vir de novo.
      queryClient.invalidateQueries({ queryKey: ['produtos'] });
      queryClient.invalidateQueries({ queryKey: ['vendas'] });
    },
    // Aqui está o motivo de o backend responder 409 e não 400: a mensagem diz
    // qual produto faltou e quanto havia, e é isso que resolve o problema de
    // quem está no caixa.
    onError: (erro) => addToast('error', mensagemDoErro(erro, 'Não foi possível registrar a venda')),
  });

  const cancelar = useMutationToast({
    mutationFn: (id: string) => api.post(`/vendas/${id}/cancelar`),
    successMessage: 'Venda cancelada e estoque devolvido',
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['produtos'] });
      queryClient.invalidateQueries({ queryKey: ['vendas'] });
    },
    onError: (erro) => addToast('error', mensagemDoErro(erro, 'Não foi possível cancelar a venda')),
  });

  const adicionar = (p: Produto) => {
    setSeletorAberto(false);
    if (carrinho.some((l) => l.produtoId === p.id)) {
      addToast('error', `${p.nome} já está na venda`);
      return;
    }
    setCarrinho((atual) => [...atual, {
      produtoId: p.id,
      nome: p.nome,
      estoque: p.quantidade,
      quantidade: '1',
      precoVenda: String(p.precoVenda),
    }]);
  };

  const alterarLinha = (produtoId: string, campo: 'quantidade' | 'precoVenda', valor: string) => {
    setCarrinho((atual) => atual.map((l) => l.produtoId === produtoId ? { ...l, [campo]: valor } : l));
  };

  const total = carrinho.reduce(
    (soma, l) => soma + (Number(l.quantidade) || 0) * (Number(l.precoVenda) || 0), 0);

  const carrinhoValido = carrinho.length > 0
    && carrinho.every((l) => Number(l.quantidade) > 0 && Number(l.precoVenda) >= 0);

  if (!lojaAtiva) {
    return (
      <div className="text-center py-16">
        <ShoppingCart className="w-12 h-12 text-slate-300 mx-auto mb-3" />
        <p className="text-slate-500 font-medium">Escolha uma loja primeiro</p>
        <Link to="/lojas" className="text-[#0c4a6e] text-sm font-medium mt-2 inline-block">
          Ir para as lojas
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto">
      <AbasPdv atual="venda" />

      <h2 className="text-xl font-bold text-slate-900 mb-5">Nova venda</h2>

      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-4 mb-6">
        {carrinho.length === 0 ? (
          <div className="text-center py-8">
            <ShoppingCart className="w-10 h-10 text-slate-300 mx-auto mb-2" />
            <p className="text-slate-500 text-sm">Nenhum produto adicionado</p>
          </div>
        ) : (
          <div className="space-y-3 mb-4">
            {carrinho.map((l) => {
              const excede = Number(l.quantidade) > l.estoque;
              return (
                <div key={l.produtoId} className="border border-slate-100 rounded-xl p-3">
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <div className="min-w-0">
                      <p className="font-medium text-slate-900 truncate">{l.nome}</p>
                      <p className="text-xs text-slate-400">
                        {formatarQuantidade(l.estoque)} em estoque
                      </p>
                    </div>
                    <button
                      onClick={() => setCarrinho((a) => a.filter((x) => x.produtoId !== l.produtoId))}
                      className="p-1.5 rounded-lg hover:bg-slate-100 flex-shrink-0"
                      aria-label={`Remover ${l.nome}`}
                    >
                      <X className="w-4 h-4 text-slate-400" />
                    </button>
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-xs text-slate-500 mb-1">Quantidade</label>
                      <input
                        value={l.quantidade}
                        onChange={(e) => alterarLinha(l.produtoId, 'quantidade', e.target.value)}
                        type="number"
                        min="0"
                        step="0.001"
                        inputMode="decimal"
                        className={`w-full px-3 py-2.5 border rounded-lg text-base outline-none focus:ring-2 ${
                          excede ? 'border-amber-400 focus:ring-amber-400' : 'border-slate-200 focus:ring-[#0c4a6e]'
                        }`}
                      />
                    </div>
                    <div>
                      <label className="block text-xs text-slate-500 mb-1">Preço unitário</label>
                      <input
                        value={l.precoVenda}
                        onChange={(e) => alterarLinha(l.produtoId, 'precoVenda', e.target.value)}
                        type="number"
                        min="0"
                        step="0.01"
                        inputMode="decimal"
                        className="w-full px-3 py-2.5 border border-slate-200 rounded-lg text-base outline-none focus:ring-2 focus:ring-[#0c4a6e]"
                      />
                    </div>
                  </div>

                  {/* Aviso, não bloqueio: quem decide se há estoque é o banco no
                      instante da venda, não esta tela. Travar aqui daria falso
                      conforto e ainda assim poderia estar errado. */}
                  {excede && (
                    <p className="text-xs text-amber-600 mt-2">
                      Acima do estoque registrado ({formatarQuantidade(l.estoque)})
                    </p>
                  )}

                  <p className="text-sm text-slate-500 mt-2">
                    Subtotal: <ValorMonetario
                      valor={(Number(l.quantidade) || 0) * (Number(l.precoVenda) || 0)}
                      className="font-medium text-slate-800"
                    />
                  </p>
                </div>
              );
            })}
          </div>
        )}

        <button
          onClick={() => setSeletorAberto(true)}
          disabled={!podeVender}
          className="w-full flex items-center justify-center gap-2 py-3 border-2 border-dashed border-slate-200 rounded-xl text-slate-500 hover:border-[#0c4a6e] hover:text-[#0c4a6e] disabled:opacity-50 transition-colors"
        >
          <Plus className="w-4 h-4" />
          Adicionar produto
        </button>

        <div className="mt-4 pt-4 border-t border-slate-100">
          <label className="block text-sm font-medium text-slate-700 mb-1.5">
            Cliente <span className="text-slate-400 font-normal">(opcional)</span>
          </label>
          <input
            value={clienteNome}
            onChange={(e) => setClienteNome(e.target.value)}
            maxLength={300}
            placeholder="Nome de quem está comprando"
            className="w-full px-4 py-3 border border-slate-200 rounded-xl text-base outline-none focus:ring-2 focus:ring-[#0c4a6e]"
          />
        </div>

        <div className="mt-4 pt-4 border-t border-slate-100 flex items-center justify-between">
          <span className="text-slate-500">Total</span>
          <ValorMonetario valor={total} className="text-2xl font-bold text-slate-900" />
        </div>

        <button
          onClick={() => {
            if (confirm(`Registrar a venda de ${new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(total)}?`)) {
              finalizar.mutate();
            }
          }}
          disabled={!carrinhoValido || finalizar.isPending || !podeVender}
          className="w-full mt-4 flex items-center justify-center gap-2 py-4 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold rounded-xl text-base transition-colors"
        >
          <Check className="w-5 h-5" />
          {finalizar.isPending ? 'Registrando...' : 'Finalizar venda'}
        </button>
      </div>

      <h3 className="text-base font-bold text-slate-900 mb-3">Últimas vendas</h3>
      {vendas.length === 0 ? (
        <p className="text-slate-400 text-sm bg-white rounded-2xl border border-slate-100 p-6 text-center">
          Nenhuma venda registrada nesta loja
        </p>
      ) : (
        <div className="space-y-2">
          {vendas.map((v) => (
            <div
              key={v.id}
              className={`bg-white rounded-xl border p-3.5 ${
                v.status === 'CANCELADA' ? 'border-slate-100 opacity-60' : 'border-slate-100'
              }`}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <ValorMonetario valor={v.total} className="font-semibold text-slate-900" />
                    {v.status === 'CANCELADA' && (
                      <span className="text-xs bg-slate-200 text-slate-600 px-2 py-0.5 rounded-full">
                        cancelada
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-slate-400 mt-0.5">
                    {new Date(v.vendidoEm).toLocaleString('pt-BR', {
                      day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
                    })}
                    {v.clienteNome && ` · ${v.clienteNome}`}
                  </p>
                  <p className="text-xs text-slate-500 mt-1 truncate">
                    {v.itens.map((i) => `${formatarQuantidade(i.quantidade)}x ${i.produtoNome}`).join(', ')}
                  </p>
                  {v.status === 'CONCLUIDA' && (
                    <p className="text-xs text-emerald-700 mt-1">
                      lucro <ValorMonetario valor={v.lucro} className="font-medium" />
                    </p>
                  )}
                </div>

                <div className="flex flex-col gap-1 flex-shrink-0">
                  {/* Reimprimir vale para venda cancelada também: o cliente pode
                      precisar do papel da compra que foi desfeita. */}
                  <button
                    onClick={() => imprimirCupom(v, nomeDaLoja)}
                    className="flex items-center gap-1.5 text-xs text-slate-500 hover:text-[#0c4a6e] px-2.5 py-2 rounded-lg hover:bg-slate-100"
                  >
                    <Printer className="w-3.5 h-3.5" />
                    Cupom
                  </button>

                  {v.status === 'CONCLUIDA' && podeVender && (
                    <button
                      onClick={() => {
                        if (confirm('Cancelar esta venda?\n\nO estoque volta e a venda sai do relatório, mas continua no histórico.')) {
                          cancelar.mutate(v.id);
                        }
                      }}
                      className="flex items-center gap-1.5 text-xs text-slate-500 hover:text-red-600 px-2.5 py-2 rounded-lg hover:bg-red-50"
                    >
                      <RotateCcw className="w-3.5 h-3.5" />
                      Cancelar
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {vendaParaCupom && (
        <div className="fixed inset-0 bg-slate-900/50 flex items-end sm:items-center justify-center z-50 p-0 sm:p-4">
          <div className="bg-white w-full sm:max-w-sm rounded-t-2xl sm:rounded-2xl p-5 pb-[calc(1.25rem+env(safe-area-inset-bottom))] text-center">
            <div className="w-12 h-12 rounded-full bg-emerald-100 flex items-center justify-center mx-auto mb-3">
              <Check className="w-6 h-6 text-emerald-700" />
            </div>
            <p className="font-bold text-slate-900 text-lg">Venda registrada</p>
            <ValorMonetario valor={vendaParaCupom.total} className="text-2xl font-bold text-slate-900 block mt-1" />

            <button
              onClick={() => imprimirCupom(vendaParaCupom, nomeDaLoja)}
              className="w-full mt-5 flex items-center justify-center gap-2 py-3.5 bg-[#0c4a6e] hover:bg-[#0a3d5c] text-white font-semibold rounded-xl"
            >
              <Printer className="w-5 h-5" />
              Imprimir cupom
            </button>
            <button
              onClick={() => setVendaParaCupom(null)}
              className="w-full mt-2 py-3 text-slate-500 font-medium"
            >
              Não precisa
            </button>
          </div>
        </div>
      )}

      {seletorAberto && (
        <div className="fixed inset-0 bg-slate-900/50 flex items-end sm:items-center justify-center z-50 p-0 sm:p-4">
          <div className="bg-white w-full sm:max-w-md rounded-t-2xl sm:rounded-2xl max-h-[80vh] flex flex-col">
            <div className="flex items-center justify-between p-4 border-b border-slate-100">
              <h3 className="font-bold text-slate-900">Escolha o produto</h3>
              <button onClick={() => setSeletorAberto(false)} className="p-2 rounded-lg hover:bg-slate-100">
                <X className="w-5 h-5 text-slate-400" />
              </button>
            </div>

            <div className="overflow-y-auto p-2 pb-[calc(0.5rem+env(safe-area-inset-bottom))]">
              {produtos.length === 0 ? (
                <div className="text-center py-10">
                  <Package className="w-10 h-10 text-slate-300 mx-auto mb-2" />
                  <p className="text-slate-500 text-sm">Nenhum produto cadastrado</p>
                  <Link to="/produtos" className="text-[#0c4a6e] text-sm font-medium mt-2 inline-block">
                    Cadastrar produto
                  </Link>
                </div>
              ) : (
                produtos.map((p) => (
                  <button
                    key={p.id}
                    onClick={() => adicionar(p)}
                    className="w-full text-left p-3.5 rounded-xl hover:bg-slate-50 flex items-center justify-between gap-3"
                  >
                    <div className="min-w-0">
                      <p className="font-medium text-slate-900 truncate">{p.nome}</p>
                      <p className="text-xs text-slate-400">
                        {formatarQuantidade(p.quantidade)} em estoque
                      </p>
                    </div>
                    <ValorMonetario valor={p.precoVenda} className="text-emerald-700 font-medium flex-shrink-0" />
                  </button>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
