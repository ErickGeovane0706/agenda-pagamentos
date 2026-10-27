import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import api from '../api/client';
import { Loja } from '../types';
import { ModalOrdenarLojas } from './ModalOrdenarLojas';

vi.mock('../api/client', () => ({ default: { put: vi.fn() } }));

const put = vi.mocked(api.put);
const loja = (id: string, nome: string): Loja => ({ id, nome, cor: '#0c4a6e', ativo: true });
const LOJAS = [loja('a', 'Centro'), loja('b', 'Shopping'), loja('c', 'Bairro')];

function abrir(onSalvo = vi.fn(), onFechar = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <ModalOrdenarLojas lojas={LOJAS} onFechar={onFechar} onSalvo={onSalvo} />
    </QueryClientProvider>,
  );
  return { user: userEvent.setup(), onSalvo, onFechar };
}

const nomesNaTela = () => screen.getAllByRole('listitem').map(li => li.querySelector('span')!.textContent);

describe('ModalOrdenarLojas', () => {
  beforeEach(() => put.mockReset());

  it('sobe e desce lojas e salva os IDs na ordem nova', async () => {
    put.mockResolvedValueOnce({ data: null });
    const { user, onSalvo } = abrir();

    await user.click(within(screen.getAllByRole('listitem')[2]).getByRole('button', { name: 'Subir Bairro' }));
    expect(nomesNaTela()).toEqual(['Centro', 'Bairro', 'Shopping']);

    await user.click(within(screen.getAllByRole('listitem')[0]).getByRole('button', { name: 'Descer Centro' }));
    expect(nomesNaTela()).toEqual(['Bairro', 'Centro', 'Shopping']);

    await user.click(screen.getByRole('button', { name: 'Salvar ordem' }));

    await waitFor(() => expect(onSalvo).toHaveBeenCalled());
    expect(put).toHaveBeenCalledWith('/lojas/ordem', ['c', 'a', 'b']);
  });

  it('não deixa subir a primeira nem descer a última', () => {
    abrir();
    const botao = (nome: string) => screen.getByRole('button', { name: nome }) as HTMLButtonElement;
    expect(botao('Subir Centro').disabled).toBe(true);
    expect(botao('Descer Bairro').disabled).toBe(true);
    expect(botao('Descer Centro').disabled).toBe(false);
  });

  it('cancelar fecha sem salvar', async () => {
    const { user, onFechar } = abrir();
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(onFechar).toHaveBeenCalled();
    expect(put).not.toHaveBeenCalled();
  });
});
