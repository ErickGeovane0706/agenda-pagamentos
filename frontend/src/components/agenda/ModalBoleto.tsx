import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { X, ScanLine } from 'lucide-react';
import api from '../../api/client';
import { Boleto } from '../../types';
import { ModalLeitorCodigo } from './ModalLeitorCodigo';

const schema = z.object({
  lojaId: z.string().min(1, 'Selecione uma loja'),
  fornecedor: z.string().min(2, 'Mínimo 2 caracteres'),
  valor: z.string().min(1, 'Informe o valor'),
  vencimento: z.string().min(1, 'Informe o vencimento'),
  codigoBarras: z.string().optional(),
  observacoes: z.string().optional(),
});

type FormData = z.infer<typeof schema>;

export function ModalBoleto({
  boleto,
  lojaId,
  onFechar,
  onSalvo,
}: {
  boleto: Boleto | null;
  lojaId: string;
  onFechar: () => void;
  onSalvo: () => void;
}) {
  const [mostrarLeitor, setMostrarLeitor] = useState(false);
  const [maxHeight, setMaxHeight] = useState('90vh');
  const { register, handleSubmit, setValue, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: boleto ? {
      lojaId: boleto.lojaId,
      fornecedor: boleto.fornecedor,
      valor: boleto.valor.toString(),
      vencimento: boleto.vencimento,
      codigoBarras: boleto.codigoBarras || '',
      observacoes: boleto.observacoes || '',
    } : {
      lojaId,
      fornecedor: '',
      valor: '',
      vencimento: '',
      codigoBarras: '',
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
        codigoBarras: data.codigoBarras || null,
        observacoes: data.observacoes || null,
      };
      return boleto
        ? api.put(`/boletos/${boleto.id}`, payload)
        : api.post('/boletos', payload);
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
    <>
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onFechar}>
        <div className="bg-white w-full sm:max-w-lg rounded-2xl animate-slide-in flex flex-col" style={{ maxHeight }} onClick={e => e.stopPropagation()}>
          <div className="flex items-center justify-between px-6 pt-6 pb-0 shrink-0">
            <h2 className="text-lg font-bold text-slate-900">{boleto ? 'Editar boleto' : 'Novo boleto'}</h2>
            <button onClick={onFechar} className="p-2 rounded-lg hover:bg-slate-100 text-slate-400">
              <X className="w-5 h-5" />
            </button>
          </div>
          
          <div className="flex-1 overflow-y-auto overscroll-contain px-6 min-h-0">
            <form id="boleto-form" onSubmit={handleSubmit(d => mutation.mutate(d))} className="space-y-4 py-4">
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
                <label className="block text-sm font-medium text-slate-700 mb-1">Código de barras (opcional)</label>
                <div className="flex gap-2">
                  <input {...register('codigoBarras')} placeholder="Código de barras do boleto"
                    className="flex-1 px-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9] font-mono" />
                  <button type="button" onClick={() => setMostrarLeitor(true)}
                    className="px-3 py-2.5 border border-slate-200 rounded-lg text-slate-500 hover:bg-slate-50 transition-colors"
                    title="Ler código de barras">
                    <ScanLine className="w-5 h-5" />
                  </button>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Observações (opcional)</label>
                <textarea {...register('observacoes')} rows={2}
                  className="w-full px-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]" />
              </div>
            </form>
          </div>

          <div className="flex gap-3 px-6 pb-6 pt-4 shrink-0">
            <button type="button" onClick={onFechar}
              className="flex-1 px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium text-slate-600 hover:bg-slate-50 transition-colors">
              Cancelar
            </button>
            <button type="submit" form="boleto-form"
              className="flex-1 px-4 py-2.5 bg-[#0c4a6e] hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9] text-white rounded-xl text-sm font-medium transition-colors">
              {mutation.isPending ? 'Salvando...' : boleto ? 'Salvar' : 'Criar boleto'}
            </button>
          </div>
        </div>
      </div>

      <ModalLeitorCodigo
        aberto={mostrarLeitor}
        onFechar={() => setMostrarLeitor(false)}
        onCodigoLido={(codigo) => {
          setValue('codigoBarras', codigo);
          setMostrarLeitor(false);
        }}
      />
    </>
  );
}
