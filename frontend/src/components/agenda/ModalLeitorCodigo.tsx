import { useState, useRef, useEffect, useCallback } from 'react';
import { BrowserMultiFormatOneDReader } from '@zxing/browser';
import { BarcodeFormat, DecodeHintType } from '@zxing/library';
import * as pdfjsLib from 'pdfjs-dist';
import Tesseract from 'tesseract.js';
import { X, Camera, Image, FileText, Loader2 } from 'lucide-react';
import { clsx } from 'clsx';
import { useIsMobile } from '../../hooks/useIsMobile';
import { useToastStore } from '../../store/toastStore';

const PDFJS_VERSION = (pdfjsLib as any).version || '4.0.379';
pdfjsLib.GlobalWorkerOptions.workerSrc = `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/${PDFJS_VERSION}/pdf.worker.min.mjs`;

const FORMATOS_BOLETO = [
  BarcodeFormat.CODE_128,
  BarcodeFormat.ITF,
  BarcodeFormat.CODE_39,
  BarcodeFormat.CODABAR,
  BarcodeFormat.EAN_13,
  BarcodeFormat.EAN_8,
  BarcodeFormat.CODE_93,
  BarcodeFormat.UPC_A,
  BarcodeFormat.UPC_E,
];

const TINY_PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';

function criarLeitor() {
  const hints = new Map();
  hints.set(DecodeHintType.POSSIBLE_FORMATS, FORMATOS_BOLETO);
  hints.set(DecodeHintType.TRY_HARDER, true);
  const isMobileDevice = /Android|iPhone|iPad/i.test(navigator.userAgent);
  return new BrowserMultiFormatOneDReader(hints, {
    delayBetweenScanSuccess: 500,
    tryPlayVideoTimeout: isMobileDevice ? 15000 : 5000,
  });
}

function extrairCodigoDigitavel(texto: string): string | null {
  const t = texto.replace(/\r?\n/g, ' ').replace(/\s{2,}/g, ' ');

  const m1 = t.match(
    /\d{5}\.\d{4,6}\s+\d{5}\.\d{5,6}\s+\d{5}\.\d{5,6}\s+\d\s+\d{14}/
  );
  if (m1) return m1[0].replace(/\s+/g, '');

  const m2 = t.match(
    /\d{8,11}-\d\s+\d{8,11}-\d\s+\d{8,11}-\d\s+\d{8,11}-\d/
  );
  if (m2) return m2[0].replace(/[\s-]/g, '');

  const m3 = t.match(
    /\d{10,11}\s+\d\s+\d{10,11}\s+\d\s+\d{10,11}\s+\d\s+\d{10,11}\s+\d/
  );
  if (m3) return m3[0].replace(/\s+/g, '');

  const semEspacos = t.replace(/[\s.\-]/g, '');
  const m4 = semEspacos.match(/\d{44,48}/);
  if (m4) return m4[0];

  return null;
}

function identificarTipoDocumento(codigo: string): { tipo: string; valido: boolean } {
  const limpo = codigo.replace(/[\s.\-]/g, '');

  if (limpo.length === 47 || limpo.length === 48) {
    if (limpo.startsWith('8')) {
      const banco = limpo.substring(1, 4);
      if (banco === '582' || banco === '580') return { tipo: 'GPS / INSS', valido: true };
      if (banco === '586') return { tipo: 'DARF / Receita Federal', valido: true };
      if (banco === '567' || banco === '566') return { tipo: 'DAR Estadual', valido: true };
      if (banco === '858') return { tipo: 'DARF / GPS / GNRE', valido: true };
      return { tipo: 'Guia de Arrecadação', valido: true };
    }
    return { tipo: 'Boleto Bancário', valido: true };
  }

  return { tipo: 'Desconhecido', valido: false };
}

function validarCodigoBoleto(codigo: string): boolean {
  const limpo = codigo.replace(/[\s.\-]/g, '');
  return /^\d{44,48}$/.test(limpo);
}

function ehFormatoBoleto(format: any): boolean {
  return format !== undefined &&
    format !== BarcodeFormat.QR_CODE &&
    format !== BarcodeFormat.AZTEC &&
    format !== BarcodeFormat.DATA_MATRIX &&
    format !== BarcodeFormat.PDF_417 &&
    format !== BarcodeFormat.MAXICODE;
}

type Aba = 'camera' | 'imagem' | 'pdf';

