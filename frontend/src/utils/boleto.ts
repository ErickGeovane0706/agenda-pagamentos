/**
 * Validação e tradução de códigos de boleto/arrecadação (padrão FEBRABAN).
 *
 * Por que isto existe: o leitor aceitava qualquer coisa com 44 a 48 dígitos,
 * sem conferir nenhum dígito verificador. Uma leitura torta era gravada em
 * silêncio, e não havia como o usuário perceber.
 *
 * As DUAS formas do mesmo boleto — a confusão que motivou este módulo:
 *
 *   - CÓDIGO DE BARRAS: 44 dígitos. É o que a câmera lê. Sempre 44.
 *   - LINHA DIGITÁVEL: 47 (bancário) ou 48 (arrecadação). É o que vem impresso
 *     e o que a pessoa digita no app do banco.
 *
 * Não são códigos diferentes: a linha digitável é o mesmo dado reagrupado em
 * blocos, com um DV a mais em cada bloco. Ler 44 quando o papel mostra 47 não
 * é dígito faltando — é a outra representação. Por isso `paraLinhaDigitavel`
 * existe: o que aparece na tela passa a bater com o papel.
 */

// ─── Dígitos verificadores ───────────────────────────────────────────────────

/** Módulo 10: DV de cada campo da linha digitável. Pesos 2 e 1 alternados. */
function modulo10(base: string): number {
  let soma = 0;
  let peso = 2;
  for (let i = base.length - 1; i >= 0; i--) {
    let produto = Number(base[i]) * peso;
    if (produto > 9) produto -= 9; // equivale a somar os dígitos do produto
    soma += produto;
    peso = peso === 2 ? 1 : 2;
  }
  return (10 - (soma % 10)) % 10;
}

/**
 * Módulo 11 do DV geral de boleto bancário (posição 5 do código de barras).
 * Pesos 2..9 ciclando da direita para a esquerda; resultado 0, 10 ou 11 vira 1.
 */
function modulo11Banco(base: string): number {
  let soma = 0;
  let peso = 2;
  for (let i = base.length - 1; i >= 0; i--) {
    soma += Number(base[i]) * peso;
    peso = peso === 9 ? 2 : peso + 1;
  }
  const dv = 11 - (soma % 11);
  return dv === 0 || dv > 9 ? 1 : dv;
}

/**
 * Módulo 11 de arrecadação. Difere do bancário no tratamento das bordas:
 * aqui resto que levaria a 10 ou 11 vira 0, não 1.
 */
function modulo11Arrecadacao(base: string): number {
  let soma = 0;
  let peso = 2;
  for (let i = base.length - 1; i >= 0; i--) {
    soma += Number(base[i]) * peso;
    peso = peso === 9 ? 2 : peso + 1;
  }
  const dv = 11 - (soma % 11);
  return dv > 9 ? 0 : dv;
}

/**
 * Arrecadação usa módulo 10 ou 11 conforme o "identificador de valor", que é a
 * 3ª posição do código. 6 e 7 → módulo 10; 8 e 9 → módulo 11. Errar isso faz
 * o validador reprovar guia legítima, que é o pior tipo de falso negativo.
 */
function dvArrecadacao(base: string, identificadorValor: string): number {
  return identificadorValor === '6' || identificadorValor === '7'
    ? modulo10(base)
    : modulo11Arrecadacao(base);
}

// ─── Vencimento ──────────────────────────────────────────────────────────────

const BASE_FATOR = Date.UTC(1997, 9, 7); // 07/10/1997, marco zero da FEBRABAN
const DIA_MS = 86400000;

/**
 * O "fator de vencimento" conta dias desde 07/10/1997 — e estourou em 9999 no
 * dia 21/02/2025, quando a FEBRABAN reiniciou a contagem em 1000. Ou seja,
 * todo fator hoje admite DUAS datas: a do ciclo antigo e a do novo (9000 dias
 * adiante). Calcular só pela base antiga devolve uma data ~24 anos no passado
 * — plausível o bastante para passar despercebida, que é o pior defeito
 * possível num campo de conferência.
 *
 * Desempate: fica a data mais próxima de hoje. Boleto em leitura é recente.
 */
