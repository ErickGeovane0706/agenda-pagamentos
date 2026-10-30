import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import api from '../../api/client';
import { Banco } from '../../types';
import { ModalCheque } from './ModalCheque';

vi.mock('../../api/client', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }));

const get = vi.mocked(api.get);

function abrirNovoCheque(bancos: Banco[]) {
  get.mockResolvedValue({ data: bancos });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <ModalCheque cheque={null} lojaId="loja-1" onFechar={() => {}} onSalvo={() => {}} />
    </QueryClientProvider>,
  );
}

describe('ModalCheque — banco', () => {
  beforeEach(() => get.mockReset());

  it('com um banco só, ele já vem escolhido', async () => {
    abrirNovoCheque([{ id: 'cx', nome: 'Caixa', agencia: '0001', conta: '23456-7', ativo: true }]);
    await waitFor(() => expect(screen.getByRole('button', { name: /Caixa/ })).toBeTruthy());
  });

  it('com vários bancos, ninguém é escolhido por ele', async () => {
    abrirNovoCheque([
      { id: 'a', nome: 'Caixa', ativo: true },
      { id: 'b', nome: 'Sicoob', ativo: true },
    ]);
    await waitFor(() => expect(get).toHaveBeenCalled());
    expect(screen.getByRole('button', { name: /Selecione o banco/ })).toBeTruthy();
  });
});

describe('ModalCheque — nota fiscal / pedido', () => {
  const post = vi.mocked(api.post);

  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  async function preencherObrigatorios(user: ReturnType<typeof userEvent.setup>) {
    await waitFor(() => expect(screen.getByRole('button', { name: /Caixa/ })).toBeTruthy());
    await user.type(screen.getByPlaceholderText('Nome do fornecedor'), 'Ambev');
    await user.type(screen.getByPlaceholderText('0,00'), '100');
    await user.type(document.querySelector('input[type="date"]') as HTMLInputElement, '2026-10-05');
    await user.type(screen.getByPlaceholderText('Número do cheque'), '123');
  }

  it('é opcional: o cheque sai sem ela', async () => {
    post.mockResolvedValueOnce({ data: {} });
    const user = userEvent.setup();
    abrirNovoCheque([{ id: 'cx', nome: 'Caixa', ativo: true }]);
    await preencherObrigatorios(user);
    await user.click(screen.getByRole('button', { name: 'Criar cheque' }));

    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(post.mock.calls[0][1]).toMatchObject({ notaFiscalPedido: null });
  });

  it('vai no cheque como texto, sem o espaço sobrando', async () => {
    post.mockResolvedValueOnce({ data: {} });
    const user = userEvent.setup();
    abrirNovoCheque([{ id: 'cx', nome: 'Caixa', ativo: true }]);
    await preencherObrigatorios(user);
    await user.type(screen.getByLabelText(/Nota fiscal \/ pedido/), ' 000 NF/1 ');
    await user.click(screen.getByRole('button', { name: 'Criar cheque' }));

    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(post.mock.calls[0][1]).toMatchObject({ notaFiscalPedido: '000 NF/1' });
  });

  it('na edição, vem preenchida com o que o cheque já tem', () => {
    get.mockResolvedValue({ data: [] });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <ModalCheque
          cheque={{
            id: 'c-1', lojaId: 'loja-1', lojaNome: 'Centro', lojaCor: '#000', fornecedor: 'Ambev', valor: 100,
            vencimento: '2026-10-05', status: 'PENDENTE', numeroCheque: '123', qtdAnexos: 0, criadoEm: '2026-10-01',
            notaFiscalPedido: 'NF 4521/1',
          }}
          lojaId="loja-1" onFechar={() => {}} onSalvo={() => {}}
        />
      </QueryClientProvider>,
    );

    expect((screen.getByLabelText(/Nota fiscal \/ pedido/) as HTMLInputElement).value).toBe('NF 4521/1');
  });
});
