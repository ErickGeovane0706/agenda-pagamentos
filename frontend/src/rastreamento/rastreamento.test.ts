import { describe, expect, it, vi } from 'vitest';
import { criarRastreamento } from './index';
import type { Rastreador } from './tipos';

function rastreadorFalso(): Rastreador & { carregar: ReturnType<typeof vi.fn>; registrar: ReturnType<typeof vi.fn> } {
  return { carregar: vi.fn(), registrar: vi.fn() };
}

describe('criarRastreamento', () => {
  it('carrega todas as plataformas quando a URL está limpa', () => {
    const meta = rastreadorFalso();
    const tiktok = rastreadorFalso();

    criarRastreamento([meta, tiktok], () => '/comecar').iniciar();

    expect(meta.carregar).toHaveBeenCalledOnce();
    expect(tiktok.carregar).toHaveBeenCalledOnce();
  });

  it('não carrega nada enquanto a URL tem token', () => {
    const plataforma = rastreadorFalso();

    criarRastreamento([plataforma], () => '/registro/confirmar?token=abc').iniciar();

    expect(plataforma.carregar).not.toHaveBeenCalled();
  });

  it('descarta evento disparado enquanto a URL tem token', () => {
    const plataforma = rastreadorFalso();
    const rastreamento = criarRastreamento([plataforma], () => '/redefinir-senha?token=abc');

    rastreamento.rastrear('cadastroConfirmado');

    expect(plataforma.carregar).not.toHaveBeenCalled();
    expect(plataforma.registrar).not.toHaveBeenCalled();
  });

  it('carrega na hora do primeiro evento quem chegou por um link com token', () => {
    let url = '/registro/confirmar?token=abc';
    const plataforma = rastreadorFalso();
    const rastreamento = criarRastreamento([plataforma], () => url);

    rastreamento.iniciar();
    url = '/bem-vindo';
    rastreamento.rastrear('cadastroConfirmado');

    expect(plataforma.carregar).toHaveBeenCalledOnce();
    expect(plataforma.registrar).toHaveBeenCalledWith('cadastroConfirmado');
    expect(plataforma.carregar.mock.invocationCallOrder[0])
      .toBeLessThan(plataforma.registrar.mock.invocationCallOrder[0]);
  });

  it('carrega uma vez só e repassa cada evento a todas as plataformas', () => {
    const meta = rastreadorFalso();
    const tiktok = rastreadorFalso();
    const rastreamento = criarRastreamento([meta, tiktok], () => '/registro');

    rastreamento.iniciar();
    rastreamento.rastrear('cadastroEnviado');
    rastreamento.rastrear('cadastroConfirmado');

    expect(meta.carregar).toHaveBeenCalledOnce();
    expect(tiktok.carregar).toHaveBeenCalledOnce();
    expect(meta.registrar.mock.calls).toEqual([['cadastroEnviado'], ['cadastroConfirmado']]);
    expect(tiktok.registrar.mock.calls).toEqual([['cadastroEnviado'], ['cadastroConfirmado']]);
  });
});