function vencimentoDe(fator: string): Date | null {
  const dias = Number(fator);
  if (!dias) return null; // 0000 = sem vencimento definido

  const cicloAntigo = BASE_FATOR + dias * DIA_MS;
  const cicloNovo = BASE_FATOR + (dias + 9000) * DIA_MS;
  const hoje = Date.now();

  return new Date(
    Math.abs(cicloAntigo - hoje) <= Math.abs(cicloNovo - hoje) ? cicloAntigo : cicloNovo
  );
}

// ─── Bancos e segmentos ──────────────────────────────────────────────────────

const BANCOS: Record<string, string> = {
  '001': 'Banco do Brasil',
  '033': 'Santander',
  '041': 'Banrisul',
  '070': 'BRB',
  '077': 'Banco Inter',
  '104': 'Caixa Econômica Federal',
  '208': 'BTG Pactual',
  '212': 'Banco Original',
  '237': 'Bradesco',
  '260': 'Nubank',
  '336': 'Banco C6',
  '341': 'Itaú',
  '380': 'PicPay',
  '422': 'Banco Safra',
  '461': 'Asaas',
  '655': 'Votorantim',
  '745': 'Citibank',
  '748': 'Sicredi',
  '756': 'Sicoob',
};

/** Segmento da arrecadação: 2ª posição do código que começa com 8. */
const SEGMENTOS: Record<string, string> = {
  '1': 'Prefeitura',
  '2': 'Saneamento',
  '3': 'Energia elétrica ou gás',
  '4': 'Telecomunicações',
  '5': 'Órgão governamental',
  '6': 'Carnês e assemelhados',
  '7': 'Multas de trânsito',
};

// ─── API pública ─────────────────────────────────────────────────────────────

export interface DadosBoleto {
  /** Linha digitável, 47 ou 48 dígitos — é o que se guarda e o que bate com o papel. */
  linhaDigitavel: string;
  /** Código de barras, 44 dígitos — o que a câmera lê. */
  codigoBarras: string;
  /** "Boleto bancário" ou "Arrecadação / Convênio". */
  tipo: string;
  /** Nome do banco, ou o segmento no caso de arrecadação. Null se não mapeado. */
  emissor: string | null;
  valor: number | null;
  vencimento: Date | null;
}

export type ResultadoLeitura =
  | { valido: true; dados: DadosBoleto }
  | { valido: false; motivo: string };

const so = (codigo: string) => codigo.replace(/\D/g, '');

/**
 * Valida e traduz um código lido. Aceita as duas formas (44, 47 ou 48) e
 * devolve sempre as duas, para que a tela mostre a linha digitável — que é o
 * que o usuário consegue conferir contra o boleto na mão.
 *
 * Comprimento é EXATO, nunca um intervalo: a versão anterior aceitava 44 a 48,
 * então uma linha digitável de 47 que perdesse 3 dígitos virava um "código de
 * barras de 44" perfeitamente válido aos olhos do sistema.
 */
export function lerCodigo(entrada: string): ResultadoLeitura {
  const c = so(entrada);

  if (c.length === 44) return deCodigoBarras(c);
  if (c.length === 47) return deLinhaDigitavel47(c);
  if (c.length === 48) return deLinhaDigitavel48(c);

  return {
    valido: false,
    motivo: `Leitura incompleta: ${c.length} dígitos (esperado 44, 47 ou 48).`,
  };
}

// ─── Código de barras (44) ───────────────────────────────────────────────────

function deCodigoBarras(bc: string): ResultadoLeitura {
  return bc[0] === '8' ? arrecadacaoDeBarras(bc) : bancarioDeBarras(bc);
}

