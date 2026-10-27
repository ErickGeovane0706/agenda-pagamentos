import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import api from '../api/client';
import { Banco } from '../types';
import { FormBanco } from './FormBanco';

vi.mock('../api/client', () => ({ default: { post: vi.fn(), put: vi.fn() } }));

const post = vi.mocked(api.post);
const put = vi.mocked(api.put);

function abrir(banco: Banco | null, onSalvo = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <FormBanco banco={banco} onFechar={() => {}} onSalvo={onSalvo} />
    </QueryClientProvider>,
  );
  return { user: userEvent.setup(), onSalvo };
}

describe('FormBanco', () => {
  beforeEach(() => { post.mockReset(); put.mockReset(); });

  it('cria banco com agência e conta', async () => {
    post.mockResolvedValueOnce({ data: {} });
    const { user, onSalvo } = abrir(null);

    await user.type(screen.getByLabelText('Nome'), 'Banco do Brasil');
    await user.type(screen.getByLabelText('Agência'), '1234');
    await user.type(screen.getByLabelText('Conta'), '56789-0');
    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(onSalvo).toHaveBeenCalled());
    expect(post).toHaveBeenCalledWith('/bancos', expect.objectContaining({
      nome: 'Banco do Brasil', agencia: '1234', conta: '56789-0',
    }));
  });

  it('edita um banco já cadastrado, acrescentando agência e conta', async () => {
    put.mockResolvedValueOnce({ data: {} });
    const { user, onSalvo } = abrir({ id: 'bb', nome: 'Banco do Brasil', codigo: '001', ativo: true });

    expect((screen.getByLabelText('Nome') as HTMLInputElement).value).toBe('Banco do Brasil');
    await user.type(screen.getByLabelText('Agência'), '4321');
    await user.type(screen.getByLabelText('Conta'), '11111-2');
    await user.click(screen.getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(onSalvo).toHaveBeenCalled());
    expect(put).toHaveBeenCalledWith('/bancos/bb', {
      nome: 'Banco do Brasil', codigo: '001', agencia: '4321', conta: '11111-2',
    });
    expect(post).not.toHaveBeenCalled();
  });
});
