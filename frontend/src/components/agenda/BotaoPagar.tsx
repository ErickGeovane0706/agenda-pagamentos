import { Wallet } from 'lucide-react';
import { clsx } from 'clsx';
import { copiarTexto } from '../../utils/clipboard';
import { useToastStore } from '../../store/toastStore';

/**
 * Botão "Pagar": copia o código (código de barras ou chave PIX) e aciona o
 * menu nativo de compartilhamento do sistema (Web Share API), que já lista
 * os apps de banco instalados como opção de "Abrir com". Sem suporte a Web
 * Share (ex: desktop), o código já foi copiado — só avisa por toast.
 */
export function BotaoPagar({ codigo, className }: { codigo: string; className?: string }) {
  const addToast = useToastStore(s => s.addToast);

  const pagar = async () => {
    await copiarTexto(codigo);

    if (!navigator.share) {
      addToast('success', 'Código copiado! Cole no app do seu banco.');
      return;
    }

    try {
      await navigator.share({ text: codigo });
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
