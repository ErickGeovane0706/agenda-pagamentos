import { useEffect, useId, useState } from 'react';
import { createPortal } from 'react-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { format, parseISO } from 'date-fns';
import { Plus, X } from 'lucide-react';
import api from '../../api/client';
import { ValorMonetario } from '../ValorMonetario';
import { gerarDatas } from '../../utils/recorrencia';
import { mensagemDoErro } from '../../utils/erroApi';

const MAX_DATAS = 36;

const schema = z.object({
  fornecedor: z.string().min(2, 'Mínimo 2 caracteres'),
  valor: z.string().min(1, 'Informe o valor'),
  chavePix: z.string().min(1, 'Informe a chave PIX'),
  tipoChave: z.string().min(1, 'Selecione o tipo de chave'),
  observacoes: z.string().optional(),
  dia: z.string(),
  inicio: z.string(),
  quantidade: z.string(),
});

type FormData = z.infer<typeof schema>;

const INPUT = 'w-full px-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]';
const LABEL = 'block text-sm font-medium text-slate-700 mb-1';

const dataBR = (iso: string) => format(parseISO(iso), 'dd/MM/yyyy');

/**
 * Cria vários PIX iguais de uma vez, um por data (ex.: aluguel de vários meses).
 * As datas saem da regra "todo dia X, a partir de, N vezes" e aparecem numa
 * prévia onde dá para tirar ou acrescentar datas antes de criar. Mudar a regra
 * refaz a lista.
 */
