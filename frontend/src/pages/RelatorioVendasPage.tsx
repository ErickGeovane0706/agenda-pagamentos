import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { BarChart3, TrendingUp, Package, CalendarRange } from 'lucide-react';
import api from '../api/client';
import { useAuthStore } from '../store/authStore';
import { RelatorioVendas } from '../types';
import { ValorMonetario } from '../components/ValorMonetario';
import { AbasPdv, formatarQuantidade } from './ProdutosPage';

/**
 * Formata a data no `YYYY-MM-DD` que a API espera, a partir do calendário
 * <b>local</b>.
 *
 * Não usar `toISOString()` aqui: ele converte para UTC, e no Brasil (UTC-3)
 * isso devolve a data de amanhã a partir das 21h. O PDV é usado à noite — o
 * relatório de "Hoje" viraria o de amanhã e mostraria zero venda justamente
 * para quem fechou o caixa tarde.
 */
function paraIso(d: Date) {
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  const dia = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mes}-${dia}`;
}

/** Hoje e N dias atrás, no calendário local. */
function periodo(dias: number) {
  const ate = new Date();
  const de = new Date();
  de.setDate(de.getDate() - dias);
  return { de: paraIso(de), ate: paraIso(ate) };
}

const ATALHOS = [
  { label: 'Hoje', dias: 0 },
  { label: '7 dias', dias: 6 },
  { label: '30 dias', dias: 29 },
];

const dataBR = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString('pt-BR');

export default function RelatorioVendasPage() {
  const lojaAtiva = useAuthStore((s) => s.lojaAtiva);
  const hoje = paraIso(new Date());

  /** Qual atalho está ativo, ou `null` quando o período é escolhido a dedo. */
  const [diasEscolhidos, setDiasEscolhidos] = useState<number | null>(6);
  const [deEscolhido, setDeEscolhido] = useState(hoje);
  const [ateEscolhido, setAteEscolhido] = useState(hoje);

  const { de, ate } = diasEscolhidos !== null
    ? periodo(diasEscolhidos)
    : { de: deEscolhido, ate: ateEscolhido };

  /** Abre o período personalizado já preenchido com o que estava em tela. */
  const abrirPersonalizado = () => {
    if (diasEscolhidos !== null) {
      const atual = periodo(diasEscolhidos);
      setDeEscolhido(atual.de);
      setAteEscolhido(atual.ate);
    }
    setDiasEscolhidos(null);
  };

  // Datas invertidas não travam a tela nem viram erro: arrastam a outra ponta
  // junto. Um período de fim antes do começo devolveria zero e pareceria "não
  // vendi nada", que é a leitura errada.
  const mudarDe = (valor: string) => {
    setDeEscolhido(valor);
    if (valor > ateEscolhido) setAteEscolhido(valor);
  };
  const mudarAte = (valor: string) => {
    setAteEscolhido(valor);
    if (valor < deEscolhido) setDeEscolhido(valor);
  };

  const { data, isLoading } = useQuery<RelatorioVendas>({
    queryKey: ['relatorio-vendas', lojaAtiva, de, ate],
    queryFn: () => api.get('/vendas/relatorio', { params: { lojaId: lojaAtiva, de, ate } })
      .then((r) => r.data),
    enabled: !!lojaAtiva,
  });

  if (!lojaAtiva) {
    return (
      <div className="text-center py-16">
        <BarChart3 className="w-12 h-12 text-slate-300 mx-auto mb-3" />
        <p className="text-slate-500 font-medium">Escolha uma loja primeiro</p>
        <Link to="/lojas" className="text-[#0c4a6e] text-sm font-medium mt-2 inline-block">
          Ir para as lojas
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto">
      <AbasPdv atual="relatorio" />

      <h2 className="text-xl font-bold text-slate-900 mb-1">Relatório de vendas</h2>
      <p className="text-sm text-slate-500 mb-5">
        {data
          ? `${data.quantidadeVendas} venda(s) · ${de === ate ? dataBR(de) : `${dataBR(de)} a ${dataBR(ate)}`}`
          : 'Carregando...'}
      </p>

      <div className="flex flex-wrap gap-2 mb-3">
        {ATALHOS.map((a) => (
          <button
            key={a.dias}
            onClick={() => setDiasEscolhidos(a.dias)}
            className={`px-4 py-2 rounded-xl text-sm font-medium transition-colors ${
              diasEscolhidos === a.dias
                ? 'bg-[#0c4a6e] text-white'
                : 'bg-white border border-slate-200 text-slate-600'
            }`}
          >
            {a.label}
          </button>
        ))}
        <button
          onClick={abrirPersonalizado}
          className={`flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm font-medium transition-colors ${
            diasEscolhidos === null
              ? 'bg-[#0c4a6e] text-white'
              : 'bg-white border border-slate-200 text-slate-600'
          }`}
        >
          <CalendarRange className="w-4 h-4" />
          Escolher data
        </button>
      </div>

      {diasEscolhidos === null && (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-4 mb-5">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="relatorio-de" className="block text-xs font-medium text-slate-600 mb-1.5">
                De
              </label>
              {/* `type="date"` nativo de propósito: no celular abre o calendário
                  do próprio sistema, que o usuário já sabe usar. Componente de
                  calendário próprio seria mais bonito e menos familiar. */}
              <input
                id="relatorio-de"
                type="date"
                value={deEscolhido}
                max={hoje}
                onChange={(e) => mudarDe(e.target.value)}
                className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-base outline-none focus:ring-2 focus:ring-[#0c4a6e]"
              />
            </div>
            <div>
              <label htmlFor="relatorio-ate" className="block text-xs font-medium text-slate-600 mb-1.5">
                Até
              </label>
              <input
                id="relatorio-ate"
                type="date"
                value={ateEscolhido}
                max={hoje}
                onChange={(e) => mudarAte(e.target.value)}
                className="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-base outline-none focus:ring-2 focus:ring-[#0c4a6e]"
              />
            </div>
          </div>
          <p className="text-xs text-slate-400 mt-2">
            Os dois dias entram na conta. Para um dia só, deixe as duas datas iguais.
          </p>
        </div>
      )}

      {isLoading || !data ? (
        <div className="grid grid-cols-2 gap-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="bg-white rounded-2xl border border-slate-100 p-4 h-24 animate-pulse" />
          ))}
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 mb-6">
            <Cartao titulo="Receita" valor={data.receita} />
            <Cartao titulo="Custo" valor={data.custo} />
            <Cartao titulo="Lucro" valor={data.lucro} destaque />
            <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-4">
              <p className="text-xs text-slate-500 mb-1">Margem</p>
              {/* Margem nula = não houve venda. Mostrar 0% seria mentira: 0% é
                  vender sem lucro, que é diferente de não vender. */}
              <p className="text-xl font-bold text-slate-900">
                {data.margemPercentual === null
                  ? <span className="text-slate-300">—</span>
                  : `${data.margemPercentual.toFixed(2).replace('.', ',')}%`}
              </p>
            </div>
          </div>

          <h3 className="text-base font-bold text-slate-900 mb-3 flex items-center gap-2">
            <TrendingUp className="w-4 h-4 text-slate-400" />
            Por produto
          </h3>

          {data.porProduto.length === 0 ? (
            <div className="text-center py-12 bg-white rounded-2xl border border-slate-100">
              <Package className="w-10 h-10 text-slate-300 mx-auto mb-2" />
              <p className="text-slate-500 text-sm">Nenhuma venda no período</p>
            </div>
          ) : (
            <div className="space-y-2">
              {data.porProduto.map((p) => (
                <div key={p.produtoId} className="bg-white rounded-xl border border-slate-100 p-3.5">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="font-medium text-slate-900 truncate">{p.produtoNome}</p>
                      <p className="text-xs text-slate-400 mt-0.5">
                        {formatarQuantidade(p.quantidade)} vendido(s)
                      </p>
                    </div>
                    <div className="text-right flex-shrink-0">
                      <p className="text-sm text-slate-500">
                        <ValorMonetario valor={p.receita} /> de receita
                      </p>
                      <p className="text-sm text-emerald-700 font-semibold">
                        <ValorMonetario valor={p.lucro} /> de lucro
                      </p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          <p className="text-xs text-slate-400 mt-6">
            Os valores são os praticados em cada venda, não os do cadastro atual — mudar o
            preço de um produto hoje não altera o lucro de uma venda antiga.
          </p>
        </>
      )}
    </div>
  );
}

function Cartao({ titulo, valor, destaque }: { titulo: string; valor: number; destaque?: boolean }) {
  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-4">
      <p className="text-xs text-slate-500 mb-1">{titulo}</p>
      <ValorMonetario
        valor={valor}
        className={`text-xl font-bold ${destaque ? 'text-emerald-700' : 'text-slate-900'}`}
      />
    </div>
  );
}