export function ModalLeitorCodigo({
  aberto,
  onFechar,
  onCodigoLido,
}: {
  aberto: boolean;
  onFechar: () => void;
  onCodigoLido: (codigo: string) => void;
}) {
  const isMobile = useIsMobile();
  const addToast = useToastStore((s) => s.addToast);
  const [aba, setAba] = useState<Aba>('imagem');
  const [lendo, setLendo] = useState(false);
  const [preparandoOCR, setPreparandoOCR] = useState(false);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [paginasPDF, setPaginasPDF] = useState<string[]>([]);
  const [pdfRef, setPdfRef] = useState<pdfjsLib.PDFDocumentProxy | null>(null);
  const [paginaCarregando, setPaginaCarregando] = useState<number | null>(null);
  const [debugLogs, setDebugLogs] = useState<string[]>([]);
  const [cameraStarted, setCameraStarted] = useState(false);
  const resultadoRef = useRef<string | null>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const controlsRef = useRef<{ stop: () => void } | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const tesseractReady = useRef(false);
  const ocrTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  function log(msg: string) {
    setDebugLogs(prev => [...prev.slice(-6), `${new Date().toLocaleTimeString()} ${msg}`]);
  }

  const pararCamera = useCallback(() => {
    controlsRef.current?.stop();
    controlsRef.current = null;
    setCameraStarted(false);
    if (videoRef.current?.srcObject instanceof MediaStream) {
      videoRef.current.srcObject.getTracks().forEach(t => t.stop());
      videoRef.current.srcObject = null;
    }
  }, []);

  useEffect(() => {
    if (!aberto) {
      pararCamera();
      setResultadoRef(null);
      setPreviewUrl(null);
      setPaginasPDF([]);
      setPdfRef(null);
      setPreparandoOCR(false);
      if (ocrTimeoutRef.current) clearTimeout(ocrTimeoutRef.current);
    } else {
      setAba(isMobile ? 'camera' : 'imagem');
      setResultadoRef(null);
      setPreviewUrl(null);
      setPaginasPDF([]);
      setPdfRef(null);

      if (!tesseractReady.current) {
        setPreparandoOCR(true);
        Tesseract.recognize(TINY_PNG, 'por').catch(() => {});
        ocrTimeoutRef.current = setTimeout(() => {
          tesseractReady.current = true;
          setPreparandoOCR(false);
        }, 8000);
      }
    }
  }, [aberto, isMobile, pararCamera]);

  useEffect(() => {
    if (!aberto || aba !== 'camera') {
      pararCamera();
    }
    return pararCamera;
  }, [aberto, aba]);

  function setResultadoRef(v: string | null) {
    resultadoRef.current = v;
  }

  function handleCodigo(codigo: string) {
    const limpo = codigo.replace(/[\s.\-]/g, '');
    const { tipo, valido } = identificarTipoDocumento(limpo);

    if (!valido) {
      addToast('info', 'Código identificado mas não reconhecido. Verifique se é um documento válido.');
      setResultadoRef(limpo);
      return;
    }

    addToast('success', `${tipo} lido com sucesso!`);
    onCodigoLido(limpo);
    onFechar();
  }

  async function iniciarCamera() {
    if (!videoRef.current) return;
    pararCamera();
    setLendo(true);
    setCameraStarted(false);

    log('Solicitando permissão da câmera...');

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: 'environment',
          width: { ideal: 1280 },
          height: { ideal: 720 },
        },
      });

      log('Permissão concedida. Atribuindo stream ao video...');
      videoRef.current.srcObject = stream;
      try {
        await videoRef.current.play();
      } catch (playErr: any) {
        log(`Aviso play(): ${playErr?.name || 'Unknown'}`);
        console.warn('Erro ao reproduzir vídeo, mas continuando:', playErr);
      }
      setCameraStarted(true);
      log('Câmera iniciada com sucesso');

      const reader = criarLeitor();
      const controls = await reader.decodeFromStream(
        stream,
        videoRef.current,
        (result) => {
          if (result && !resultadoRef.current) {
            if (!ehFormatoBoleto(result.getBarcodeFormat())) return;
            const codigo = result.getText();
            log(`Código lido: ${codigo}`);
            setResultadoRef(codigo);
            pararCamera();
            handleCodigo(codigo);
          }
        },
      );
      controlsRef.current = controls;
    } catch (err: any) {
      log(`ERRO câmera: ${err?.name} - ${err?.message}`);
      console.error('Erro ao acessar câmera:', err);

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
      log('Iniciando Tesseract...');
      const resultado = await Promise.race([
        Tesseract.recognize(url, 'por'),
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
      addToast('error', 'Erro ao processar a imagem');
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

      log('Texto embutido não tem código. Iniciando OCR...');

      const isMobileDevice = /Android|iPhone|iPad/i.test(navigator.userAgent);
      const scale = isMobileDevice ? 1.2 : 2.0;
      log(`Scale OCR: ${scale} (mobile: ${isMobileDevice})`);

      const viewport = page.getViewport({ scale });
      const canvas = document.createElement('canvas');
      canvas.width = viewport.width;
      canvas.height = viewport.height;
      log(`Canvas: ${canvas.width}x${canvas.height}`);

      await page.render({ canvasContext: canvas.getContext('2d')!, viewport }).promise;
      log('Render OK. Convertendo para JPEG...');

      const dataUrl = canvas.toDataURL('image/jpeg', 0.8);
      log(`JPEG gerado: ${(dataUrl.length / 1024).toFixed(0)}KB`);

      log('Iniciando Tesseract...');
      const resultado = await Promise.race([
        Tesseract.recognize(dataUrl, 'por'),
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
  if (isMobile) abas.push({ id: 'camera', label: 'Câmera', icon: Camera });
  abas.push({ id: 'imagem', label: 'Imagem', icon: Image });
  abas.push({ id: 'pdf', label: 'PDF', icon: FileText });

  return (
    <div className="fixed inset-0 z-[60] flex items-end sm:items-center justify-center bg-black/40" onClick={onFechar}>
      <div
        className="bg-white w-full sm:max-w-lg rounded-t-2xl sm:rounded-2xl animate-slide-in max-h-[90vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between p-4 border-b border-slate-100">
          <h2 className="text-lg font-bold text-slate-900">Ler código de barras</h2>
          <button onClick={onFechar} className="p-2 rounded-lg hover:bg-slate-100 text-slate-400">
            <X className="w-5 h-5" />
          </button>
        </div>

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
          {aba === 'camera' && (
            <div>
              <div style={{ position: 'relative', width: '100%', maxWidth: 400, margin: '0 auto', minHeight: 220 }}>
                {!cameraStarted && (
                  <div className="absolute inset-0 flex flex-col items-center justify-center z-10">
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
                )}
                <video ref={videoRef} className={clsx('w-full rounded-xl bg-slate-900', !cameraStarted && 'hidden')} autoPlay muted playsInline />
                {cameraStarted && lendo && (
                  <div className="flex items-center justify-center gap-2 mt-2 text-sm text-slate-500">
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Aguardando código...
                  </div>
                )}
              </div>
              {resultadoRef.current && !validarCodigoBoleto(resultadoRef.current) && (
                <div className="mt-4">
                  <div className="bg-amber-50 border border-amber-200 rounded-xl p-3 mb-3">
                    <p className="text-xs text-amber-600 font-medium">Código identificado (não reconhecido como boleto):</p>
                    <p className="text-sm font-mono text-amber-800 break-all mt-1">{resultadoRef.current}</p>
                  </div>
                  <button
                    onClick={() => {
                      onCodigoLido(resultadoRef.current!);
                      onFechar();
                    }}
                    className="w-full px-4 py-2.5 bg-[#0c4a6e] text-white rounded-xl text-sm font-medium"
                  >
                    Usar mesmo assim
                  </button>
                </div>
              )}
            </div>
          )}

          {(aba === 'imagem' || aba === 'pdf') && (
            <div className="space-y-4">
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

              {previewUrl && aba === 'imagem' && (
                <div className="rounded-xl overflow-hidden border border-slate-100">
                  <img src={previewUrl} alt="Preview" className="w-full" />
                </div>
              )}

              {paginasPDF.length > 0 && (
                <div>
                  <p className="text-sm font-medium text-slate-700 mb-1">
                    PDF com {paginasPDF.length} páginas — selecione o boleto que deseja ler:
                  </p>
                  <p className="text-xs text-slate-400 mb-3">
                    Toque na página que contém o boleto
                  </p>
                  <div className="grid grid-cols-2 gap-3">
                    {paginasPDF.map((src, i) => (
                      <button
                        key={i}
                        onClick={() => pdfRef && lerCodigoDaPagina(pdfRef, i + 1)}
                        disabled={lendo}
                        className="relative border-2 border-slate-200 rounded-xl overflow-hidden hover:border-[#0ea5e9] transition-colors disabled:opacity-50 group"
                      >
                        <img src={src} alt={`Página ${i + 1}`} className="w-full" />
                        <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors" />
                        <div className="absolute bottom-0 inset-x-0 bg-slate-900/70 text-white text-xs py-1 text-center">
                          Página {i + 1}
                        </div>
                      </button>
                    ))}
                  </div>
                  <button
                    onClick={() => { setPaginasPDF([]); setPdfRef(null); }}
                    className="mt-3 w-full text-sm text-slate-400 hover:text-slate-600 py-2"
                  >
                    Cancelar e escolher outro arquivo
                  </button>
                </div>
              )}

              {preparandoOCR && (
                <div className="flex items-center justify-center gap-2 text-sm text-slate-500">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Preparando OCR (primeira vez)...
                </div>
              )}

              {lendo && (
                <div className="flex items-center justify-center gap-2 text-sm text-slate-500">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Lendo código...
                </div>
              )}

              {resultadoRef.current && !validarCodigoBoleto(resultadoRef.current) && (
                <div className="bg-amber-50 border border-amber-200 rounded-xl p-3 mb-3">
                  <p className="text-xs text-amber-600 font-medium">Código identificado (não reconhecido como boleto):</p>
                  <p className="text-sm font-mono text-amber-800 break-all mt-1">{resultadoRef.current}</p>
                  <button
                    onClick={() => {
                      onCodigoLido(resultadoRef.current!);
                      onFechar();
                    }}
                    className="mt-2 w-full px-4 py-2.5 bg-[#0c4a6e] text-white rounded-xl text-sm font-medium"
                  >
                    Usar mesmo assim
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

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
  );
}
