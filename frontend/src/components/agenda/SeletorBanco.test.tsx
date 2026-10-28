import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Banco } from '../../types';
import { SeletorBanco } from './SeletorBanco';

const BB_CENTRO: Banco = { id: 'bb1', nome: 'Banco do Brasil', agencia: '1234', conta: '56789-0', ativo: true };
const BB_SUL: Banco = { id: 'bb2', nome: 'Banco do Brasil', agencia: '4321', conta: '11111-2', ativo: true };
const SICOOB: Banco = { id: 'sic', nome: 'Sicoob', ativo: true };
const BANCOS = [BB_CENTRO, BB_SUL, SICOOB];

describe('SeletorBanco', () => {
  it('sem escolha, o campo pede para selecionar', () => {
    render(<SeletorBanco bancos={BANCOS} value="" onChange={() => {}} />);
    expect(screen.getByRole('button', { name: /Selecione o banco/ })).toBeTruthy();
  });

  it('mostra o banco escolhido com agência e conta', () => {
    render(<SeletorBanco bancos={BANCOS} value="bb2" onChange={() => {}} />);
    const campo = screen.getByRole('button', { name: /Banco do Brasil/ });
    expect(campo.textContent).toContain('Ag 4321 · Conta 11111-2');
  });

  it('abre a lista, distingue as duas contas do mesmo banco e fecha ao escolher', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<SeletorBanco bancos={BANCOS} value="" onChange={onChange} />);

    await user.click(screen.getByRole('button', { name: /Selecione o banco/ }));
    const lista = screen.getByRole('dialog', { name: 'Escolha a conta do cheque' });
    expect(lista.textContent).toContain('Ag 1234 · Conta 56789-0');
    expect(lista.textContent).toContain('Ag 4321 · Conta 11111-2');

    await user.click(screen.getByRole('button', { name: /Ag 4321/ }));

    expect(onChange).toHaveBeenCalledWith('bb2');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('banco sem agência e conta mostra só o nome, sem "Ag" vazio', async () => {
    const user = userEvent.setup();
    render(<SeletorBanco bancos={BANCOS} value="sic" onChange={() => {}} />);
    expect(screen.getByRole('button', { name: /Sicoob/ }).textContent).not.toMatch(/Ag|Conta/);

    await user.click(screen.getByRole('button', { name: /Sicoob/ }));
    const botoesSicoob = screen.getAllByRole('button', { name: /Sicoob/ });
    const opcaoSicoob = botoesSicoob[botoesSicoob.length - 1];
    expect(opcaoSicoob.textContent).not.toMatch(/Ag|Conta/);
  });

  it('sem bancos cadastrados, a lista diz onde cadastrar', async () => {
    const user = userEvent.setup();
    render(<SeletorBanco bancos={[]} value="" onChange={() => {}} />);
    await user.click(screen.getByRole('button', { name: /Selecione o banco/ }));
    expect(screen.getByRole('dialog').textContent).toContain('Configurações');
  });
});
