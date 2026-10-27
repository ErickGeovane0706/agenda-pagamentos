import { Banco } from '../types';

/**
 * "Ag 1234 · Conta 56789-0", só com o que estiver preenchido; vazio quando o
 * banco não tem nenhum dos dois — aí a tela mostra só o nome.
 */
export function detalheConta(banco: Banco): string {
  return [banco.agencia && `Ag ${banco.agencia}`, banco.conta && `Conta ${banco.conta}`]
    .filter(Boolean)
    .join(' · ');
}
