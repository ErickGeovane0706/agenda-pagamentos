import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { Cheque } from '../../types';
import { CardCheque } from './CardCheque';

const CHEQUE: Cheque = {
  id: 'c1', lojaId: 'l1', lojaNome: 'Loja Centro', lojaCor: '#000',
  bancoId: 'bb2', bancoNome: 'Banco do Brasil', bancoAgencia: '4321', bancoConta: '11111-2',
  fornecedor: 'Fornecedor X', valor: 500, vencimento: '2099-01-10', status: 'PENDENTE',
  numeroCheque: '123', qtdAnexos: 0, criadoEm: '2026-10-27T10:00:00',
};

function cartao(cheque: Cheque) {
  const noop = () => {};
  return render(<CardCheque cheque={cheque} onEditar={noop} onExcluir={noop} onAnexos={noop} onMudarStatus={noop} />).container;
}

describe('CardCheque', () => {
  it('mostra agência e conta do banco, para distinguir duas contas do mesmo banco', () => {
    expect(cartao(CHEQUE).textContent).toContain('Ag 4321 · Conta 11111-2');
  });

  it('banco sem agência e conta mostra só o nome, sem "Ag" vazio', () => {
    const texto = cartao({ ...CHEQUE, bancoAgencia: undefined, bancoConta: undefined }).textContent;
    expect(texto).toContain('Banco do Brasil');
    expect(texto).not.toMatch(/Ag |Conta/);
  });
});
