import { useState, useRef, useEffect, useCallback } from 'react';
import * as pdfjsLib from 'pdfjs-dist';
import Tesseract from 'tesseract.js';
import { BrowserMultiFormatReader, IScannerControls } from '@zxing/browser';
import { BarcodeFormat, DecodeHintType } from '@zxing/library';
import { X, Camera, Image, FileText, Loader2, RotateCw, Check } from 'lucide-react';
import { lerCodigo, formatarLinha, DadosBoleto } from '../../utils/boleto';
import { clsx } from 'clsx';
import { useToastStore } from '../../store/toastStore';

/**
 * "É um aparelho de mão, com câmera traseira?" — pergunta diferente da do
 * useIsMobile ("a tela é estreita?"), e usar aquele aqui era um bug: o leitor
 * deita a tela, a largura do celular passa dos 768px, o app concluía que virou
 * um desktop, trocava a aba de câmera para imagem e matava a câmera no meio da
 * leitura. Ponteiro grosso não muda quando o aparelho gira.
 */
const APARELHO_DE_TOQUE = window.matchMedia('(pointer: coarse)').matches;

// Worker do PDF.js via CDN — evita o erro de import dinâmico de .mjs bloqueado
// pelo Brave, Opera e Safari. O jsDelivr já está liberado no CSP (worker-src).
pdfjsLib.GlobalWorkerOptions.workerSrc =
    `https://cdn.jsdelivr.net/npm/pdfjs-dist@${pdfjsLib.version}/build/pdf.worker.min.mjs`;

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Extrai o código digitável/numérico de um texto OCR.
 *
 * Formatos suportados (todos exceto FGTS e EAN de produtos):
 *
 * [A] BOLETO BANCÁRIO (47 dígitos)
 *     Código digitável: 99999.99999 99999.999999 99999.999999 9 99999999999999
 *     Linha digitável com pontos e espaços — 3 campos + dígito + valor/venc.
 *
 * [B] CONCESSIONÁRIAS / CONVÊNIO (48 dígitos, começa com 8)
 *     Produto 6 — água, luz, gás, telefone, TV a cabo, multas, etc.
 *     Código digitável: 99999999999-9 99999999999-9 99999999999-9 99999999999-9
 *     Ou 4 grupos separados por espaço com DV avulso:
 *       "85800000025 9  98500328261 5  77072026176 2  26894263681 5"
 *
 * [C] DARF (Documento de Arrecadação de Receitas Federais) — 48 dígitos, começa com 8
 *     Segmento 586x — Receita Federal (IRPF, IRPJ, PIS, COFINS, CSLL, IOF, IPI...)
 *
 * [D] GPS (Guia da Previdência Social / INSS) — 48 dígitos, começa com 8
 *     Segmento 580x / 582x
 *
 * [E] DAS (Simples Nacional / MEI) — 48 dígitos, começa com 858
 *     4 grupos: 85800000025 9 · 98500328261 5 · 77072026176 2 · 26894263681 5
 *
 * [F] GNRE (Guia Nacional de Recolhimento Estadual) — 48 dígitos, começa com 8
 *     Segmento 858x — ICMS entre estados, ST, etc.
 *
 * [G] DAR / DARE (Documento de Arrecadação Estadual/Municipal) — 48 dígitos
 *     Formato varia por estado; começa com 8 ou segue padrão bancário
 *
 * [H] GRU (Guia de Recolhimento da União) — 48 dígitos, começa com 8
 *     Segmento 858x ou 826x — taxas federais, passaportes, universidades públicas
 *
 * [I] IPTU / IPVA / Licenciamento — emitidos por prefeituras/Detran
 *     Podem usar padrão bancário (47 dígitos) ou convênio (48 dígitos)
 *
 * [J] Código de barras numérico puro — 44 dígitos (lido pela câmera diretamente)
 *
 * EXCLUÍDOS intencionalmente:
 *   - FGTS: migrou para PIX (chave CNPJ 00.360.305/0001-04)
 *   - EAN-8 / EAN-13 / UPC: códigos de produto — rejeitados pela validação de tamanho
 */
function extrairCodigoDigitavel(texto: string): string | null {
  // Normaliza: quebras de linha viram espaço, múltiplos espaços colapsam
  const t = texto.replace(/\r?\n/g, ' ').replace(/\s{2,}/g, ' ');

  // ── [A] Boleto bancário — 3 campos com pontos + dígito + 14 dígitos ──────
  // Ex: "34191.75400 71630.330003 00846.390007 9 99890000025000"
  const mBoleto = t.match(
      /\d{4,5}\.\d{4,6}[\s]+\d{4,5}\.\d{5,6}[\s]+\d{4,5}\.\d{5,6}[\s]+\d[\s]+\d{14}/
  );
  if (mBoleto) return mBoleto[0].replace(/[\s.]/g, '');

  // ── [B] Convênio/concessionária com hífen — 4 grupos NNNNNNNNNNN-D ───────
  // Ex: "83600000001-7 63300422008-5 93401190026-7 82259000000-6"
  // Separadores entre grupos podem ser espaço, ponto, · ou •
  const mHifen = t.match(
      /\d{8,12}-\d[\s·•.]+\d{8,12}-\d[\s·•.]+\d{8,12}-\d[\s·•.]+\d{8,12}-\d/
  );
  if (mHifen) return mHifen[0].replace(/[\s\-·•.]/g, '');

  // ── [C–H] DAS / GPS / DARF / GNRE / GRU / DAR — 4 grupos com DV avulso ──
  // Ex DAS: "85800000025 9  98500328261 5  77072026176 2  26894263681 5"
  // Ex GPS: "85800000001 0  00003900202 1  60127062026 3  10003000000 0"
  // Cada grupo: 8–12 dígitos, espaço(s)/separador, 1 dígito verificador
  const SEP = /[\s·•,]+/;
  const GRP = /(\d{8,12})/;
  const DV  = /(\d)/;
  const patGrupos = new RegExp(
      GRP.source + SEP.source + DV.source + SEP.source +
      GRP.source + SEP.source + DV.source + SEP.source +
      GRP.source + SEP.source + DV.source + SEP.source +
      GRP.source + SEP.source + DV.source
  );
  const mGrupos = t.match(patGrupos);
  if (mGrupos) {
    return mGrupos[1] + mGrupos[2] +
        mGrupos[3] + mGrupos[4] +
        mGrupos[5] + mGrupos[6] +
        mGrupos[7] + mGrupos[8];
  }

  // ── Fallback 1: remove separadores comuns e busca bloco de 44–48 dígitos ──
  // Cobre casos onde o OCR lê sem espaços ou com separadores incomuns
  const semSep = t.replace(/[\s.\-·•,/]/g, '');
  const mBloco = semSep.match(/\d{44,48}/);
  if (mBloco) return mBloco[0];

  // ── Fallback 2: extrai TODOS os dígitos do texto original ────────────────
  // Último recurso: OCR inseriu espaços no meio dos números
  const soDig = texto.replace(/\D/g, '');
  const mTudo = soDig.match(/\d{44,48}/);
  if (mTudo) return mTudo[0];

  return null;
}

