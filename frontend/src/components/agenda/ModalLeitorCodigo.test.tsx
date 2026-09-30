import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import * as pdfjsLib from 'pdfjs-dist';
import { ModalLeitorCodigo } from './ModalLeitorCodigo';

// O contrato do pdf.js que o leitor usa (conferido contra o 4.8.69 com um PDF
// criptografado de verdade): sem senha ou com senha errada, a promise rejeita
// com name 'PasswordException' e code NEED_PASSWORD (1) / INCORRECT_PASSWORD (2).
vi.mock('pdfjs-dist', () => ({
  version: 'teste',
  GlobalWorkerOptions: {},
  PasswordResponses: { NEED_PASSWORD: 1, INCORRECT_PASSWORD: 2 },
  getDocument: vi.fn(),
}));
vi.mock('tesseract.js', () => ({ default: {} }));
vi.mock('@zxing/browser', () => ({ BrowserMultiFormatReader: vi.fn() }));

const getDocument = vi.mocked(pdfjsLib.getDocument);

function senhaExigida(code: 1 | 2) {
  const erro = Object.assign(new Error(code === 1 ? 'No password given' : 'Incorrect Password'),
    { name: 'PasswordException', code });
  // getter: a promise só nasce (rejeitada) quando o leitor a consome
  return { get promise() { return Promise.reject(erro); } } as unknown as ReturnType<typeof pdfjsLib.getDocument>;
}

/** PDF de 2 páginas: abre o seletor de páginas, sem cair no OCR. */
function pdfAberto() {
  const pagina = {
    getViewport: () => ({ width: 10, height: 10 }),
    render: () => ({ promise: Promise.resolve() }),
  };
  const pdf = { numPages: 2, getPage: async () => pagina };
  return { promise: Promise.resolve(pdf) } as unknown as ReturnType<typeof pdfjsLib.getDocument>;
}

async function enviarPdf() {
  render(<ModalLeitorCodigo aberto onFechar={() => {}} onCodigoLido={() => {}} />);
  const user = userEvent.setup();
  await user.click(screen.getByRole('button', { name: /PDF/ }));
  const arquivo = new File(['%PDF-1.7'], 'boleto.pdf', { type: 'application/pdf' });
  await user.upload(document.getElementById('file-input') as HTMLInputElement, arquivo);
  return user;
}

describe('ModalLeitorCodigo — PDF com senha', () => {
  beforeEach(() => getDocument.mockReset());

  it('pede a senha, avisa quando está errada e abre com a certa', async () => {
    getDocument
      .mockReturnValueOnce(senhaExigida(1))
      .mockReturnValueOnce(senhaExigida(2))
      .mockReturnValueOnce(pdfAberto());

    const user = await enviarPdf();

    const campo = await screen.findByPlaceholderText('Senha do PDF');
    expect(screen.getByText('Este PDF tem senha')).toBeTruthy();
    expect(screen.queryByText(/Senha incorreta/)).toBeNull();

    await user.type(campo, '999');
    await user.click(screen.getByRole('button', { name: 'Abrir PDF' }));
    await screen.findByText(/Senha incorreta/);
    expect(getDocument).toHaveBeenLastCalledWith(expect.objectContaining({ password: '999' }));

    await user.type(screen.getByPlaceholderText('Senha do PDF'), '12345');
    await user.click(screen.getByRole('button', { name: 'Abrir PDF' }));
    await screen.findByText('PDF com 2 páginas');
    expect(getDocument).toHaveBeenLastCalledWith(expect.objectContaining({ password: '12345' }));
    expect(screen.queryByPlaceholderText('Senha do PDF')).toBeNull();
  });

  it('PDF sem senha abre direto, sem perguntar nada', async () => {
    getDocument.mockReturnValueOnce(pdfAberto());

    await enviarPdf();

    await screen.findByText('PDF com 2 páginas');
    expect(screen.queryByPlaceholderText('Senha do PDF')).toBeNull();
  });

  it('cancelar volta para a escolha de arquivo', async () => {
    getDocument.mockReturnValueOnce(senhaExigida(1));

    const user = await enviarPdf();
    await screen.findByPlaceholderText('Senha do PDF');
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    await waitFor(() => expect(screen.getByText('Clique para selecionar um PDF')).toBeTruthy());
  });
});
