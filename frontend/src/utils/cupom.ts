import { Venda } from '../types';

const moeda = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v);

const quantidade = (q: number) =>
  Number.isInteger(q) ? String(q) : q.toLocaleString('pt-BR');

const escapar = (texto: string) =>
  texto.replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]!
  ));

/**
 * Monta e manda imprimir o cupom de uma venda.
 *
 * ## Por que iframe, e não `window.open`
 *
 * Abrir uma janela nova é o caminho óbvio e o errado aqui: o Chrome do Android
 * bloqueia popup com frequência, e o usuário final deste sistema opera no
 * celular. Um iframe fora da tela não passa por bloqueador nenhum e imprime
 * igual.
 *
 * ## Por que HTML próprio, e não `@media print` na página
 *
 * Esconder o app inteiro com `visibility: hidden` e revelar só o cupom briga
 * com o Tailwind e com o layout a cada mudança de tela. Um documento separado,
 * com o seu próprio CSS de 12 linhas, não briga com nada.
 *
 * ## O aviso fiscal não é decoração
 *
 * O cupom leva "SEM VALOR FISCAL" em destaque. Um papel com itens, valores e
 * nome de loja se parece o suficiente com uma nota fiscal para ser confundido
 * com uma — e emitir documento que se passa por fiscal sem ser é problema do
 * dono da loja, não do sistema. O aviso é o que separa comprovante de venda de
 * documento fiscal.
 *
 * ## Largura
 *
 * 80mm é a bobina de impressora térmica mais comum. Em impressora comum ou no
 * "Salvar como PDF" o cupom sai como uma tira estreita no meio da folha, que é
 * o comportamento esperado.
 */
export function imprimirCupom(venda: Venda, lojaNome: string) {
  const linhas = venda.itens.map((i) => `
    <tr>
      <td colspan="2" class="nome">${escapar(i.produtoNome)}</td>
    </tr>
    <tr>
      <td class="det">${quantidade(i.quantidade)} x ${moeda(i.precoVenda)}</td>
      <td class="val">${moeda(i.quantidade * i.precoVenda)}</td>
    </tr>`).join('');

  const html = `<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><title>Cupom</title><style>
  @page { size: 80mm auto; margin: 3mm; }
  * { box-sizing: border-box; }
  body { width: 74mm; margin: 0; font: 12px/1.45 "Courier New", monospace; color: #000; }
  h1 { font-size: 14px; margin: 0 0 2px; text-align: center; text-transform: uppercase; }
  .sub { text-align: center; font-size: 11px; margin-bottom: 6px; }
  hr { border: 0; border-top: 1px dashed #000; margin: 6px 0; }
  table { width: 100%; border-collapse: collapse; }
  .nome { padding-top: 3px; }
  .det { font-size: 11px; }
  .val { text-align: right; white-space: nowrap; }
  .total { font-size: 15px; font-weight: bold; }
  .aviso { text-align: center; font-size: 11px; font-weight: bold; margin-top: 8px;
           border: 1px solid #000; padding: 3px; }
  .rodape { text-align: center; font-size: 10px; margin-top: 6px; }
</style></head><body>
  <h1>${escapar(lojaNome)}</h1>
  <div class="sub">Comprovante de venda</div>
  <hr>
  <table>
    <tr><td>Data</td><td class="val">${new Date(venda.vendidoEm).toLocaleString('pt-BR')}</td></tr>
    ${venda.clienteNome ? `<tr><td>Cliente</td><td class="val">${escapar(venda.clienteNome)}</td></tr>` : ''}
  </table>
  <hr>
  <table>${linhas}</table>
  <hr>
  <table>
    <tr class="total"><td>TOTAL</td><td class="val">${moeda(venda.total)}</td></tr>
  </table>
  <div class="aviso">SEM VALOR FISCAL</div>
  <div class="rodape">Obrigado pela preferência!</div>
</body></html>`;

  const iframe = document.createElement('iframe');
  // Fora da tela, e não `display: none`: parte dos navegadores não imprime o
  // conteúdo de um iframe que não tem caixa de layout.
  iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0';
  document.body.appendChild(iframe);

  const doc = iframe.contentWindow?.document;
  if (!doc) { document.body.removeChild(iframe); return; }

  doc.open();
  doc.write(html);
  doc.close();

  // O print precisa esperar o layout do documento novo; sem o onload, o
  // Safari imprime folha em branco.
  iframe.onload = () => {
    iframe.contentWindow?.focus();
    iframe.contentWindow?.print();
    // A remoção é tardia de propósito: tirar o iframe antes de o diálogo de
    // impressão fechar cancela o trabalho no Firefox.
    setTimeout(() => iframe.remove(), 60_000);
  };
}
