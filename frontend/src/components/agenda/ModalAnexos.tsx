import { useRef, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import { X, FileText, Trash2, Upload, Loader2, Paperclip } from 'lucide-react';
import { clsx } from 'clsx';
import api from '../../api/client';
import type { Anexo } from '../../types';

export const MAXIMO_ANEXOS = 5;

/**
 * Arquivos de um lançamento: boleto, comprovante, pedido, nota fiscal... até
 * MAXIMO_ANEXOS. `onAlterado` serve para a lista de fora atualizar a contagem.
 */
export function ModalAnexos({
  recurso,
  lancamentoId,
  titulo,
  onFechar,
  onAlterado,
}: {
  recurso: 'boletos' | 'pix' | 'cheques';
  lancamentoId: string;
  titulo: string;
  onFechar: () => void;
  onAlterado: () => void;
}) {
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);
  const [erro, setErro] = useState<string | null>(null);
  const base = `/${recurso}/${lancamentoId}/anexos`;
  const queryKey = ['anexos', recurso, lancamentoId];

  const { data: anexos = [], isLoading } = useQuery<Anexo[]>({
    queryKey,
    queryFn: () => api.get(base).then(r => r.data),
  });

  const aoMudar = () => {
    queryClient.invalidateQueries({ queryKey });
    onAlterado();
  };
  const aoErrar = (e: unknown) => {
    const msg = (e as AxiosError<{ mensagem?: string }>).response?.data?.mensagem;
    setErro(msg || 'Não foi possível concluir. Tente de novo.');
  };

  const enviar = useMutation({
    // Um por vez: o limite é conferido no servidor a cada envio.
    mutationFn: async (arquivos: File[]) => {
      for (const arquivo of arquivos) {
        const form = new FormData();
        form.append('arquivo', arquivo);
        await api.post(base, form, { headers: { 'Content-Type': 'multipart/form-data' } });
      }
    },
    onSettled: aoMudar,
    onError: aoErrar,
  });

  const remover = useMutation({
    mutationFn: (anexoId: string) => api.delete(`${base}/${anexoId}`),
    onSuccess: aoMudar,
    onError: aoErrar,
  });

  const abrir = async (anexoId: string) => {
    try {
      const { data } = await api.get(`${base}/${anexoId}/url`);
      window.open(data.url, '_blank');
    } catch (e) {
      aoErrar(e);
    }
  };

  const onSelecionar = (e: React.ChangeEvent<HTMLInputElement>) => {
    const restantes = MAXIMO_ANEXOS - anexos.length;
    const arquivos = Array.from(e.target.files ?? []);
    e.target.value = '';
    if (arquivos.length === 0) return;
    setErro(arquivos.length > restantes
      ? `Só cabem mais ${restantes}. Os primeiros ${restantes} foram enviados.`
      : null);
    enviar.mutate(arquivos.slice(0, restantes));
  };

  const cheio = anexos.length >= MAXIMO_ANEXOS;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onFechar}>
      <div
        className="bg-white w-full sm:max-w-lg rounded-2xl animate-slide-in flex flex-col max-h-modal"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-6 pt-6 pb-0 shrink-0">
          <div className="min-w-0">
            <h2 className="text-lg font-bold text-slate-900">Arquivos</h2>
            <p className="text-sm text-slate-500 truncate">{titulo}</p>
          </div>
          <button onClick={onFechar} className="p-2 rounded-lg hover:bg-slate-100 text-slate-400">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto overscroll-contain px-6 py-4 min-h-0 space-y-2">
          <p className="text-xs text-slate-400">
            Boleto, comprovante, pedido, nota fiscal... até {MAXIMO_ANEXOS} arquivos (PDF ou foto).
          </p>

          {isLoading ? (
            <div className="flex justify-center py-6"><Loader2 className="w-5 h-5 animate-spin text-slate-400" /></div>
          ) : anexos.length === 0 ? (
            <p className="text-center text-sm text-slate-400 py-6">Nenhum arquivo anexado</p>
          ) : (
            anexos.map((anexo, i) => (
              <div key={anexo.id} className="flex items-center gap-2 border border-slate-100 rounded-xl p-2">
                <button
                  onClick={() => abrir(anexo.id)}
                  className="flex-1 min-w-0 flex items-center gap-3 text-left p-1 rounded-lg hover:bg-slate-50"
                >
                  <FileText className="w-5 h-5 text-[#0c4a6e] shrink-0" />
                  <span className="text-sm text-slate-700 truncate">{anexo.nomeArquivo || `Arquivo ${i + 1}`}</span>
                </button>
                <button
                  onClick={() => { if (confirm('Remover este arquivo?')) remover.mutate(anexo.id); }}
                  disabled={remover.isPending}
                  title="Remover arquivo"
                  className="flex items-center justify-center w-10 h-10 rounded-lg bg-red-100 hover:bg-red-200 text-red-600 shrink-0"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            ))
          )}

          {erro && <p className="text-red-500 text-sm">{erro}</p>}
        </div>

        <div className="px-6 pb-6 pt-2 shrink-0">
          <button
            onClick={() => { setErro(null); inputRef.current?.click(); }}
            disabled={cheio || enviar.isPending}
            className="w-full flex items-center justify-center gap-2 bg-[#0c4a6e] hover:bg-[#0a3d5c] disabled:opacity-50
                       text-white text-sm font-medium px-4 py-3 rounded-xl transition-colors"
          >
            {enviar.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
            {cheio ? `Limite de ${MAXIMO_ANEXOS} arquivos` : `Anexar arquivo (${anexos.length}/${MAXIMO_ANEXOS})`}
          </button>
          <input
            type="file"
            ref={inputRef}
            className="hidden"
            multiple
            onChange={onSelecionar}
            accept=".pdf,.jpg,.jpeg,.png,.webp"
          />
        </div>
      </div>
    </div>
  );
}

/** Clipe com a quantidade de arquivos; abre o ModalAnexos. */
export function BotaoAnexos({
  qtd,
  onClick,
  className,
}: {
  qtd: number;
  onClick: () => void;
  className: string;
}) {
  return (
    <button
      onClick={onClick}
      title={qtd > 0 ? `${qtd} arquivo(s)` : 'Anexar arquivos'}
      className={clsx(
        'relative flex items-center justify-center rounded-lg',
        qtd > 0 ? 'bg-[#e0f2fe] text-[#0c4a6e]' : 'bg-slate-100 hover:bg-slate-200 text-slate-500',
        className
      )}
    >
      <Paperclip className="w-4 h-4" />
      {qtd > 0 && (
        <span className="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 rounded-full bg-[#0c4a6e] text-white text-[11px] font-semibold leading-[18px] text-center">
          {qtd}
        </span>
      )}
    </button>
  );
}
