import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import api from '../../api/client';
import { ModalAnexos } from './ModalAnexos';

vi.mock('../../api/client', () => ({ default: { get: vi.fn() } }));

const get = vi.mocked(api.get);

function renderizar() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <ModalAnexos recurso="boletos" lancamentoId="b-1" titulo="Fornecedor" onFechar={() => {}} onAlterado={() => {}} />
    </QueryClientProvider>,
  );
}

describe('ModalAnexos', () => {
  beforeEach(() => { get.mockReset(); vi.restoreAllMocks(); });

  // O Safari do iPhone só deixa abrir aba no próprio toque: um window.open
  // depois do await da URL é bloqueado sem aviso, e o arquivo nunca aparece.
  it('abre a aba no toque, antes de a URL chegar, e só depois aponta para o arquivo', async () => {
    let entregarUrl: (v: unknown) => void = () => {};
    get.mockImplementation((url?: string) => url?.endsWith('/url')
      ? new Promise(r => { entregarUrl = r; })
      : Promise.resolve({ data: [{ id: 'a-1', nomeArquivo: 'foto.jpg', criadoEm: '2026-10-28' }] }));
    const aba = { location: { href: '' }, close: vi.fn() };
    const open = vi.spyOn(window, 'open').mockReturnValue(aba as unknown as Window);
    renderizar();

    fireEvent.click(await screen.findByText('foto.jpg'));
    expect(open).toHaveBeenCalledTimes(1);

    entregarUrl({ data: { url: 'https://r2.example/foto.jpg' } });
    await waitFor(() => expect(aba.location.href).toBe('https://r2.example/foto.jpg'));
  });
});
