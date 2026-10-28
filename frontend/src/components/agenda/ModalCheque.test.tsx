import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
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
