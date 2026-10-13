import { afterEach, describe, expect, it } from 'vitest';
import { criarRastreadorMeta } from './meta';
import { criarRastreadorTikTok } from './tiktok';

afterEach(() => {
  delete window.fbq;
  delete window._fbq;
  delete window.ttq;
  delete window.TiktokAnalyticsObject;
  document.head.innerHTML = '';
});

function scripts(): string[] {
  return Array.from(document.querySelectorAll('script')).map((s) => s.src);
}

describe('Meta', () => {
  it('injeta o fbevents.js, inicia o pixel e registra a visita', () => {
    criarRastreadorMeta('123').carregar();

    expect(scripts()).toContain('https://connect.facebook.net/en_US/fbevents.js');
    expect(window.fbq!.queue).toEqual([['init', '123'], ['track', 'PageView']]);
  });

  it('traduz os eventos do funil para os nomes da Meta', () => {
    const meta = criarRastreadorMeta('123');
    meta.carregar();

    meta.registrar('cadastroEnviado');
    meta.registrar('cadastroConfirmado');

    expect(window.fbq!.queue.slice(2)).toEqual([['track', 'Lead'], ['track', 'CompleteRegistration']]);
  });
});

describe('TikTok', () => {
  it('injeta o events.js com o id do pixel e registra a visita', () => {
    criarRastreadorTikTok('ABC').carregar();

    expect(scripts()).toContain('https://analytics.tiktok.com/i18n/pixel/events.js?sdkid=ABC&lib=ttq');
    expect(window.TiktokAnalyticsObject).toBe('ttq');
    expect(Array.from(window.ttq!)).toEqual([['page']]);
  });

  it('traduz os eventos do funil para os nomes do TikTok, que não tem Lead', () => {
    const tiktok = criarRastreadorTikTok('ABC');
    tiktok.carregar();

    tiktok.registrar('cadastroEnviado');
    tiktok.registrar('cadastroConfirmado');

    expect(Array.from(window.ttq!).slice(1)).toEqual([['track', 'SubmitForm'], ['track', 'CompleteRegistration']]);
  });
});
