import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AxiosError, AxiosHeaders } from 'axios';
import api from '../../api/client';
import { ModalBoleto } from './ModalBoleto';

vi.mock('../../api/client', () => ({ default: { post: vi.fn(), put: vi.fn() } }));
// O leitor carrega pdf.js/tesseract/zxing — irrelevante aqui.
vi.mock('./ModalLeitorCodigo', () => ({ ModalLeitorCodigo: () => null }));

const post = vi.mocked(api.post);
const AVISO = 'Este boleto já foi cadastrado em 12/09/2026 na loja Centro (pendente, vence em 05/10/2026).';
const LINHA = '00190.50095 40144.816069 06809.350314 3 37370000000100';

/** O 409 que o backend manda: status + ErrorResponse com `mensagem`. */
function conflito409() {
  const config = { headers: new AxiosHeaders() };
  return new AxiosError('Conflict', 'ERR_BAD_REQUEST', config, null, {
    status: 409, statusText: 'Conflict', headers: {}, config,
    data: { status: 409, mensagem: AVISO },
  });
}

async function preencherECriar(onSalvo: () => void) {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <ModalBoleto boleto={null} lojaId="loja-1" onFechar={() => {}} onSalvo={onSalvo} />
    </QueryClientProvider>,
  );
  const user = userEvent.setup();
  await user.type(screen.getByPlaceholderText('Nome do fornecedor'), 'Fornecedor X');
  await user.type(screen.getByPlaceholderText('0,00'), '100');
  const vencimento = document.querySelector('input[type="date"]') as HTMLInputElement;
  await user.type(vencimento, '2026-10-05');
  await user.type(screen.getByPlaceholderText('Código de barras do boleto'), LINHA);
  await user.click(screen.getByRole('button', { name: 'Criar boleto' }));
}

describe('ModalBoleto — boleto duplicado', () => {
  beforeEach(() => {
    post.mockReset();
    vi.restoreAllMocks();
  });

  it('mostra o aviso do servidor e, se a pessoa confirma, reenvia com confirmarDuplicado', async () => {
    post.mockRejectedValueOnce(conflito409()).mockResolvedValueOnce({ data: {} });
    const confirmar = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const onSalvo = vi.fn();

    await preencherECriar(onSalvo);

    await waitFor(() => expect(onSalvo).toHaveBeenCalled());
    expect(confirmar).toHaveBeenCalledWith(`${AVISO}\n\nCadastrar mesmo assim?`);
    expect(post).toHaveBeenCalledTimes(2);
    expect(post.mock.calls[0][1]).toMatchObject({ codigoBarras: LINHA, confirmarDuplicado: undefined });
    expect(post.mock.calls[1][1]).toMatchObject({ codigoBarras: LINHA, confirmarDuplicado: true });
  });

  it('se a pessoa cancela, não salva nada', async () => {
    post.mockRejectedValueOnce(conflito409());
    const confirmar = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const onSalvo = vi.fn();

    await preencherECriar(onSalvo);

    await waitFor(() => expect(confirmar).toHaveBeenCalled());
    expect(post).toHaveBeenCalledTimes(1);
    expect(onSalvo).not.toHaveBeenCalled();
  });

  it('outro erro não vira pergunta de duplicata', async () => {
    const config = { headers: new AxiosHeaders() };
    post.mockRejectedValueOnce(new AxiosError('x', 'ERR', config, null,
      { status: 500, statusText: '', headers: {}, config, data: {} }));
    const confirmar = vi.spyOn(window, 'confirm');

    await preencherECriar(vi.fn());

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(confirmar).not.toHaveBeenCalled();
  });
});
