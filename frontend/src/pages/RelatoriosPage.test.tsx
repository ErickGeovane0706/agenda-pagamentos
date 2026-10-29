import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { AxiosRequestConfig } from 'axios';
import api from '../api/client';
import RelatoriosPage from './RelatoriosPage';

vi.mock('../api/client', () => ({ default: { get: vi.fn() } }));
vi.mock('../store/authStore', () => ({ useAuthStore: () => ({ usuario: { empresaId: 'e-1' } }) }));
// Os gráficos usam canvas, que o jsdom não tem; não são o que está sob teste aqui.
vi.mock('../components/GraficosRelatorio', () => ({ default: () => <div>graficos</div> }));

const get = vi.mocked(api.get);

const resumo = { total: 1, valor: 0 };
const relatorio = (porLoja?: unknown[]) => ({
  totalGeral: 1050, boletos: resumo, pix: resumo, cheques: resumo, porLoja,
});
const porLoja = [
  { lojaId: 'n', nome: 'Norte', valorPago: 0, valorAPagar: 800, valorEmAtraso: 300 },
  { lojaId: 'c', nome: 'Centro', valorPago: 1000, valorAPagar: 250, valorEmAtraso: 0 },
];

function renderizar() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const { container } = render(
    <QueryClientProvider client={client}><RelatoriosPage /></QueryClientProvider>,
  );
  const [de, ate] = container.querySelectorAll('input[type="date"]');
  fireEvent.change(de, { target: { value: '2026-10-01' } });
  fireEvent.change(ate, { target: { value: '2026-10-31' } });
}

describe('RelatoriosPage — por loja', () => {
  beforeEach(() => {
    get.mockReset();
    get.mockImplementation((url: string, config?: AxiosRequestConfig) => {
      if (url === '/lojas') return Promise.resolve({ data: [{ id: 'c', nome: 'Centro' }, { id: 'n', nome: 'Norte' }] });
      return Promise.resolve({ data: relatorio((config?.params as { lojaId?: string } | undefined)?.lojaId ? undefined : porLoja) });
    });
  });

  it('com "Todas as lojas" mostra a quebra por loja abaixo dos gráficos', async () => {
    renderizar();

    const titulo = await screen.findByText('Por loja');
    const graficos = screen.getByText('graficos');
    expect(graficos.compareDocumentPosition(titulo) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Todas as lojas/ })).toBeNull();
  });

  it('tocar na loja filtra o relatório por ela, e "Todas as lojas" volta', async () => {
    renderizar();

    await userEvent.click(await screen.findByRole('button', { name: /^Centro/ }));

    await waitFor(() => expect(get).toHaveBeenCalledWith('/relatorios',
      expect.objectContaining({ params: expect.objectContaining({ lojaId: 'c' }) })));
    expect(screen.queryByText('Por loja')).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: /Todas as lojas/ }));

    expect(await screen.findByText('Por loja')).toBeTruthy();
  });
});
