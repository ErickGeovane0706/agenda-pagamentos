import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import api from '../../api/client';
import { ModalPixRecorrente } from './ModalPixRecorrente';

vi.mock('../../api/client', () => ({ default: { post: vi.fn() } }));

const post = vi.mocked(api.post);

async function preencher(dia: string, inicio: string, vezes: string) {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  const onSalvo = vi.fn();
  render(
    <QueryClientProvider client={client}>
      <ModalPixRecorrente lojaId="loja-1" onFechar={() => {}} onSalvo={onSalvo} />
    </QueryClientProvider>,
  );
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Fornecedor'), 'Aluguel');
  await user.type(screen.getByLabelText(/Valor de cada/), '1500');
  await user.type(screen.getByLabelText('Chave PIX'), 'chave-do-locador');
  await user.clear(screen.getByLabelText('Todo dia'));
  await user.type(screen.getByLabelText('Todo dia'), dia);
  fireEvent.change(screen.getByLabelText('A partir de'), { target: { value: inicio } });
  await user.clear(screen.getByLabelText('Vezes'));
  await user.type(screen.getByLabelText('Vezes'), vezes);
  return { user, onSalvo };
}

describe('ModalPixRecorrente', () => {
  beforeEach(() => post.mockReset());

  it('mostra a prévia das datas e o total, deixa tirar uma e cria só as que ficaram', async () => {
    post.mockResolvedValueOnce({ data: [] });
    const { user, onSalvo } = await preencher('27', '2026-11', '3');

    const dialogo = screen.getByRole('dialog');
    expect(dialogo.textContent).toContain('27/11/2026');
    expect(dialogo.textContent).toContain('27/12/2026');
    expect(dialogo.textContent).toContain('27/01/2027');
    expect(dialogo.textContent).toMatch(/4\.500,00/);

    await user.click(screen.getByRole('button', { name: 'Remover 27/12/2026' }));
    expect(dialogo.textContent).not.toContain('27/12/2026');
    expect(dialogo.textContent).toMatch(/3\.000,00/);

    await user.click(screen.getByRole('button', { name: 'Criar 2 PIX' }));

    await waitFor(() => expect(onSalvo).toHaveBeenCalled());
    expect(post).toHaveBeenCalledWith('/pix/recorrente', expect.objectContaining({
      lojaId: 'loja-1', fornecedor: 'Aluguel', valor: 1500, chavePix: 'chave-do-locador',
      vencimentos: ['2026-11-27', '2027-01-27'],
    }));
  });

  it('data avulsa entra na lista em ordem e sem repetir', async () => {
    const { user } = await preencher('27', '2026-11', '2');

    const avulsa = screen.getByLabelText('Outra data');
    fireEvent.change(avulsa, { target: { value: '2026-12-05' } });
    await user.click(screen.getByRole('button', { name: 'Adicionar' }));
    fireEvent.change(avulsa, { target: { value: '2026-11-27' } });
    await user.click(screen.getByRole('button', { name: 'Adicionar' }));

    const datas = screen.getAllByRole('button', { name: /^Remover / }).map(b => b.getAttribute('aria-label'));
    expect(datas).toEqual(['Remover 27/11/2026', 'Remover 05/12/2026', 'Remover 27/12/2026']);
  });
});
