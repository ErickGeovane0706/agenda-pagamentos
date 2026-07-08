import { useState } from 'react';
import { Copy, Check } from 'lucide-react';
import { clsx } from 'clsx';
import { copiarTexto } from '../../utils/clipboard';
import { useToastStore } from '../../store/toastStore';

interface BotaoPagarProps {
  codigo: string;
  className?: string;
}

export function BotaoPagar({ codigo, className }: BotaoPagarProps) {
  const addToast = useToastStore(s => s.addToast);
  const [copiado, setCopiado] = useState(false);

  const copiar = async () => {
    await copiarTexto(codigo);
    setCopiado(true);
    addToast('success', 'Código copiado! Cole no app do seu banco.');
    setTimeout(() => setCopiado(false), 2000);
  };

  return (
    <button
      type="button"
      onClick={copiar}
      className={clsx(
        'flex items-center justify-center gap-2 rounded-xl font-medium',
        'bg-[#0c4a6e] hover:bg-[#0a3d5c] text-white transition-colors',
        className,
      )}
    >
      {copiado ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
      {copiado ? 'Copiado!' : 'Copiar'}
    </button>
  );
}
