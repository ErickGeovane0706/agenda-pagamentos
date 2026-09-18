export interface Empresa {
  id: string;
  nome: string;
  ativo: boolean;
  /** Módulo de Estoque/PDV liberado. Nasce falso; só o MASTER liga. */
  pdvHabilitado: boolean;
}

export type EtapaOnboarding = 'LEMBRETES' | 'CHEQUES' | 'CONCLUIDO';

export interface Usuario {
  id: string;
  nome: string;
  email: string;
  perfil: 'MASTER' | 'ADMIN' | 'OPERADOR' | 'VIEWER';
  empresaId: string;
  onboardingEtapa: EtapaOnboarding;
}

export interface Loja {
  id: string;
  nome: string;
  cnpj?: string;
  descricao?: string;
  cor: string;
  ativo: boolean;
}

export type StatusBoleto = 'PENDENTE' | 'PAGO' | 'VENCIDO' | 'CANCELADO';
export type StatusPix = 'PENDENTE' | 'PAGO' | 'VENCIDO' | 'CANCELADO';
export type StatusCheque = 'PENDENTE' | 'COMPENSADO' | 'DEVOLVIDO' | 'CANCELADO';

export interface Boleto {
  id: string;
  lojaId: string;
  lojaNome: string;
  lojaCor: string;
  fornecedor: string;
  valor: number;
  vencimento: string;
  status: StatusBoleto;
  codigoBarras?: string;
  urlArquivo?: string;
  nomeArquivo?: string;
  arquivoKey?: string;
  observacoes?: string;
  pagoEm?: string;
  criadoEm: string;
}

export interface PagamentoPix {
  id: string;
  lojaId: string;
  lojaNome: string;
  lojaCor: string;
  fornecedor: string;
  valor: number;
  vencimento: string;
  status: StatusPix;
  chavePix: string;
  tipoChave?: string;
  observacoes?: string;
  arquivoKey?: string;
  pagoEm?: string;
  criadoEm: string;
}

export interface Cheque {
  id: string;
  lojaId: string;
  lojaNome: string;
  lojaCor: string;
  bancoId?: string;
  bancoNome?: string;
  fornecedor: string;
  valor: number;
  vencimento: string;
  status: StatusCheque;
  numeroCheque?: string;
  observacoes?: string;
  arquivoKey?: string;
  compensadoEm?: string;
  criadoEm: string;
}

export interface Banco {
  id: string;
  nome: string;
  codigo?: string;
  ativo: boolean;
}

export interface FiltrosAgenda {
  de?: string;
  ate?: string;
  status?: string;
  nome?: string;
}

export interface FiltrosRelatorio {
  de: string;
  ate: string;
  lojaId?: string;
  tipo?: 'BOLETO' | 'PIX' | 'CHEQUE' | 'TODOS';
  status?: string;
}

// ─────────────────── Módulo de Estoque e PDV ───────────────────

export interface Produto {
  id: string;
  lojaId: string;
  nome: string;
  quantidade: number;
  precoCusto: number;
  precoVenda: number;
  ativo: boolean;
}

export type StatusVenda = 'CONCLUIDA' | 'CANCELADA';

/**
 * Item de uma venda. `precoCusto` e `precoVenda` são o que foi praticado
 * naquele instante, não o preço atual do produto — é assim que o histórico
 * sobrevive a um reajuste de cadastro.
 */
export interface VendaItem {
  produtoId: string;
  produtoNome: string;
  quantidade: number;
  precoCusto: number;
  precoVenda: number;
}

export interface Venda {
  id: string;
  lojaId: string;
  clienteNome?: string;
  total: number;
  custoTotal: number;
  lucro: number;
  status: StatusVenda;
  vendidoEm: string;
  itens: VendaItem[];
}

/** Relatório de vendas de um período, numa loja. */
export interface RelatorioVendas {
  receita: number;
  custo: number;
  lucro: number;
  /** Nulo quando não houve venda no período — a tela mostra "—", nunca 0%. */
  margemPercentual: number | null;
  quantidadeVendas: number;
  porProduto: LinhaProdutoRelatorio[];
}

export interface LinhaProdutoRelatorio {
  produtoId: string;
  produtoNome: string;
  quantidade: number;
  receita: number;
  custo: number;
  lucro: number;
}
