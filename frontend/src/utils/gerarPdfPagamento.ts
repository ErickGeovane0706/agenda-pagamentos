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

// ─── Boleto: linha digitável ↔ código de barras ────────────────────────────

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

/** Formata a linha digitável de boleto bancário com pontos/espaços no
 * padrão oficial (ex: "75691.43402 01253.630709 00049.250012 5 15000000104500"). */
function formatarLinhaDigitavelBoleto(d47: string): string {
  const c1 = d47.slice(0, 10);
  const c2 = d47.slice(10, 21);
  const c3 = d47.slice(21, 32);
  const c4 = d47.slice(32, 33);
  const c5 = d47.slice(33, 47);
  return `${c1.slice(0, 5)}.${c1.slice(5)} ${c2.slice(0, 5)}.${c2.slice(5)} ${c3.slice(0, 5)}.${c3.slice(5)} ${c4} ${c5}`;
}

/** Formata a linha digitável de convênio/guia com hífen por bloco
 * (ex: "85800000025-9 98500328261-5 77072026176-2 26894263681-5"). */
function formatarLinhaDigitavelConvenio(d48: string): string {
  return [d48.slice(0, 12), d48.slice(12, 24), d48.slice(24, 36), d48.slice(36, 48)]
    .map(bloco => `${bloco.slice(0, 11)}-${bloco.slice(11)}`)
    .join(' ');
}

/**
 * Formata o código salvo pro padrão oficial com separadores (pontos/espaços
 * ou hífens), do jeito que aparece impresso num boleto de verdade. Apps de
 * banco que fazem extração de texto (não OCR do código de barras) procuram
 * esse formato — por isso o texto real no PDF importa tanto quanto a imagem.
 */
function formatarCodigoParaExibir(codigo: string): string {
  const limpo = codigo.replace(/\D/g, '');
  if (limpo.length === 47 && !limpo.startsWith('8')) return formatarLinhaDigitavelBoleto(limpo);
  if (limpo.length === 48 && limpo.startsWith('8')) return formatarLinhaDigitavelConvenio(limpo);
  return limpo;
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

// ─── PIX: payload EMV ("Copia e Cola") ──────────────────────────────────────

/** Cidade do recebedor exigida pelo payload EMV. O app não coleta esse dado
 * (não existe campo de cidade em Loja/Empresa/PIX) — usamos um valor fixo,
 * já que o campo é só informativo no padrão e não é validado pelos bancos. */
const CIDADE_PIX_PADRAO = 'BRASIL';

/** Monta um campo TLV (Tag-Length-Value) do padrão EMV: 2 dígitos de tag +
 * 2 dígitos de tamanho (zero-padded) + o valor. */
function tlv(tag: string, valor: string): string {
  return `${tag}${valor.length.toString().padStart(2, '0')}${valor}`;
}

/** CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF) — o checksum exigido no
 * campo final (63) do payload EMV do Pix. */
function crc16(texto: string): string {
  let crc = 0xffff;
  for (let i = 0; i < texto.length; i++) {
    crc ^= texto.charCodeAt(i) << 8;
    for (let bit = 0; bit < 8; bit++) {
      crc = (crc & 0x8000) !== 0 ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff;
    }
  }
  return crc.toString(16).toUpperCase().padStart(4, '0');
}

/**
 * Monta o payload EMV completo do Pix ("BR Code" / Pix Copia e Cola), no
 * formato que os apps de banco reconhecem tanto por QR code quanto por
 * texto colado — sempre começa com "000201". Campos obrigatórios do padrão
 * que o app não coleta (cidade) usam um valor fixo; sem valor definido, o
 * campo de valor (54) é omitido (pagamento com valor livre).
 */
function montarPayloadPixEmv(chave: string, fornecedor: string | undefined, valor: number | undefined): string {
  const contaComerciante = tlv('00', 'br.gov.bcb.pix') + tlv('01', chave);
  const nomeRecebedor = (fornecedor || 'RECEBEDOR').toUpperCase().slice(0, 25);
  const campoValor = valor != null ? tlv('54', valor.toFixed(2)) : '';
  const dadosAdicionais = tlv('62', tlv('05', '***'));

  const semCrc =
    tlv('00', '01') +
    tlv('26', contaComerciante) +
    tlv('52', '0000') +
    tlv('53', '986') +
    campoValor +
    tlv('58', 'BR') +
    tlv('59', nomeRecebedor) +
    tlv('60', CIDADE_PIX_PADRAO) +
    dadosAdicionais +
    '6304';

  return semCrc + crc16(semCrc);
}

async function gerarImagemQrCode(valor: string): Promise<string | null> {
  try {
    return await QRCode.toDataURL(valor, { margin: 1, width: 300 });
  } catch {
    return null;
  }
}

// ─── PDF ────────────────────────────────────────────────────────────────────

/**
 * Gera um PDF com fornecedor/valor/vencimento e o código de pagamento em
 * destaque — pro boleto, a linha digitável formatada (padrão oficial, com
 * pontos/espaços); pro PIX, o payload EMV completo ("Copia e Cola"). Em
 * ambos os casos o código aparece como texto real e selecionável (não é
 * desenhado dentro da imagem), logo acima da imagem de apoio (código de
 * barras ITF ou QR code) — apps de banco que fazem extração de texto do PDF
 * procuram esse texto num formato reconhecível, não necessariamente leem a
 * imagem visualmente.
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
  y += 8;

  if (dados.tipo === 'boleto') {
    doc.setFontSize(10);
    doc.setFont('helvetica', 'normal');
    doc.text('Linha digitável:', margem, y);
    y += 7;

    doc.setFont('courier', 'normal');
    doc.setFontSize(12);
    doc.text(doc.splitTextToSize(formatarCodigoParaExibir(dados.codigo), 170), margem, y);
    y += 10;

    const barcode44 = resolverBarcode44(dados.codigo);
    const imagem = barcode44 ? gerarImagemBarcodeITF(barcode44) : null;
    if (imagem) {
      doc.addImage(imagem, 'PNG', margem, y, 170, 28);
      y += 34;
    }
  } else {
    const payloadEmv = montarPayloadPixEmv(dados.codigo, dados.fornecedor, dados.valor);

    doc.setFontSize(10);
    doc.setFont('helvetica', 'normal');
    doc.text('PIX Copia e Cola:', margem, y);
    y += 7;

    doc.setFont('courier', 'normal');
    doc.setFontSize(9);
    doc.text(doc.splitTextToSize(payloadEmv, 170), margem, y);
    y += 16;

    const imagem = await gerarImagemQrCode(payloadEmv);
    if (imagem) {
      doc.addImage(imagem, 'PNG', margem, y, 50, 50);
      y += 56;
    }
  }

  return doc.output('blob');
}
