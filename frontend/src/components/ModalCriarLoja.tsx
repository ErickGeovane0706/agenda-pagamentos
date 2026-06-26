import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { X } from 'lucide-react';
import api from '../api/client';
import { Loja } from '../types';

const schema = z.object({
  nome: z.string().min(2, 'Mínimo 2 caracteres'),
  cnpj: z.string().optional(),
  descricao: z.string().optional(),
  cor: z.string().default('#3B82F6'),
});

type FormData = z.infer<typeof schema>;

const CORES = [
  { hex: '#0c4a6e', nome: 'Petróleo' },
  { hex: '#065f46', nome: 'Verde' },
  { hex: '#7c3aed', nome: 'Roxo' },
  { hex: '#b91c1c', nome: 'Vermelho' },
  { hex: '#c2410c', nome: 'Laranja' },
  { hex: '#0e7490', nome: 'Teal' },
  { hex: '#1f2937', nome: 'Escuro' },
  { hex: '#92400e', nome: 'Marrom' },
];

export function ModalCriarLoja({
                                 loja,
                                 onFechar,
                                 onSalvo,
                               }: {
  loja: Loja | null;
  onFechar: () => void;
  onSalvo: () => void;
}) {
  const { register, handleSubmit, watch, setValue, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      nome: loja?.nome || '',
      cnpj: loja?.cnpj || '',
      descricao: loja?.descricao || '',
      cor: loja?.cor || '#3B82F6',
    },
  });

  const corSelecionada = watch('cor');

  const mutation = useMutation({
    mutationFn: (data: FormData) =>
        loja
            ? api.put(`/lojas/${loja.id}`, data)
            : api.post('/lojas', data),
    onSuccess: onSalvo,
  });

  return (
      <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/40" onClick={onFechar}>
        {/* ALTERAÇÃO 1: flex-col, overflow removido e max-h ajustado
      */}
        <div
            className="bg-white w-full sm:max-w-lg rounded-t-2xl sm:rounded-2xl animate-slide-in flex flex-col max-h-[90vh]"
            onClick={(e) => e.stopPropagation()}
        >

          {/* Cabeçalho Fixo (Não rola) */}
          <div className="flex items-center justify-between p-6 pb-4 shrink-0">
            <h2 className="text-lg font-bold text-slate-900">
              {loja ? 'Editar loja' : 'Nova loja'}
            </h2>
            <button onClick={onFechar} className="p-2 rounded-lg hover:bg-slate-100 text-slate-400">
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* ALTERAÇÃO 2: Form vira um flex-col e engloba o scroll apenas na parte dos inputs
        */}
          <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="flex flex-col overflow-hidden">

            {/* Corpo com Scroll */}
            <div className="px-6 pb-4 overflow-y-auto space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Nome da loja</label>
                <input
                    {...register('nome')}
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]"
                />
                {errors.nome && <p className="text-red-500 text-xs mt-1">{errors.nome.message}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">CNPJ (opcional)</label>
                <input
                    {...register('cnpj')}
                    placeholder="00.000.000/0000-00"
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Descrição (opcional)</label>
                <textarea
                    {...register('descricao')}
                    rows={2}
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-2">Cor</label>
                <div className="flex gap-2 flex-wrap">
                  {CORES.map((c) => (
                      <button
                          key={c.hex}
                          type="button"
                          onClick={() => setValue('cor', c.hex)}
                          className={`w-8 h-8 rounded-lg border-2 transition-all ${
                              corSelecionada === c.hex ? 'border-slate-900 scale-110' : 'border-transparent'
                          }`}
                          style={{ backgroundColor: c.hex }}
                          title={c.nome}
                      />
                  ))}
                </div>
              </div>
            </div>

            {/* ALTERAÇÃO 3: Rodapé Fixo.
            - border-t para visualmente separar do scroll.
            - pb-24 no mobile garante que a barra de navegação (Lojas, Agenda) não fique por cima.
            - sm:pb-6 volta ao padding normal no desktop.
          */}
            <div className="p-6 pt-4 shrink-0 border-t border-slate-100 bg-white pb-24 sm:pb-6 rounded-b-2xl">
              <div className="flex gap-3">
                <button
                    type="button"
                    onClick={onFechar}
                    className="flex-1 px-4 py-2.5 border border-slate-200 rounded-xl text-sm font-medium text-slate-600 hover:bg-slate-50 transition-colors"
                >
                  Cancelar
                </button>
                <button
                    type="submit"
                    disabled={mutation.isPending}
                    className="flex-1 px-4 py-2.5 bg-[#0c4a6e] hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9] text-white rounded-xl text-sm font-medium transition-colors"
                >
                  {mutation.isPending ? 'Salvando...' : loja ? 'Salvar' : 'Criar loja'}
                </button>
              </div>
            </div>
          </form>
        </div>
      </div>
  );
}