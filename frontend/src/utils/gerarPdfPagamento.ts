import { jsPDF } from 'jspdf';
import JsBarcode from 'jsbarcode';
import { format, parseISO } from 'date-fns';

export interface DadosPagamento {
  codigo: string;
  fornecedor?: string;
  valor?: number;
  vencimento?: string;
}

/**
 * Gera a imagem (PNG data URL) do código de barras via ITF. Só é chamada
 * quando o código tem exatamente 44 dígitos — a linha digitável (47/48
 * dígitos) tem dígitos verificadores extras por campo e não é o payload
 * real do barcode, então não dá pra renderizar como imagem válida.
 */
function gerarImagemCodigoBarras(codigo44: string): string | null {
  try {
    const canvas = document.createElement('canvas');
    JsBarcode(canvas, codigo44, {
      format: 'ITF',
      width: 2,
      height: 60,
      displayValue: false,
      margin: 0,
    });
    return canvas.toDataURL('image/png');
  } catch {
    return null;
  }
}

/**
 * Gera um PDF simples com fornecedor/valor/vencimento e o código de
 * pagamento (código de barras ou chave PIX) em destaque — usado pra
 * compartilhar com apps de banco, que costumam só listar arquivos como
 * alvo de compartilhamento (não texto puro) no menu "Abrir com".
 */
export function gerarPdfPagamento(dados: DadosPagamento): Blob {
  const doc = new jsPDF({ unit: 'mm', format: 'a4' });
  const margem = 20;
  let y = 30;

  doc.setFontSize(18);
  doc.text('Pagamento', margem, y);
  y += 14;

  doc.setFontSize(12);
  if (dados.fornecedor) {
    doc.text(`Fornecedor: ${dados.fornecedor}`, margem, y);
    y += 8;
  }
  if (dados.valor != null) {
    const valorFormatado = new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    }).format(dados.valor);
    doc.text(`Valor: ${valorFormatado}`, margem, y);
    y += 8;
  }
  if (dados.vencimento) {
    doc.text(`Vencimento: ${format(parseISO(dados.vencimento), 'dd/MM/yyyy')}`, margem, y);
    y += 8;
  }
  y += 6;

  const codigoLimpo = dados.codigo.replace(/\D/g, '');
  if (codigoLimpo.length === 44) {
    const imagem = gerarImagemCodigoBarras(codigoLimpo);
    if (imagem) {
      doc.addImage(imagem, 'PNG', margem, y, 170, 25);
      y += 32;
    }
  }

  doc.setFontSize(10);
  doc.text('Código:', margem, y);
  y += 7;
  doc.setFont('courier', 'normal');
  doc.setFontSize(13);
  doc.text(doc.splitTextToSize(dados.codigo, 170), margem, y);

  return doc.output('blob');
}
