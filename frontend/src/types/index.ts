export interface Empresa {
  id: string;
  nome: string;
  ativo: boolean;
}

export interface Usuario {
  id: string;
  nome: string;
  email: string;
  perfil: 'MASTER' | 'ADMIN' | 'OPERADOR' | 'VIEWER';
  empresaId: string;
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
