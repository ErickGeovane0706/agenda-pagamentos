import { jsPDF } from 'jspdf';
import JsBarcode from 'jsbarcode';
import QRCode from 'qrcode';
import { format, parseISO } from 'date-fns';

export interface DadosPagamento {
  tipo: 'boleto' | 'pix';
  codigo: string;
  fornecedor?: string;
  valor?: number;
  vencimento?: string;
}

/**
 * Converte a linha digitável de boleto bancário padrão (47 dígitos, não
 * começa com 8) pro código de barras real (44 dígitos), reorganizando os
 * campos conforme o layout FEBRABAN. Os dígitos verificadores de cada campo
 * da linha digitável (posições 10, 21 e 32) só servem pra conferir que a
 * pessoa digitou certo — não entram no código de barras, por isso são
 * descartados aqui.
 */
function linhaDigitavelParaBarcode47(linha: string): string {
  const campo1 = linha.slice(0, 9);   // banco(3) + moeda(1) + campoLivre1(5)
  const campo2 = linha.slice(10, 20); // campoLivre2(10)
  const campo3 = linha.slice(21, 31); // campoLivre3(10)
  const dvGeral = linha[32];          // dígito verificador geral do barcode
  const campo5 = linha.slice(33, 47); // fator de vencimento(4) + valor(10)

  return campo1.slice(0, 4) + dvGeral + campo5 + campo1.slice(4) + campo2 + campo3;
}

/**
 * Converte a linha digitável de convênio/guia (48 dígitos, começa com 8) pro
 * código de barras (44 dígitos): são 4 blocos de 11 dígitos + 1 dígito
 * verificador cada — o barcode é só os 4 blocos de dados concatenados, sem
 * os verificadores.
 */
function convenioParaBarcode(linha48: string): string {
  return (
    linha48.slice(0, 11) +
    linha48.slice(12, 23) +
    linha48.slice(24, 35) +
    linha48.slice(36, 47)
  );
}

/**
 * Resolve o código de barras "puro" (44 dígitos) a partir do que está salvo,
 * que pode ser o próprio código de barras, a linha digitável de boleto
 * bancário ou a de convênio/guia. Retorna null quando não dá pra determinar
 * com segurança — nesse caso o PDF mostra só o texto, sem imagem.
 */
function resolverBarcode44(codigo: string): string | null {
  const limpo = codigo.replace(/\D/g, '');
  if (limpo.length === 44) return limpo;
  if (limpo.length === 47 && !limpo.startsWith('8')) return linhaDigitavelParaBarcode47(limpo);
  if (limpo.length === 48 && limpo.startsWith('8')) return convenioParaBarcode(limpo);
  return null;
}

function gerarImagemBarcodeITF(codigo44: string): string | null {
  try {
    const canvas = document.createElement('canvas');
    JsBarcode(canvas, codigo44, {
      format: 'ITF',
      width: 2.2,
      height: 70,
      displayValue: false,
      margin: 0,
    });
    return canvas.toDataURL('image/png');
  } catch {
    return null;
  }
}

/**
 * QR code com o valor bruto da chave PIX. Atenção: isso NÃO é um "BR Code"
 * (o payload EMV oficial do Banco Central, com merchant/valor/CRC) — é só a
 * chave em texto dentro do QR. Serve como redundância visual e pra apps que
 * aceitam ler a chave direto; bancos que exigem o BR Code completo pra pagar
 * por QR podem não reconhecer.
 */
async function gerarImagemQrCode(valor: string): Promise<string | null> {
  try {
    return await QRCode.toDataURL(valor, { margin: 1, width: 300 });
  } catch {
    return null;
  }
}

/**
 * Gera um PDF simples com fornecedor/valor/vencimento e o código de
 * pagamento em destaque, com imagem (código de barras ITF pro boleto, QR
 * code pro PIX) além do texto — usado pra compartilhar com apps de banco,
 * que costumam ler o código visualmente (câmera), não extrair texto do PDF.
 */
export async function gerarPdfPagamento(dados: DadosPagamento): Promise<Blob> {
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

  if (dados.tipo === 'boleto') {
    const barcode44 = resolverBarcode44(dados.codigo);
    const imagem = barcode44 ? gerarImagemBarcodeITF(barcode44) : null;
    if (imagem) {
      doc.addImage(imagem, 'PNG', margem, y, 170, 28);
      y += 34;
    }
  } else {
    const imagem = await gerarImagemQrCode(dados.codigo);
    if (imagem) {
      doc.addImage(imagem, 'PNG', margem, y, 50, 50);
      y += 56;
    }
  }

  doc.setFontSize(10);
  doc.text(dados.tipo === 'boleto' ? 'Linha digitável:' : 'Chave PIX:', margem, y);
  y += 7;
  doc.setFont('courier', 'normal');
  doc.setFontSize(13);
  doc.text(doc.splitTextToSize(dados.codigo, 170), margem, y);

  return doc.output('blob');
}
