import { Wallet } from 'lucide-react';
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
 * Botão "Pagar": copia o código (código de barras ou chave PIX) e aciona o
 * menu nativo de compartilhamento do sistema (Web Share API). Bancos como
 * Caixa e BB só aparecem nesse menu como alvo de arquivo (ex: PDF), não de
 * texto puro — por isso geramos um PDF simples com os dados do pagamento e
 * compartilhamos ele quando o navegador suporta `canShare` com arquivos.
 * Sem suporte a Web Share (ex: desktop), o código já foi copiado — só
 * avisa por toast.
 */
export function BotaoPagar({ tipo, codigo, className, fornecedor, valor, vencimento }: BotaoPagarProps) {
  const addToast = useToastStore(s => s.addToast);

  const pagar = async () => {
    await copiarTexto(codigo);

    if (!navigator.share) {
      addToast('success', 'Código copiado! Cole no app do seu banco.');
      return;
    }

    try {
      const pdf = await gerarPdfPagamento({ tipo, codigo, fornecedor, valor, vencimento });
      const arquivo = new File([pdf], 'boleto.pdf', { type: 'application/pdf' });

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

  return (
    <button
      type="button"
      onClick={pagar}
      title="Pagar"
      className={clsx(
        'flex items-center justify-center gap-2 rounded-xl font-medium',
        'bg-[#0c4a6e] hover:bg-[#0a3d5c] text-white transition-colors',
        className,
      )}
    >
      <Wallet className="w-4 h-4" />
      Pagar
    </button>
  );
}
