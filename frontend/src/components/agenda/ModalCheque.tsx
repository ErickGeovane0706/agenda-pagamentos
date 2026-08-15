import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { X } from 'lucide-react';
import api from '../../api/client';
import { Cheque, Banco } from '../../types';

const schema = z.object({
  lojaId: z.string().min(1, 'Selecione uma loja'),
  fornecedor: z.string().min(2, 'Mínimo 2 caracteres'),
  valor: z.string().min(1, 'Informe o valor'),
  vencimento: z.string().min(1, 'Informe o vencimento'),
  bancoId: z.string().min(1, 'Selecione o banco'),
  numeroCheque: z.string().min(1, 'Informe o número do cheque'),
  observacoes: z.string().optional(),
});

type FormData = z.infer<typeof schema>;

export function ModalCheque({
                              cheque,
                              lojaId,
                              onFechar,
                              onSalvo,
                            }: {
  cheque: Cheque | null;
  lojaId: string;
  onFechar: () => void;
  onSalvo: () => void;
}) {
  const { data: bancos } = useQuery<Banco[]>({
    queryKey: ['bancos'],
    queryFn: () => api.get('/bancos').then(r => r.data),
  });

  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: cheque ? {
      lojaId: cheque.lojaId,
      fornecedor: cheque.fornecedor,
      valor: cheque.valor.toString(),
      vencimento: cheque.vencimento,
      bancoId: cheque.bancoId,
      numeroCheque: cheque.numeroCheque,
      observacoes: cheque.observacoes || '',
    } : {
      lojaId,
      fornecedor: '',
      valor: '',
      vencimento: '',
      bancoId: '',
      numeroCheque: '',
      observacoes: '',
    },
  });

  const mutation = useMutation({
    mutationFn: (data: FormData) => {
      const payload = {
        lojaId: data.lojaId,
        fornecedor: data.fornecedor,
        valor: parseFloat(data.valor),
        vencimento: data.vencimento,
        bancoId: data.bancoId,
        numeroCheque: data.numeroCheque,
        observacoes: data.observacoes || null,
      };
      return cheque
          ? api.put(`/cheques/${cheque.id}`, payload)
          : api.post('/cheques', payload);
    },
    onSuccess: onSalvo,
  });

  useEffect(() => {
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = ''; };
  }, []);

  return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onFechar}>
        <div
            className="bg-white w-full sm:max-w-lg rounded-2xl animate-slide-in flex flex-col max-h-modal"
            onClick={e => e.stopPropagation()}
        >
          {/* Cabeçalho fixo */}
          <div className="flex items-center justify-between px-6 pt-6 pb-0 shrink-0">
            <h2 className="text-lg font-bold text-slate-900">{cheque ? 'Editar cheque' : 'Novo cheque'}</h2>
            <button onClick={onFechar} className="p-2 rounded-lg hover:bg-slate-100 text-slate-400">
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Área rolável */}
          <div className="flex-1 overflow-y-auto overscroll-contain px-6 min-h-0">
            <form id="cheque-form" onSubmit={handleSubmit(d => mutation.mutate(d))} className="space-y-4 py-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Fornecedor</label>
                <input {...register('fornecedor')} placeholder="Nome do fornecedor"
                       className="w-full px-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]" />
                {errors.fornecedor && <p className="text-red-500 text-xs mt-1">{errors.fornecedor.message}</p>}
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Valor (R$)</label>
                  <input {...register('valor')} type="number" step="0.01" min="0.01" placeholder="0,00"
                         className="w-full px-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]" />
                  {errors.valor && <p className="text-red-500 text-xs mt-1">{errors.valor.message}</p>}
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Vencimento</label>
                  <input {...register('vencimento')} type="date"
                         className="w-full px-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]" />
                  {errors.vencimento && <p className="text-red-500 text-xs mt-1">{errors.vencimento.message}</p>}
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Banco</label>
                <select {...register('bancoId')}
                        className="w-full px-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]">
                  <option value="">Selecione um banco</option>
                  {bancos?.map(b => (
                      <option key={b.id} value={b.id}>{b.nome}</option>
                  ))}
                </select>
                {errors.bancoId && <p className="text-red-500 text-xs mt-1">{errors.bancoId.message}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Número do cheque</label>
                <input {...register('numeroCheque')} placeholder="Número do cheque"
                       className="w-full px-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9] font-mono" />
                {errors.numeroCheque && <p className="text-red-500 text-xs mt-1">{errors.numeroCheque.message}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Observações (opcional)</label>
                <textarea {...register('observacoes')} rows={2}
                          className="w-full px-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]" />
              </div>
            </form>
          </div>

          {/* Botões fixos no rodapé */}
          <div className="flex gap-3 px-6 pb-6 pt-4 shrink-0">
            <button type="button" onClick={onFechar}
                    className="flex-1 px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium text-slate-600 hover:bg-slate-50 transition-colors">
              Cancelar
            </button>
            <button type="submit" form="cheque-form" disabled={mutation.isPending}
                    className="flex-1 px-4 py-2.5 bg-[#0c4a6e] hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9] text-white rounded-xl text-sm font-medium transition-colors">
              {mutation.isPending ? 'Salvando...' : cheque ? 'Salvar' : 'Criar cheque'}
            </button>
          </div>
        </div>
      </div>
  );
}