// ─── Tesseract v7 ────────────────────────────────────────────────────────────
// Problema: workerBlobURL:false exige CORS no CDN (jsDelivr não retorna
//   Access-Control-Allow-Origin para Worker), bloqueado pelo browser.
//   workerBlobURL:true (padrão) cria blob worker mas no celular o blob worker
//   não consegue fazer fetch externo para carregar o script real.
//
// Solução: baixar o script do worker via fetch (que passa pelo connect-src),
//   criar um Blob com o conteúdo e passar a blob URL para o Tesseract.
//   Assim o worker roda como blob: (sem CORS), mas o script veio do CDN.
const TESSERACT_WORKER_URL = 'https://cdn.jsdelivr.net/npm/tesseract.js@7.0.0/dist/worker.min.js';
const TESSERACT_LANG_PATH  = 'https://cdn.jsdelivr.net/npm/@tesseract.js-data/por/4.0.0_best_int';

let _workerBlobUrl: string | null = null;

async function getWorkerBlobUrl(): Promise<string> {
  if (_workerBlobUrl) return _workerBlobUrl;
  const res = await fetch(TESSERACT_WORKER_URL);
  if (!res.ok) throw new Error(`Falha ao baixar worker Tesseract: ${res.status}`);
  const blob = new Blob([await res.text()], { type: 'application/javascript' });
  _workerBlobUrl = URL.createObjectURL(blob);
  return _workerBlobUrl;
}

async function criarWorkerTesseract() {
  const workerPath = await getWorkerBlobUrl();
  const worker = await Tesseract.createWorker('por', 1, {
    workerBlobURL: false, // já passamos blob URL manualmente
    workerPath,
    langPath: TESSERACT_LANG_PATH,
    logger: () => {},
  });
  return worker;
}

// ─── Tipos ────────────────────────────────────────────────────────────────────

type Aba = 'camera' | 'imagem' | 'pdf';

// ─── Componente ───────────────────────────────────────────────────────────────

