import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { X } from 'lucide-react';
import api from '../../api/client';
import { PagamentoPix } from '../../types';

const schema = z.object({
  lojaId: z.string().min(1, 'Selecione uma loja'),
  fornecedor: z.string().min(2, 'Mínimo 2 caracteres'),
  valor: z.string().min(1, 'Informe o valor'),
  vencimento: z.string().min(1, 'Informe o vencimento'),
  chavePix: z.string().min(1, 'Informe a chave PIX'),
  tipoChave: z.string().min(1, 'Selecione o tipo de chave'),
  observacoes: z.string().optional(),
});

type FormData = z.infer<typeof schema>;

export function ModalPix({
                           pix,
                           lojaId,
                           onFechar,
                           onSalvo,
                         }: {
  pix: PagamentoPix | null;
  lojaId: string;
  onFechar: () => void;
  onSalvo: () => void;
}) {
  const [maxHeight, setMaxHeight] = useState('90vh');

  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: pix ? {
      lojaId: pix.lojaId,
      fornecedor: pix.fornecedor,
      valor: pix.valor.toString(),
      vencimento: pix.vencimento,
      chavePix: pix.chavePix,
      tipoChave: pix.tipoChave,
      observacoes: pix.observacoes || '',
    } : {
      lojaId,
      fornecedor: '',
      valor: '',
      vencimento: '',
      chavePix: '',
      tipoChave: 'CPF',
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
        chavePix: data.chavePix,
        tipoChave: data.tipoChave,
        observacoes: data.observacoes || null,
      };
      return pix
          ? api.put(`/pix/${pix.id}`, payload)
          : api.post('/pix', payload);
    },
    onSuccess: onSalvo,
  });

  useEffect(() => {
    document.body.style.overflow = 'hidden';
    const atualizarAltura = () => setMaxHeight(`${window.innerHeight - 20}px`);
    atualizarAltura();
    window.addEventListener('resize', atualizarAltura);
    return () => {
      document.body.style.overflow = '';
      window.removeEventListener('resize', atualizarAltura);
    };
  }, []);

  return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onFechar}>
        <div
            className="bg-white w-full sm:max-w-lg rounded-2xl animate-slide-in flex flex-col"
            style={{ maxHeight }}
            onClick={e => e.stopPropagation()}
        >
          {/* Cabeçalho fixo */}
          <div className="flex items-center justify-between px-6 pt-6 pb-0 shrink-0">
            <h2 className="text-lg font-bold text-slate-900">{pix ? 'Editar PIX' : 'Novo PIX'}</h2>
            <button onClick={onFechar} className="p-2 rounded-lg hover:bg-slate-100 text-slate-400">
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Área rolável */}
          <div className="flex-1 overflow-y-auto overscroll-contain px-6 min-h-0">
            <form id="pix-form" onSubmit={handleSubmit(d => mutation.mutate(d))} className="space-y-4 py-4">
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
                <label className="block text-sm font-medium text-slate-700 mb-1">Tipo de chave</label>
                <select {...register('tipoChave')}
                        className="w-full px-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]">
                  <option value="CPF">CPF</option>
                  <option value="CNPJ">CNPJ</option>
                  <option value="EMAIL">E-mail</option>
                  <option value="TELEFONE">Telefone</option>
                  <option value="ALEATORIA">Aleatória</option>
                  <option value="QRCODE">QR Code</option>
                </select>
                {errors.tipoChave && <p className="text-red-500 text-xs mt-1">{errors.tipoChave.message}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Chave PIX</label>
                <input {...register('chavePix')} placeholder="Chave PIX do recebedor"
                       className="w-full px-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9] font-mono" />
                {errors.chavePix && <p className="text-red-500 text-xs mt-1">{errors.chavePix.message}</p>}
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
            <button type="submit" form="pix-form" disabled={mutation.isPending}
                    className="flex-1 px-4 py-2.5 bg-[#0c4a6e] hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9] text-white rounded-xl text-sm font-medium transition-colors">
              {mutation.isPending ? 'Salvando...' : pix ? 'Salvar' : 'Criar PIX'}
            </button>
          </div>
        </div>
      </div>
  );
}