function bancarioDeBarras(bc: string): ResultadoLeitura {
  const dvInformado = Number(bc[4]);
  const semDv = bc.slice(0, 4) + bc.slice(5);

  if (modulo11Banco(semDv) !== dvInformado) {
    return { valido: false, motivo: 'Dígito verificador não confere — leitura incorreta.' };
  }

  const campoLivre = bc.slice(19);
  const c1 = bc.slice(0, 4) + campoLivre.slice(0, 5);
  const c2 = campoLivre.slice(5, 15);
  const c3 = campoLivre.slice(15, 25);

  const linha =
    c1 + modulo10(c1) + c2 + modulo10(c2) + c3 + modulo10(c3) + bc[4] + bc.slice(5, 19);

  return {
    valido: true,
    dados: {
      linhaDigitavel: linha,
      codigoBarras: bc,
      tipo: 'Boleto bancário',
      emissor: BANCOS[bc.slice(0, 3)] ?? null,
      valor: Number(bc.slice(9, 19)) / 100 || null,
      vencimento: vencimentoDe(bc.slice(5, 9)),
    },
  };
}

function arrecadacaoDeBarras(bc: string): ResultadoLeitura {
  const identificador = bc[2];
  const dvInformado = Number(bc[3]);
  const semDv = bc.slice(0, 3) + bc.slice(4);

  if (dvArrecadacao(semDv, identificador) !== dvInformado) {
    return { valido: false, motivo: 'Dígito verificador não confere — leitura incorreta.' };
  }

  // A linha digitável de arrecadação é o próprio código em 4 blocos de 11,
  // cada um com seu DV — daí 48.
  let linha = '';
  for (let i = 0; i < 4; i++) {
    const bloco = bc.slice(i * 11, i * 11 + 11);
    linha += bloco + dvArrecadacao(bloco, identificador);
  }

  // Valor só é confiável quando o identificador diz que ele está no código
  // (6 e 8 = valor efetivo; 7 e 9 = valor a ser conferido em outro canal).
  const temValor = identificador === '6' || identificador === '8';

  return {
    valido: true,
    dados: {
      linhaDigitavel: linha,
      codigoBarras: bc,
      tipo: 'Arrecadação / Convênio',
      emissor: SEGMENTOS[bc[1]] ?? null,
      valor: temValor ? Number(bc.slice(4, 15)) / 100 || null : null,
      vencimento: null, // arrecadação não carrega fator de vencimento
    },
  };
}

// ─── Linha digitável ─────────────────────────────────────────────────────────

function deLinhaDigitavel47(l: string): ResultadoLeitura {
  const campos: [string, string][] = [
    [l.slice(0, 9), l[9]],
    [l.slice(10, 20), l[20]],
    [l.slice(21, 31), l[31]],
  ];

  for (const [base, dv] of campos) {
    if (modulo10(base) !== Number(dv)) {
      return { valido: false, motivo: 'Dígito verificador não confere — leitura incorreta.' };
    }
  }

  // Remonta o código de barras e revalida pelo DV geral, que é o mais forte.
  const bc =
    l.slice(0, 4) + l[32] + l.slice(33, 47) + l.slice(4, 9) + l.slice(10, 20) + l.slice(21, 31);

  return deCodigoBarras(bc);
}

function deLinhaDigitavel48(l: string): ResultadoLeitura {
  const identificador = l[2];

  let bc = '';
  for (let i = 0; i < 4; i++) {
    const bloco = l.slice(i * 12, i * 12 + 11);
    const dv = l[i * 12 + 11];
    if (dvArrecadacao(bloco, identificador) !== Number(dv)) {
      return { valido: false, motivo: 'Dígito verificador não confere — leitura incorreta.' };
    }
    bc += bloco;
  }

  return deCodigoBarras(bc);
}

/** Formata a linha digitável com os separadores impressos no boleto. */
export function formatarLinha(linha: string): string {
  const l = so(linha);
  if (l.length === 47) {
    return `${l.slice(0, 5)}.${l.slice(5, 10)} ${l.slice(10, 15)}.${l.slice(15, 21)} `
      + `${l.slice(21, 26)}.${l.slice(26, 32)} ${l[32]} ${l.slice(33)}`;
  }
  if (l.length === 48) {
    return `${l.slice(0, 12)} ${l.slice(12, 24)} ${l.slice(24, 36)} ${l.slice(36)}`;
  }
  return l;
}