export function ModalLeitorCodigo({
                                    aberto,
                                    onFechar,
                                    onCodigoLido,
                                  }: {
  aberto: boolean;
  onFechar: () => void;
  onCodigoLido: (codigo: string) => void;
}) {
  const addToast = useToastStore((s) => s.addToast);
  const [aba, setAba] = useState<Aba>('imagem');
  const [lendo, setLendo] = useState(false);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [paginasPDF, setPaginasPDF] = useState<string[]>([]);
  const [pdfRef, setPdfRef] = useState<pdfjsLib.PDFDocumentProxy | null>(null);
  const [paginaCarregando, setPaginaCarregando] = useState<number | null>(null);
  const [debugLogs, setDebugLogs] = useState<string[]>([]);
  const [cameraStarted, setCameraStarted] = useState(false);
  // Só vira true quando o ZXing já está decodificando — distinto de
  // cameraStarted, que sobe antes, assim que a tela cheia aparece.
  const [escaneando, setEscaneando] = useState(false);
  // Código lido pela câmera, segurando o painel de sucesso até o celular voltar
  // a ficar em pé — ver handleCodigoCamera.
  const [codigoLido, setCodigoLido] = useState<string | null>(null);

  /** Leitura que passou no dígito verificador e aguarda o OK do usuário. */
  const [confirmacao, setConfirmacao] = useState<
    { dados: DadosBoleto; origem: 'camera' | 'outro' } | null
  >(null);
  // Safari/iPhone não implementa screen.orientation.lock() — quando o lock
  // falha, o giro passa a ser manual (o usuário vira o aparelho e a interface
  // acompanha pelo botão de girar).
  const [giroManual, setGiroManual] = useState(false);
  const [girado, setGirado] = useState(false);
  const [paisagem, setPaisagem] = useState(false);

  const resultadoRef = useRef<string | null>(null);
  const scannerRef = useRef<HTMLDivElement>(null);
  // Alvo do fullscreen. Precisa envolver o vídeo E o overlay: em fullscreen o
  // navegador só renderiza o elemento e seus descendentes — pedir fullscreen só
  // no container do vídeo faria a moldura e o botão de fechar sumirem.
  const wrapperRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const scannerControlsRef = useRef<IScannerControls | null>(null);
  const cameraRunningRef = useRef(false);
  // Worker do Tesseract reutilizável — criado uma vez e reaproveitado
  const tesseractWorkerRef = useRef<Tesseract.Worker | null>(null);
  const workerLoadingRef = useRef(false);

  function log(msg: string) {
    setDebugLogs(prev => [...prev.slice(-6), `${new Date().toLocaleTimeString()} ${msg}`]);
  }

  // ─── Tesseract worker: lazy init ──────────────────────────────────────────
  async function getWorker(): Promise<Tesseract.Worker> {
    if (tesseractWorkerRef.current) return tesseractWorkerRef.current;
    if (workerLoadingRef.current) {
      // Espera o worker carregando
      await new Promise<void>(resolve => {
        const check = setInterval(() => {
          if (!workerLoadingRef.current) { clearInterval(check); resolve(); }
        }, 200);
      });
      return tesseractWorkerRef.current!;
    }
    workerLoadingRef.current = true;
    log('Criando worker Tesseract...');
    try {
      const worker = await criarWorkerTesseract();
      tesseractWorkerRef.current = worker;
      log('Worker Tesseract pronto');
      return worker;
    } finally {
      workerLoadingRef.current = false;
    }
  }

  async function encerrarWorker() {
    if (tesseractWorkerRef.current) {
      try { await tesseractWorkerRef.current.terminate(); } catch (_) {}
      tesseractWorkerRef.current = null;
    }
  }

  // ─── Câmera ───────────────────────────────────────────────────────────────

  /**
   * Deita a tela ao ligar a câmera. O ganho real não é estético: com o celular
   * em pé a lente enxerga um retângulo alto e estreito, e o código de barras
   * (largo) só cabe se o usuário se afastar — de longe, cada barra recebe
   * poucos pixels e a leitura falha. Deitado, o código atravessa o lado longo
   * do sensor e dá pra encostar no boleto.
   *
   * O lock só é permitido em fullscreen de verdade (daí o requestFullscreen
   * antes) e não existe no Safari/iPhone — lá caímos no giro manual.
   */
  const entrarModoPaisagem = useCallback(async () => {
    try {
      await wrapperRef.current?.requestFullscreen();
    } catch (_) {}
    try {
      await (screen.orientation as any).lock('landscape');
      setGiroManual(false);
    } catch (_) {
      setGiroManual(true);
    }
  }, []);

  const pararCamera = useCallback(() => {
    cameraRunningRef.current = false;
    if (scannerControlsRef.current) {
      scannerControlsRef.current.stop();
      scannerControlsRef.current = null;
    }
    if (scannerRef.current) {
      scannerRef.current.innerHTML = '';
    }
    try { (screen.orientation as any).unlock(); } catch (_) {}
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    setGiroManual(false);
    setGirado(false);
    setEscaneando(false);
    setCameraStarted(false);
  }, []);

  // Se a página girar sozinha (Android, ou iPhone sem trava de rotação), a
  // rotação manual por CSS viraria giro em dobro — desliga.
  useEffect(() => {
    const mq = window.matchMedia('(orientation: landscape)');
    const atualizar = () => {
      setPaisagem(mq.matches);
      if (mq.matches) setGirado(false);
    };
    atualizar();
    mq.addEventListener('change', atualizar);
    return () => mq.removeEventListener('change', atualizar);
  }, []);

  const iniciarCameraRef = useRef<() => void>(() => {});
  useEffect(() => { iniciarCameraRef.current = iniciarCamera; });

  /**
   * Girar com o leitor JÁ rodando (o caso do iPhone, onde o giro é manual)
   * corrompe a leitura: o ZXing dimensiona o canvas de captura uma única vez,
   * no início do scan, e depois desenha cada quadro sem escalar — girando, o
   * quadro fica maior que o canvas e o código de barras é cortado fora.
   * Reiniciar o leitor recria o canvas no tamanho novo.
   *
   * Escuta só enquanto está escaneando, não desde cameraStarted: o giro que nós
   * mesmos forçamos acontece ANTES de o scan começar, e reiniciar por causa dele
   * seria um laço.
   */
  useEffect(() => {
    if (!escaneando) return;
    const aoGirar = () => {
      log('Orientação mudou — reiniciando o leitor');
      iniciarCameraRef.current();
    };
    window.addEventListener('orientationchange', aoGirar);
    return () => window.removeEventListener('orientationchange', aoGirar);
  }, [escaneando]);

  useEffect(() => {
    if (!aberto) {
      pararCamera();
      resultadoRef.current = null;
      setCodigoLido(null);
      setPreviewUrl(null);
      setPaginasPDF([]);
      setPdfRef(null);
    } else {
      setAba(APARELHO_DE_TOQUE ? 'camera' : 'imagem');
      resultadoRef.current = null;
      setPreviewUrl(null);
      setPaginasPDF([]);
      setPdfRef(null);
      // Pré-carrega worker ao abrir (sem bloqueio)
      getWorker().catch(() => {});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [aberto]);

  // Limpa câmera ao trocar de aba
  useEffect(() => {
    if (!aberto || aba !== 'camera') {
      pararCamera();
    }
    return pararCamera;
  }, [aberto, aba, pararCamera]);

  // Encerra worker ao desmontar
  useEffect(() => {
    return () => { encerrarWorker(); };
  }, []);

  // ─── Handlers ─────────────────────────────────────────────────────────────

  /**
   * Portão único de aceitação. Antes daqui, qualquer coisa com 44 a 48 dígitos
   * era gravada direto: uma linha digitável de 47 que perdesse 3 dígitos virava
   * um "código de barras de 44" perfeitamente válido aos olhos do sistema, e o
   * usuário não tinha como perceber. Agora o dígito verificador decide, e o que
   * passa ainda vai para conferência humana — a aritmética pega ~99% das
   * leituras erradas, e o olho no valor e na data cobre o resto.
   */
  function aceitar(codigo: string, origem: 'camera' | 'outro') {
    const r = lerCodigo(codigo);

    if (!r.valido) {
      addToast('info', `${r.motivo} Tente ler novamente.`);
      resultadoRef.current = codigo.replace(/\D/g, '');
      return;
    }

    setConfirmacao({ dados: r.dados, origem });
  }

  /** Só aqui o código sai do modal — e sempre como linha digitável (47/48). */
  function confirmar() {
    if (!confirmacao) return;
    const { dados, origem } = confirmacao;
    setConfirmacao(null);
    onCodigoLido(dados.linhaDigitavel);
    if (origem === 'camera') setCodigoLido(dados.linhaDigitavel);
    else onFechar();
  }

  function handleCodigo(codigo: string) {
    aceitar(codigo, 'outro');
  }

  /**
   * Sucesso na leitura pela CÂMERA. Diferente das abas de imagem e PDF, aqui o
   * celular está deitado na mão do usuário — e, fora do fullscreen, o navegador
   * obedece ao aparelho: não há como forçar a página de volta a retrato. Fechar
   * o modal agora descobriria o app em paisagem, onde as tabelas viram tabela de
   * desktop e o formulário vira um cartão centralizado.
   *
   * Então o campo é preenchido na hora, mas um painel de sucesso fica por cima
   * até o usuário endireitar o celular — o gesto natural depois de ler. Se o
   * aparelho já estiver em pé (trava de rotação ligada), o painel fecha sozinho
   * no mesmo instante e ninguém vê etapa nenhuma.
   */
  function handleCodigoCamera(codigo: string) {
    aceitar(codigo, 'camera');
  }

  function concluirLeitura() {
    setCodigoLido(null);
    onFechar();
  }

  useEffect(() => {
    if (codigoLido && !paisagem) {
      concluirLeitura();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [codigoLido, paisagem]);

  async function iniciarCamera() {
    if (!scannerRef.current) return;
    pararCamera();
    setLendo(true);
    log('Iniciando câmera...');
    try {
      // Deita a tela ANTES de abrir a câmera — a ordem é o que faz a leitura
      // funcionar. O ZXing dimensiona o canvas de captura no instante em que o
      // scan começa e nunca mais o redimensiona (desenha cada quadro sem
      // escalar): girar depois deixava o canvas mais estreito que o quadro e
      // cortava fora justamente o miolo, onde está o código. De quebra, o
      // fullscreen é pedido logo após o clique, enquanto o gesto do usuário
      // ainda vale — depois da permissão da câmera ele já pode ter expirado.
      setCameraStarted(true);
      await new Promise((r) => requestAnimationFrame(() => r(null)));
      await entrarModoPaisagem();

      const hints = new Map();
      hints.set(DecodeHintType.POSSIBLE_FORMATS, [BarcodeFormat.ITF, BarcodeFormat.CODE_128]);
      hints.set(DecodeHintType.TRY_HARDER, true);
      hints.set(DecodeHintType.ALLOWED_LENGTHS, [44, 47, 48]);
      const reader = new BrowserMultiFormatReader(hints, {
        delayBetweenScanAttempts: 200,
        delayBetweenScanSuccess: 200,
      });

      const video = document.createElement('video');
      video.style.width = '100%';
      video.style.height = '100%';
      video.style.objectFit = 'cover';

      const container = scannerRef.current;
      container.innerHTML = '';
      container.appendChild(video);

      const controls = await reader.decodeFromConstraints(
          {
            video: {
              facingMode: 'environment',
              width: { ideal: 1280 },
              height: { ideal: 720 },
              focusMode: 'continuous',
            } as any,
            audio: false,
          },
          video,
          (result, _err, controls) => {
            if (!result || resultadoRef.current || !cameraRunningRef.current) return;
            const codigo = result.getText();
            if (!codigo) return;
            const apenasDigitos = codigo.replace(/\D/g, '');
            if ([44, 47, 48].includes(apenasDigitos.length)) {
              log(`Código lido: ${apenasDigitos}`);
              resultadoRef.current = apenasDigitos;
              controls.stop();
              pararCamera();
              handleCodigoCamera(apenasDigitos);
            }
          }
      );
      scannerControlsRef.current = controls;
      cameraRunningRef.current = true;
      setEscaneando(true);
      log('Câmera iniciada com sucesso');
    } catch (err: any) {
      log(`ERRO câmera: ${err?.name} - ${err?.message}`);
      // Falhou depois de já termos entrado em tela cheia e travado a orientação
      // — pararCamera desfaz os dois.
      pararCamera();
      if (err?.name === 'NotAllowedError') {
        addToast('error', 'Permissão de câmera negada. Vá em Configurações do navegador e permita o acesso à câmera.');
      } else if (err?.name === 'NotFoundError') {
        addToast('error', 'Nenhuma câmera encontrada no dispositivo.');
      } else if (err?.name === 'NotReadableError') {
        addToast('error', 'Câmera em uso por outro aplicativo. Feche outros apps e tente novamente.');
      } else {
        addToast('error', `Erro ao acessar câmera: ${err?.message || 'desconhecido'}`);
      }
    } finally {
      setLendo(false);
    }
  }

  async function lerDeImagem(file: File) {
    if (file.size > 10 * 1024 * 1024) {
      addToast('error', 'Arquivo muito grande. Use uma imagem menor que 10MB.');
      return;
    }

    log(`Arquivo: ${file.name} | Tamanho: ${(file.size / 1024).toFixed(0)}KB`);
    setLendo(true);
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    log('Preview URL criada');

    try {
      log('Obtendo worker Tesseract...');
      const worker = await getWorker();
      log('Iniciando OCR...');

      const resultado = await Promise.race([
        worker.recognize(url),
        new Promise<never>((_, reject) =>
            setTimeout(() => reject(new Error('Timeout OCR')), 60000)
        ),
      ]);
      log(`OCR concluído. Texto: ${resultado.data.text.substring(0, 80)}...`);

      const codigo = extrairCodigoDigitavel(resultado.data.text);

      if (codigo) {
        log(`Código encontrado: ${codigo}`);
        handleCodigo(codigo);
      } else {
        log('Nenhum código encontrado na imagem');
        addToast('error', 'Não foi possível identificar um código de boleto na imagem. Certifique-se que o código digitável está visível.');
      }
    } catch (err: any) {
      log(`ERRO lerDeImagem: ${err?.name} - ${err?.message}`);
      // Worker pode ter corrompido — descarta para recriar na próxima tentativa
      await encerrarWorker();
      addToast('error', `Erro ao processar a imagem: ${err?.message || 'desconhecido'}`);
    } finally {
      setLendo(false);
      URL.revokeObjectURL(url);
    }
  }

  async function lerCodigoDaPagina(pdf: pdfjsLib.PDFDocumentProxy, numeroPagina: number) {
    log(`Lendo página ${numeroPagina}...`);
    setPaginaCarregando(numeroPagina);
    setLendo(true);

    try {
      log('Tentando extração de texto embutido...');
      const page = await pdf.getPage(numeroPagina);
      const content = await page.getTextContent();
      const texto = content.items.map((item: any) => item.str).join(' ');
      log(`Texto extraído: ${texto.substring(0, 80)}...`);

      const codigo = extrairCodigoDigitavel(texto);
      if (codigo) {
        log(`Código encontrado via texto: ${codigo}`);
        handleCodigo(codigo);
        setPaginasPDF([]);
        setPdfRef(null);
        return;
      }

      log('Texto embutido sem código. Iniciando OCR...');

      const isMobileDevice = /Android|iPhone|iPad/i.test(navigator.userAgent);
      const scale = isMobileDevice ? 1.2 : 2.0;
      log(`Scale OCR: ${scale}`);

      const viewport = page.getViewport({ scale });
      const canvas = document.createElement('canvas');
      canvas.width = viewport.width;
      canvas.height = viewport.height;

      await page.render({ canvasContext: canvas.getContext('2d')!, viewport }).promise;
      log('Render OK. Convertendo para JPEG...');

      const dataUrl = canvas.toDataURL('image/jpeg', 0.8);
      log(`JPEG gerado: ${(dataUrl.length / 1024).toFixed(0)}KB`);

      log('Obtendo worker Tesseract...');
      const worker = await getWorker();
      log('Iniciando OCR no PDF...');

      const resultado = await Promise.race([
        worker.recognize(dataUrl),
        new Promise<never>((_, reject) =>
            setTimeout(() => reject(new Error('Timeout OCR')), 60000)
        ),
      ]);
      log(`OCR concluído. Texto: ${resultado.data.text.substring(0, 80)}...`);

      const codigoOCR = extrairCodigoDigitavel(resultado.data.text);
      if (codigoOCR) {
        log(`Código encontrado via OCR: ${codigoOCR}`);
        handleCodigo(codigoOCR);
        setPaginasPDF([]);
        setPdfRef(null);
      } else {
        log('Nenhum código encontrado nesta página');
        addToast('error', 'Código não encontrado nesta página. Tente outra.');
      }
    } catch (err: any) {
      log(`ERRO lerCodigoDaPagina: ${err?.name} - ${err?.message}`);
      await encerrarWorker();
      addToast('error', `Erro ao ler página: ${err?.message || 'desconhecido'}`);
      console.error('lerCodigoDaPagina error:', err);
    } finally {
      setPaginaCarregando(null);
      setLendo(false);
    }
  }

  async function lerDePDF(file: File) {
    log(`Arquivo: ${file.name} | Tamanho: ${(file.size / 1024).toFixed(0)}KB`);
    setLendo(true);

    try {
      log('Lendo ArrayBuffer...');
      const buffer = await file.arrayBuffer();
      log('ArrayBuffer OK. Carregando PDF...');

      const pdf = await pdfjsLib.getDocument({ data: buffer }).promise;
      log(`PDF carregado: ${pdf.numPages} página(s)`);

      if (pdf.numPages === 1) {
        await lerCodigoDaPagina(pdf, 1);
        return;
      }

      log('Gerando previews...');
      const isMobileDevice = /Android|iPhone|iPad/i.test(navigator.userAgent);
      const scalePreview = isMobileDevice ? 0.25 : 0.4;
      const previews: string[] = [];

      for (let i = 1; i <= pdf.numPages; i++) {
        log(`Preview página ${i}...`);
        const page = await pdf.getPage(i);
        const viewport = page.getViewport({ scale: scalePreview });
        const canvas = document.createElement('canvas');
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        await page.render({ canvasContext: canvas.getContext('2d')!, viewport }).promise;
        previews.push(canvas.toDataURL('image/jpeg', 0.6));
        log(`Preview ${i} OK (${canvas.width}x${canvas.height})`);
      }

      setPdfRef(pdf);
      setPaginasPDF(previews);
      log('Seletor de páginas exibido');
    } catch (err: any) {
      log(`ERRO lerDePDF: ${err?.name} - ${err?.message}`);
      addToast('error', `Erro ao processar PDF: ${err?.message || 'desconhecido'}`);
    } finally {
      setLendo(false);
    }
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = '';

    if (aba === 'imagem') {
      lerDeImagem(file);
    } else if (aba === 'pdf') {
      lerDePDF(file);
    }
  }

  if (!aberto) return null;

  const abas: { id: Aba; label: string; icon: typeof Camera }[] = [];
  if (APARELHO_DE_TOQUE) abas.push({ id: 'camera', label: 'Câmera', icon: Camera });
  abas.push({ id: 'imagem', label: 'Imagem', icon: Image });
  abas.push({ id: 'pdf', label: 'PDF', icon: FileText });

  return (
      <>
        {/* Modal principal */}
        <div
            className="fixed inset-0 z-[60] flex items-end sm:items-center justify-center bg-black/40"
            style={{ display: cameraStarted ? 'none' : 'flex' }}
            onClick={onFechar}
        >
          <div
              className="bg-white w-full sm:max-w-lg rounded-t-2xl sm:rounded-2xl animate-slide-in max-h-[90vh] overflow-y-auto"
              onClick={(e) => e.stopPropagation()}
          >
            {/* Cabeçalho */}
            <div className="flex items-center justify-between p-4 border-b border-slate-100">
              <h2 className="text-lg font-bold text-slate-900">Ler código de barras</h2>
              <button onClick={onFechar} className="p-2 rounded-lg hover:bg-slate-100 text-slate-400">
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Abas */}
            <div className="flex border-b border-slate-100">
              {abas.map(({ id, label, icon: Icon }) => (
                  <button
                      key={id}
                      onClick={() => setAba(id)}
                      className={clsx(
                          'flex-1 flex items-center justify-center gap-2 py-3 text-sm font-medium border-b-2 transition-colors',
                          aba === id
                              ? 'text-[#0c4a6e] border-[#0c4a6e]'
                              : 'text-slate-400 border-transparent hover:text-slate-600'
                      )}
                  >
                    <Icon className="w-4 h-4" />
                    {label}
                  </button>
              ))}
            </div>

            <div className="p-4">
              {/* Aba Câmera */}
              {aba === 'camera' && (
                  <div>
                    {!cameraStarted ? (
                        <div className="flex flex-col items-center justify-center py-16">
                          <Camera className="w-12 h-12 mb-3 text-slate-300" />
                          <p className="text-sm text-slate-400 mb-4">Toque no botão para ligar a câmera</p>
                          <button
                              onClick={iniciarCamera}
                              disabled={lendo}
                              className="px-6 py-3 bg-[#0c4a6e] text-white rounded-xl text-sm font-medium disabled:opacity-50"
                          >
                            {lendo ? (
                                <span className="flex items-center gap-2">
                          <Loader2 className="w-4 h-4 animate-spin" />
                          Aguardando permissão...
                        </span>
                            ) : (
                                'Ligar câmera'
                            )}
                          </button>
                        </div>
                    ) : (
                        <div className="flex items-center justify-center py-8">
                          <p className="text-sm text-slate-400">Câmera ativa em tela cheia</p>
                        </div>
                    )}
                    {resultadoRef.current && !lerCodigo(resultadoRef.current).valido && (
                        <div className="mt-4">
                          <div className="bg-amber-50 border border-amber-200 rounded-xl p-3 mb-3">
                            <p className="text-xs text-amber-600 font-medium">Código identificado (não reconhecido como boleto):</p>
                            <p className="text-sm font-mono text-amber-800 break-all mt-1">{resultadoRef.current}</p>
                          </div>
                          <button
                              onClick={() => { onCodigoLido(resultadoRef.current!); onFechar(); }}
                              className="w-full px-4 py-2.5 bg-[#0c4a6e] text-white rounded-xl text-sm font-medium"
                          >
                            Usar mesmo assim
                          </button>
                        </div>
                    )}
                  </div>
              )}

              {/* Aba Imagem / PDF */}
              {(aba === 'imagem' || aba === 'pdf') && (
                  <div className="space-y-4">

                    {/* Seletor de arquivo — escondido enquanto seletor de páginas PDF estiver ativo */}
                    {paginasPDF.length === 0 && (
                        <label
                            htmlFor="file-input"
                            className="border-2 border-dashed border-slate-200 rounded-xl p-8 text-center cursor-pointer hover:border-[#0ea5e9] transition-colors block"
                        >
                          {aba === 'imagem' ? (
                              <>
                                <Image className="w-8 h-8 mx-auto mb-2 text-slate-300" />
                                <p className="text-sm text-slate-500">Clique para selecionar uma imagem</p>
                                <p className="text-xs text-slate-400 mt-1">PNG, JPG, JPEG, WEBP</p>
                              </>
                          ) : (
                              <>
                                <FileText className="w-8 h-8 mx-auto mb-2 text-slate-300" />
                                <p className="text-sm text-slate-500">Clique para selecionar um PDF</p>
                                <p className="text-xs text-slate-400 mt-1">Até 10MB</p>
                              </>
                          )}
                        </label>
                    )}

                    {previewUrl && aba === 'imagem' && (
                        <div className="rounded-xl overflow-hidden border border-slate-100">
                          <img src={previewUrl} alt="Preview" className="w-full" />
                        </div>
                    )}

                    {/* Seletor de páginas do PDF */}
                    {paginasPDF.length > 0 && (
                        <div>
                          {/* Cabeçalho do seletor */}
                          <div className="flex items-center justify-between mb-3">
                            <div>
                              <p className="text-sm font-semibold text-slate-700">
                                PDF com {paginasPDF.length} páginas
                              </p>
                              <p className="text-xs text-slate-400 mt-0.5">
                                Toque na página que contém o código de barras
                              </p>
                            </div>
                            <button
                                onClick={() => { setPaginasPDF([]); setPdfRef(null); }}
                                className="flex items-center gap-1 text-xs text-slate-400 hover:text-slate-600 border border-slate-200 rounded-lg px-2 py-1.5 transition-colors"
                            >
                              <X className="w-3 h-3" />
                              Trocar arquivo
                            </button>
                          </div>

                          {/* Grid de miniaturas */}
                          <div className="grid grid-cols-2 gap-3">
                            {paginasPDF.map((src, i) => (
                                <button
                                    key={i}
                                    onClick={() => pdfRef && lerCodigoDaPagina(pdfRef, i + 1)}
                                    disabled={lendo}
                                    className={clsx(
                                        'relative border-2 rounded-xl overflow-hidden transition-all group',
                                        paginaCarregando === i + 1
                                            ? 'border-[#0ea5e9] scale-[0.98]'
                                            : 'border-slate-200 hover:border-[#0ea5e9] hover:shadow-md',
                                        lendo && paginaCarregando !== i + 1 && 'opacity-40 cursor-not-allowed'
                                    )}
                                >
                                  <img src={src} alt={`Página ${i + 1}`} className="w-full block" />

                                  {/* Spinner na página sendo processada */}
                                  {paginaCarregando === i + 1 && (
                                      <div className="absolute inset-0 bg-[#0c4a6e]/60 flex flex-col items-center justify-center gap-2">
                                        <Loader2 className="w-6 h-6 text-white animate-spin" />
                                        <span className="text-white text-xs font-medium">Lendo...</span>
                                      </div>
                                  )}

                                  {/* Hover overlay nas outras páginas */}
                                  {paginaCarregando !== i + 1 && (
                                      <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors" />
                                  )}

                                  {/* Label da página */}
                                  <div className={clsx(
                                      'absolute bottom-0 inset-x-0 text-white text-xs py-1.5 text-center font-medium',
                                      paginaCarregando === i + 1 ? 'bg-[#0c4a6e]' : 'bg-slate-900/70'
                                  )}>
                                    Página {i + 1}
                                  </div>
                                </button>
                            ))}
                          </div>
                        </div>
                    )}

                    {lendo && paginaCarregando === null && (
                        <div className="flex items-center justify-center gap-2 text-sm text-slate-500">
                          <Loader2 className="w-4 h-4 animate-spin" />
                          {workerLoadingRef.current ? 'Carregando OCR (primeira vez)...' : 'Lendo código...'}
                        </div>
                    )}

                    {resultadoRef.current && !lerCodigo(resultadoRef.current).valido && (
                        <div className="bg-amber-50 border border-amber-200 rounded-xl p-3 mb-3">
                          <p className="text-xs text-amber-600 font-medium">Código identificado (não reconhecido como boleto):</p>
                          <p className="text-sm font-mono text-amber-800 break-all mt-1">{resultadoRef.current}</p>
                          <button
                              onClick={() => { onCodigoLido(resultadoRef.current!); onFechar(); }}
                              className="mt-2 w-full px-4 py-2.5 bg-[#0c4a6e] text-white rounded-xl text-sm font-medium"
                          >
                            Usar mesmo assim
                          </button>
                        </div>
                    )}
                  </div>
              )}
            </div>

            {/* Debug */}
            {debugLogs.length > 0 && (
                <div className="px-4 pb-3">
                  <details>
                    <summary className="text-xs text-slate-400 cursor-pointer hover:text-slate-600 select-none">
                      Debug ({debugLogs.length})
                    </summary>
                    <div className="mt-2 bg-slate-900 text-green-400 rounded-lg p-2 text-xs font-mono max-h-40 overflow-y-auto space-y-0.5">
                      {debugLogs.map((l, i) => (
                          <div key={i}>{l}</div>
                      ))}
                    </div>
                  </details>
                </div>
            )}

            <input
                ref={fileInputRef}
                id="file-input"
                type="file"
                accept={aba === 'imagem' ? 'image/*' : 'application/pdf'}
                className="hidden"
                onChange={handleFileChange}
            />
          </div>
        </div>

        {/* Câmera em tela cheia: vídeo + overlay no MESMO elemento, que é o
            alvo do fullscreen — fora dele, nada é renderizado em fullscreen.
            Quando o giro é manual (Safari), o conjunto todo é rotacionado 90°
            para acompanhar o celular deitado na mão do usuário. */}
        <div
            ref={wrapperRef}
            style={{
              position: 'fixed',
              zIndex: cameraStarted ? 70 : -1,
              opacity: cameraStarted ? 1 : 0,
              visibility: cameraStarted ? 'visible' : 'hidden',
              background: '#000',
              ...(girado
                  ? {
                    top: 0,
                    left: 0,
                    width: '100vh',
                    height: '100vw',
                    transformOrigin: 'top left',
                    transform: 'rotate(90deg) translateY(-100%)',
                  }
                  : { inset: 0 }),
            }}
        >
          <div ref={scannerRef} id="scanner-container" className="absolute inset-0" />

          {cameraStarted && (
              <div className="absolute inset-0 z-[71]" style={{ pointerEvents: 'none' }}>
                <button
                    onClick={pararCamera}
                    className="absolute top-4 right-4 z-10 pointer-events-auto p-2 rounded-full bg-black/50 text-white"
                >
                  <X className="w-6 h-6" />
                </button>

                <div className="absolute top-0 left-0 right-0 px-4 py-3 bg-gradient-to-b from-black/60 to-transparent pointer-events-auto">
                  <span className="text-white text-sm">
                    {giroManual && !paisagem && !girado
                        ? 'Vire o celular na horizontal para chegar mais perto do código'
                        : 'Alinhe o código na linha vermelha'}
                  </span>
                </div>

                <div className="absolute inset-0">
                  <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: '35%', background: 'rgba(0,0,0,0.55)' }} />
                  <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, height: '35%', background: 'rgba(0,0,0,0.55)' }} />
                  <div style={{ position: 'absolute', top: '35%', left: 0, width: '5%', height: '30%', background: 'rgba(0,0,0,0.55)' }} />
                  <div style={{ position: 'absolute', top: '35%', right: 0, width: '5%', height: '30%', background: 'rgba(0,0,0,0.55)' }} />
                  <div style={{ position: 'absolute', top: '35%', left: '5%', width: '90%', height: '30%', border: '2px solid #22c55e', borderRadius: 6, boxSizing: 'border-box' }} />
                  <div style={{ position: 'absolute', top: '50%', left: '5%', width: '90%', height: '2px', background: '#ef4444', zIndex: 10 }} />
                </div>

                {/* Só aparece onde o giro automático não é possível (Safari) e a
                    página continua em pé — ou seja, com a trava de rotação do
                    iOS ligada, único caso em que virar o aparelho não basta. */}
                {giroManual && !paisagem && (
                    <button
                        onClick={() => setGirado((g) => !g)}
                        className="absolute bottom-6 left-1/2 -translate-x-1/2 pointer-events-auto flex items-center gap-2 px-4 py-2.5 rounded-full bg-white/90 text-slate-900 text-sm font-medium"
                    >
                      <RotateCw className="w-4 h-4" />
                      {girado ? 'Desgirar tela' : 'Girar tela'}
                    </button>
                )}

                {lendo && (
                    <div className="absolute bottom-16 left-0 right-0 flex items-center justify-center gap-2 text-white text-sm">
                      <Loader2 className="w-4 h-4 animate-spin" />
                      Aguardando código...
                    </div>
                )}
              </div>
          )}
        </div>

        {/* Conferência antes de gravar. O dígito verificador pega ~99% das
            leituras erradas, mas o padrão FEBRABAN colapsa alguns restos do
            módulo 11 num mesmo DV — sobra uma zona cega que nenhuma
            implementação alcança. Estes três campos são a segunda camada: o
            usuário reconhece na hora um valor ou vencimento que não é o dele. */}
        {confirmacao && (
            <div className="fixed inset-0 z-[90] bg-black/60 flex items-end sm:items-center justify-center p-4">
              <div className="bg-white rounded-2xl w-full max-w-sm p-5 space-y-4">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-emerald-50 flex items-center justify-center shrink-0">
                    <Check className="w-5 h-5 text-emerald-600" />
                  </div>
                  <div>
                    <p className="font-bold text-slate-900 leading-tight">Confira os dados</p>
                    <p className="text-xs text-slate-500">{confirmacao.dados.tipo}</p>
                  </div>
                </div>

                <dl className="space-y-2 text-sm">
                  <div className="flex justify-between gap-3">
                    <dt className="text-slate-500 shrink-0">Emissor</dt>
                    <dd className="font-medium text-slate-900 text-right">
                      {confirmacao.dados.emissor ?? 'Não identificado'}
                    </dd>
                  </div>
                  <div className="flex justify-between gap-3">
                    <dt className="text-slate-500 shrink-0">Valor</dt>
                    <dd className="font-medium text-slate-900 text-right">
                      {confirmacao.dados.valor != null
                          ? confirmacao.dados.valor.toLocaleString('pt-BR',
                              { style: 'currency', currency: 'BRL' })
                          : 'Não consta no código'}
                    </dd>
                  </div>
                  <div className="flex justify-between gap-3">
                    <dt className="text-slate-500 shrink-0">Vencimento</dt>
                    <dd className="font-medium text-slate-900 text-right">
                      {confirmacao.dados.vencimento
                          ? confirmacao.dados.vencimento.toLocaleDateString('pt-BR',
                              { timeZone: 'UTC' })
                          : 'Não consta no código'}
                    </dd>
                  </div>
                </dl>

                <div className="bg-slate-50 rounded-xl p-3">
                  <p className="text-[10px] uppercase tracking-wide text-slate-400 mb-1">
                    Linha digitável
                  </p>
                  <p className="font-mono text-[11px] leading-snug text-slate-700 break-all">
                    {formatarLinha(confirmacao.dados.linhaDigitavel)}
                  </p>
                </div>

                <div className="flex gap-2 pt-1">
                  <button
                      onClick={() => setConfirmacao(null)}
                      className="flex-1 px-4 py-2.5 rounded-xl border border-slate-200 text-slate-600 text-sm font-medium"
                  >
                    Cancelar
                  </button>
                  <button
                      onClick={confirmar}
                      className="flex-1 px-4 py-2.5 rounded-xl bg-[#0c4a6e] text-white text-sm font-medium"
                  >
                    OK
                  </button>
                </div>
              </div>
            </div>
        )}

        {/* Painel de sucesso: cobre o app enquanto o celular ainda está deitado,
            para que o layout de desktop nunca apareça. Fecha sozinho quando o
            aparelho volta a ficar em pé. */}
        {codigoLido && (
            <div className="fixed inset-0 z-[80] bg-[#0c4a6e] text-white flex flex-col items-center justify-center gap-3 px-8 text-center">
              <div className="w-16 h-16 rounded-full bg-white/15 flex items-center justify-center">
                <Check className="w-9 h-9" />
              </div>
              <p className="text-xl font-bold">Código lido!</p>
              <p className="text-sm text-white/70">Endireite o celular para continuar</p>
              <button
                  onClick={concluirLeitura}
                  className="mt-3 px-6 py-2.5 rounded-xl bg-white text-[#0c4a6e] text-sm font-medium"
              >
                Continuar
              </button>
            </div>
        )}
      </>
  );
}
