import { Wallet, Download } from 'lucide-react';
import { clsx } from 'clsx';
import { copiarTexto } from '../../utils/clipboard';
import { useToastStore } from '../../store/toastStore';
import { gerarPdfPagamento } from '../../utils/gerarPdfPagamento';

interface BotaoPagarProps {
  tipo: 'boleto' | 'pix';
  codigo: string;
  className?: string;
  fornecedor?: string;
  valor?: number;
  vencimento?: string;
}

/**
 * Dois caminhos pra pagar: "Pagar" (compartilhar via Web Share, ACTION_SEND)
 * e o ícone de baixar (download real do PDF, que no Android dispara a
 * notificação "Download concluído" — abrir nela aciona ACTION_VIEW). Alguns
 * bancos (ex: BB) só se registram pra ACTION_VIEW, não pra ACTION_SEND, e
 * por isso não aparecem no menu de compartilhamento mesmo com o PDF certo —
 * daí a necessidade dos dois caminhos em paralelo. Em ambos, o código já é
 * copiado primeiro, como fallback garantido.
 */
export function BotaoPagar({ tipo, codigo, className, fornecedor, valor, vencimento }: BotaoPagarProps) {
  const addToast = useToastStore(s => s.addToast);
  const nomeArquivo = tipo === 'boleto' ? 'boleto.pdf' : 'pix.pdf';

  const gerarArquivo = async () => {
    const pdf = await gerarPdfPagamento({ tipo, codigo, fornecedor, valor, vencimento });
    return new File([pdf], nomeArquivo, { type: 'application/pdf' });
  };

  const compartilhar = async () => {
    await copiarTexto(codigo);

    if (!navigator.share) {
      addToast('success', 'Código copiado! Cole no app do seu banco.');
      return;
    }

    try {
      const arquivo = await gerarArquivo();
      if (navigator.canShare?.({ files: [arquivo] })) {
        await navigator.share({ files: [arquivo], title: 'Boleto para pagamento' });
      } else {
        await navigator.share({ text: codigo });
      }
    } catch (err: any) {
      if (err?.name !== 'AbortError') {
        addToast('info', 'Código copiado. Cole no app do seu banco.');
      }
    }
  };

  const baixar = async () => {
    await copiarTexto(codigo);

    const arquivo = await gerarArquivo();
    const url = URL.createObjectURL(arquivo);
    const link = document.createElement('a');
    link.href = url;
    link.download = nomeArquivo;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    // Libera o Blob URL depois de dar tempo do navegador iniciar o download.
    setTimeout(() => URL.revokeObjectURL(url), 10000);

    addToast('success', 'Código copiado e boleto baixado — toque na notificação de download pra abrir com o app do banco.');
  };

  return (
    <div className="flex items-center gap-1.5">
      <button
        type="button"
        onClick={compartilhar}
        title="Compartilhar"
        className={clsx(
          'flex items-center justify-center gap-2 rounded-xl font-medium',
          'bg-[#0c4a6e] hover:bg-[#0a3d5c] text-white transition-colors',
          className,
        )}
      >
        <Wallet className="w-4 h-4" />
        Pagar
      </button>
      <button
        type="button"
        onClick={baixar}
        title="Baixar (abrir com o app do banco)"
        className="p-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-500 transition-colors shrink-0"
      >
        <Download className="w-4 h-4" />
      </button>
    </div>
  );
}