export function ModalPixRecorrente({ lojaId, onFechar, onSalvo }: {
  lojaId: string;
  onFechar: () => void;
  onSalvo: () => void;
}) {
  const id = useId();
  const hoje = new Date();
  const { register, handleSubmit, watch, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      fornecedor: '', valor: '', chavePix: '', tipoChave: 'CPF', observacoes: '',
      dia: String(hoje.getDate()), inicio: format(hoje, 'yyyy-MM'), quantidade: '12',
    },
  });

  const [dia, inicio, quantidade, valor] = watch(['dia', 'inicio', 'quantidade', 'valor']);
  const [datas, setDatas] = useState<string[]>([]);
  const [avulsa, setAvulsa] = useState('');

  useEffect(() => {
    setDatas(gerarDatas(Number(dia), inicio, Math.min(Number(quantidade), MAX_DATAS)));
  }, [dia, inicio, quantidade]);

  const adicionar = () => {
    if (!avulsa) return;
    setDatas(atual => [...new Set([...atual, avulsa])].sort().slice(0, MAX_DATAS));
    setAvulsa('');
  };

  const mutation = useMutation({
    mutationFn: (d: FormData) => api.post('/pix/recorrente', {
      lojaId,
      fornecedor: d.fornecedor,
      valor: parseFloat(d.valor),
      vencimentos: datas,
      chavePix: d.chavePix,
      tipoChave: d.tipoChave,
      observacoes: d.observacoes || null,
    }),
    onSuccess: onSalvo,
  });

  useEffect(() => {
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = ''; };
  }, []);

  const valorCada = parseFloat(valor) || 0;

  // Portal e z-[60]: o modal é alto (lista de datas) e, no celular, ficaria
  // embaixo da barra de navegação (z-50), escondendo o botão de criar.
  return createPortal(
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40" onClick={onFechar}>
      <div
        role="dialog"
        aria-labelledby={`${id}-titulo`}
        className="bg-white w-full sm:max-w-lg rounded-2xl animate-slide-in flex flex-col max-h-modal"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-6 pt-6 pb-0 shrink-0">
          <h2 id={`${id}-titulo`} className="text-lg font-bold text-slate-900">PIX recorrente</h2>
          <button onClick={onFechar} className="p-2 rounded-lg hover:bg-slate-100 text-slate-400">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto overscroll-contain px-6 min-h-0">
          <form id="pix-recorrente-form" onSubmit={handleSubmit(d => mutation.mutate(d))} className="space-y-4 py-4">
            <div>
              <label htmlFor={`${id}-fornecedor`} className={LABEL}>Fornecedor</label>
              <input id={`${id}-fornecedor`} {...register('fornecedor')} placeholder="Ex.: Aluguel" className={INPUT} />
              {errors.fornecedor && <p className="text-red-500 text-xs mt-1">{errors.fornecedor.message}</p>}
            </div>

            <div>
              <label htmlFor={`${id}-valor`} className={LABEL}>Valor de cada PIX (R$)</label>
              <input id={`${id}-valor`} {...register('valor')} type="number" step="0.01" min="0.01" placeholder="0,00" className={INPUT} />
              {errors.valor && <p className="text-red-500 text-xs mt-1">{errors.valor.message}</p>}
            </div>

            <div className="grid grid-cols-[2fr_3fr] gap-3">
              <div>
                <label htmlFor={`${id}-tipo`} className={LABEL}>Tipo de chave</label>
                <select id={`${id}-tipo`} {...register('tipoChave')} className={INPUT}>
                  <option value="CPF">CPF</option>
                  <option value="CNPJ">CNPJ</option>
                  <option value="EMAIL">E-mail</option>
                  <option value="TELEFONE">Telefone</option>
                  <option value="ALEATORIA">Aleatória</option>
                  <option value="QRCODE">QR Code</option>
                </select>
              </div>
              <div className="min-w-0">
                <label htmlFor={`${id}-chave`} className={LABEL}>Chave PIX</label>
                <input id={`${id}-chave`} {...register('chavePix')} placeholder="Chave do recebedor" className={`${INPUT} font-mono`} />
                {errors.chavePix && <p className="text-red-500 text-xs mt-1">{errors.chavePix.message}</p>}
              </div>
            </div>

            <fieldset className="rounded-xl bg-slate-50 border border-slate-100 p-3 space-y-3">
              <legend className="sr-only">Repetição</legend>
              <div className="grid grid-cols-[4.5rem_1fr_4.5rem] gap-2">
                <div className="min-w-0">
                  <label htmlFor={`${id}-dia`} className="block text-xs font-medium text-slate-600 mb-1">Todo dia</label>
                  <input id={`${id}-dia`} {...register('dia')} type="number" min="1" max="31" inputMode="numeric"
                         className={`${INPUT} px-3 bg-white`} />
                </div>
                <div className="min-w-0">
                  <label htmlFor={`${id}-inicio`} className="block text-xs font-medium text-slate-600 mb-1">A partir de</label>
                  <input id={`${id}-inicio`} {...register('inicio')} type="month" className={`${INPUT} px-3 bg-white`} />
                </div>
                <div className="min-w-0">
                  <label htmlFor={`${id}-qtd`} className="block text-xs font-medium text-slate-600 mb-1">Vezes</label>
                  <input id={`${id}-qtd`} {...register('quantidade')} type="number" min="1" max={MAX_DATAS} inputMode="numeric"
                         className={`${INPUT} px-3 bg-white`} />
                </div>
              </div>

              <div>
                <div className="flex items-baseline justify-between mb-2">
                  <span className="text-sm font-medium text-slate-700">{datas.length} PIX</span>
                  <span className="text-sm text-slate-500">
                    Total <ValorMonetario valor={valorCada * datas.length} className="font-semibold text-[#0c4a6e]" />
                  </span>
                </div>

                {datas.length === 0 ? (
                  <p className="text-sm text-slate-400 py-2">Nenhuma data. Ajuste a repetição ou adicione abaixo.</p>
                ) : (
                  <ul className="grid grid-cols-2 sm:grid-cols-3 gap-1.5">
                    {datas.map(d => (
                      <li key={d} className="flex items-center justify-between bg-white border border-slate-200 rounded-lg pl-3 pr-1 py-1">
                        <span className="text-sm text-slate-700 tabular-nums">{dataBR(d)}</span>
                        <button type="button" onClick={() => setDatas(atual => atual.filter(x => x !== d))}
                                aria-label={`Remover ${dataBR(d)}`}
                                className="p-1.5 rounded-md text-slate-400 hover:text-red-600 hover:bg-red-50">
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <div className="flex items-end gap-2">
                <div className="flex-1 min-w-0">
                  <label htmlFor={`${id}-avulsa`} className="block text-xs font-medium text-slate-600 mb-1">Outra data</label>
                  <input id={`${id}-avulsa`} type="date" value={avulsa} onChange={e => setAvulsa(e.target.value)}
                         className={`${INPUT} px-3 bg-white`} />
                </div>
                <button type="button" onClick={adicionar} disabled={!avulsa || datas.length >= MAX_DATAS}
                        className="flex items-center gap-1 px-3 py-2.5 rounded-lg border border-slate-200 bg-white text-sm font-medium text-[#0c4a6e] hover:bg-slate-50 disabled:text-slate-300">
                  <Plus className="w-4 h-4" />
                  Adicionar
                </button>
              </div>
            </fieldset>

            <div>
              <label htmlFor={`${id}-obs`} className={LABEL}>Observações (opcional)</label>
              <textarea id={`${id}-obs`} {...register('observacoes')} rows={2} className={INPUT} />
            </div>

            {mutation.isError && (
              <p className="text-red-500 text-sm">{mensagemDoErro(mutation.error, 'Não foi possível criar os PIX. Tente de novo.')}</p>
            )}
          </form>
        </div>

        <div className="flex gap-3 px-6 pb-6 pt-4 shrink-0">
          <button type="button" onClick={onFechar}
                  className="flex-1 px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium text-slate-600 hover:bg-slate-50 transition-colors">
            Cancelar
          </button>
          <button type="submit" form="pix-recorrente-form" disabled={mutation.isPending || datas.length === 0}
                  className="flex-1 px-4 py-2.5 bg-[#0c4a6e] hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9] text-white rounded-xl text-sm font-medium transition-colors">
            {mutation.isPending ? 'Criando...' : `Criar ${datas.length} PIX`}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
