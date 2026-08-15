import { useEffect, useState } from 'react';

/**
 * A MESMA media query do breakpoint `md` do Tailwind. Consultar o navegador em
 * vez de comparar `window.innerWidth < 768` é o ponto: quem responde aqui é o
 * mesmo motor que aplica os `md:` das classes, então o JS não tem como divergir
 * do CSS.
 *
 * Com innerWidth divergia, e o leitor de código de barras era o gatilho: ele
 * deita a tela de propósito (a lente precisa do lado longo do sensor), a largura
 * passa de 768px e isMobile vira false — correto até aí. Ao endireitar o
 * celular, o único `resize` que devolveria isMobile=true chega com as dimensões
 * da orientação ANTERIOR no WKWebView, que é onde roda o PWA instalado no
 * iPhone (WebKit #170595, aberto desde 2017 e sem correção). Como `resize` era
 * o único gatilho e o aparelho não gira de novo, o estado errado nunca mais se
 * corrigia: as tabelas ficavam em layout de desktop até recarregar a página.
 */
const DESKTOP = '(min-width: 768px)';

export function useIsMobile() {
  const [isMobile, setIsMobile] = useState(() => !window.matchMedia(DESKTOP).matches);
  useEffect(() => {
    const mql = window.matchMedia(DESKTOP);
    const handler = (e: MediaQueryListEvent) => setIsMobile(!e.matches);
    // Resincroniza: entre o primeiro render e este efeito a tela pode ter virado.
    setIsMobile(!mql.matches);
    mql.addEventListener('change', handler);
    return () => mql.removeEventListener('change', handler);
  }, []);
  return isMobile;
}
