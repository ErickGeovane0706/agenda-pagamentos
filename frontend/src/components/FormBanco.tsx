import { useId } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import api from '../api/client';
import { Banco } from '../types';

const schema = z.object({
  nome: z.string().min(2, 'Mínimo 2 caracteres'),
  codigo: z.string().optional(),
  agencia: z.string().max(10, 'Máximo 10 caracteres').optional(),
  conta: z.string().max(20, 'Máximo 20 caracteres').optional(),
});

type FormData = z.infer<typeof schema>;

const INPUT = 'w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]';

/**
 * Cria (banco = null) ou edita um banco. Agência e conta são opcionais — é
 * pela edição que o cliente completa os bancos cadastrados antes de existirem.
 */
export function FormBanco({ banco, onFechar, onSalvo }: {
  banco: Banco | null;
  onFechar: () => void;
  onSalvo: () => void;
}) {
  const id = useId();
  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      nome: banco?.nome ?? '',
      codigo: banco?.codigo ?? '',
      agencia: banco?.agencia ?? '',
      conta: banco?.conta ?? '',
    },
  });

  const mutation = useMutation({
    mutationFn: (d: FormData) => banco ? api.put(`/bancos/${banco.id}`, d) : api.post('/bancos', d),
    onSuccess: onSalvo,
  });

  return (
    <form onSubmit={handleSubmit(d => mutation.mutate(d))} className="space-y-3">
      <div>
        <label htmlFor={`${id}-nome`} className="block text-xs font-medium text-slate-600 mb-1">Nome</label>
        <input id={`${id}-nome`} {...register('nome')} placeholder="Nome do banco" className={INPUT} />
        {errors.nome && <p className="text-red-500 text-xs mt-1">{errors.nome.message}</p>}
      </div>

      <div className="grid grid-cols-[1fr_1fr_1.4fr] gap-2">
        <div className="min-w-0">
          <label htmlFor={`${id}-codigo`} className="block text-xs font-medium text-slate-600 mb-1">Código</label>
          <input id={`${id}-codigo`} {...register('codigo')} placeholder="001" className={INPUT} />
        </div>
        <div className="min-w-0">
          <label htmlFor={`${id}-agencia`} className="block text-xs font-medium text-slate-600 mb-1">Agência</label>
          <input id={`${id}-agencia`} {...register('agencia')} placeholder="1234" inputMode="numeric" className={INPUT} />
        </div>
        <div className="min-w-0">
          <label htmlFor={`${id}-conta`} className="block text-xs font-medium text-slate-600 mb-1">Conta</label>
          <input id={`${id}-conta`} {...register('conta')} placeholder="56789-0" className={INPUT} />
        </div>
      </div>
      {(errors.agencia || errors.conta) && (
        <p className="text-red-500 text-xs">{errors.agencia?.message ?? errors.conta?.message}</p>
      )}

      {banco && (
        <p className="text-xs text-slate-500">
          A alteração vale também para os cheques já lançados com este banco.
          Se mudou de conta, cadastre um banco novo.
        </p>
      )}
      {mutation.isError && <p className="text-red-500 text-xs">Não foi possível salvar. Tente de novo.</p>}

      <div className="flex justify-end gap-2">
        <button type="button" onClick={onFechar}
                className="px-4 py-2 border border-slate-200 bg-white rounded-lg text-sm font-medium text-slate-600 hover:bg-slate-50">
          Cancelar
        </button>
        <button type="submit" disabled={mutation.isPending}
                className="px-4 py-2 bg-[#0c4a6e] text-white text-sm font-medium rounded-lg hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9]">
          Salvar
        </button>
      </div>
    </form>
  );
}
