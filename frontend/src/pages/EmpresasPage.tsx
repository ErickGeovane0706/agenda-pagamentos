import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Building2, Plus, Pencil, Trash2, X, User, ChevronDown, ChevronRight, Shield
} from 'lucide-react';
import api from '../api/client';
import { useAuthStore } from '../store/authStore';
import { Usuario, Empresa } from '../types';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

const empresaSchema = z.object({
  nome: z.string().min(2, 'Mínimo 2 caracteres'),
});

const usuarioSchema = z.object({
  nome: z.string().min(2, 'Mínimo 2 caracteres'),
  email: z.string().email('E-mail inválido'),
  senha: z.string()
    .min(8, 'Mínimo 8 caracteres')
    .regex(/[A-Z]/, 'Deve conter letra maiúscula')
    .regex(/[a-z]/, 'Deve conter letra minúscula')
    .regex(/\d/, 'Deve conter um número'),
  perfil: z.enum(['ADMIN', 'OPERADOR', 'VIEWER']),
});

export default function EmpresasPage() {
  const { usuario } = useAuthStore();
  const queryClient = useQueryClient();
  const [expandida, setExpandida] = useState<string | null>(null);
  const [criandoEmpresa, setCriandoEmpresa] = useState(false);
  const [editandoEmpresa, setEditandoEmpresa] = useState<string | null>(null);
  const [criandoUsuario, setCriandoUsuario] = useState<string | null>(null);

  const { data: empresas = [] } = useQuery({
    queryKey: ['empresas'],
    queryFn: () => api.get('/empresas').then(r => r.data),
    enabled: usuario?.perfil === 'MASTER',
  });

  return (
    <div className="max-w-4xl mx-auto px-4 py-6">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <Building2 className="w-6 h-6 text-[#0c4a6e]" />
          <h1 className="text-xl font-bold text-slate-900">Empresas</h1>
        </div>
        <button
          onClick={() => setCriandoEmpresa(true)}
          className="flex items-center gap-2 bg-[#0c4a6e] hover:bg-[#0a3d5c] text-white text-sm font-medium px-4 py-2 rounded-xl transition-colors"
        >
          <Plus className="w-4 h-4" /> Nova empresa
        </button>
      </div>

      {criandoEmpresa && (
        <FormCriarEmpresa onSalvo={() => { setCriandoEmpresa(false); queryClient.invalidateQueries({ queryKey: ['empresas'] }); }} onCancelar={() => setCriandoEmpresa(false)} />
      )}

      <div className="space-y-3">
        {empresas.map((empresa: Empresa) => (
          <div key={empresa.id} className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
            <div className="flex items-center justify-between px-4 py-3 hover:bg-slate-50 transition-colors">
              <button
                onClick={() => setExpandida(expandida === empresa.id ? null : empresa.id)}
                className="flex items-center gap-3 flex-1 text-left"
              >
                {expandida === empresa.id ? <ChevronDown className="w-4 h-4 text-slate-400" /> : <ChevronRight className="w-4 h-4 text-slate-400" />}
                <Building2 className="w-5 h-5 text-slate-500" />
                <div>
                  <p className="text-sm font-medium text-slate-900">{empresa.nome}</p>
                  <p className="text-xs text-slate-400">{empresa.ativo ? 'Ativa' : 'Inativa'}</p>
                </div>
              </button>
              <div className="flex gap-2">
                <button
                  onClick={() => setCriandoUsuario(empresa.id)}
                  className="p-2 rounded-lg bg-[#e0f2fe] hover:bg-[#e0f2fe] text-[#0c4a6e]"
                  title="Adicionar usuário"
                >
                  <User className="w-4 h-4" />
                </button>
                <button
                  onClick={() => setEditandoEmpresa(empresa.id)}
                  className="p-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600"
                  title="Editar"
                >
                  <Pencil className="w-4 h-4" />
                </button>
                <button
                  onClick={() => {
                    if (confirm(`Excluir empresa "${empresa.nome}"?`))
                      api.delete(`/empresas/${empresa.id}`).then(() => queryClient.invalidateQueries({ queryKey: ['empresas'] }));
                  }}
                  className="p-2 rounded-lg bg-red-100 hover:bg-red-200 text-red-600"
                  title="Excluir"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>

            {editandoEmpresa === empresa.id && (
              <FormEditarEmpresa empresa={empresa} onSalvo={() => { setEditandoEmpresa(null); queryClient.invalidateQueries({ queryKey: ['empresas'] }); }} onCancelar={() => setEditandoEmpresa(null)} />
            )}

            {expandida === empresa.id && (
              <div className="border-t border-slate-100 bg-slate-50/50">
                {criandoUsuario === empresa.id && (
                  <FormCriarUsuario empresaId={empresa.id} empresaNome={empresa.nome} onSalvo={() => { setCriandoUsuario(null); queryClient.invalidateQueries({ queryKey: ['usuarios-por-empresa', empresa.id] }); }} onCancelar={() => setCriandoUsuario(null)} />
                )}
                <div className="px-4 py-3 space-y-2">
                  <UsuariosEmpresa empresaId={empresa.id} />
                </div>
              </div>
            )}
          </div>
        ))}

        {empresas.length === 0 && (
          <div className="text-center py-12 text-slate-400 text-sm">Nenhuma empresa cadastrada</div>
        )}
      </div>
    </div>
  );
}

function FormCriarEmpresa({ onSalvo, onCancelar }: { onSalvo: () => void; onCancelar: () => void }) {
  const { register, handleSubmit, formState: { errors } } = useForm<z.infer<typeof empresaSchema>>({
    resolver: zodResolver(empresaSchema),
  });
  const mutation = useMutation({
    mutationFn: (d: z.infer<typeof empresaSchema>) => api.post('/empresas', d),
    onSuccess: onSalvo,
  });

  return (
    <div className="bg-[#e0f2fe] rounded-2xl border border-blue-100 p-4 mb-3">
      <form onSubmit={handleSubmit(d => mutation.mutate(d))} className="flex gap-3 items-end">
        <div className="flex-1">
          <label className="block text-xs font-medium text-slate-600 mb-1">Nome da empresa</label>
          <input {...register('nome')} placeholder="Nome"
            className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]" />
          {errors.nome && <p className="text-red-500 text-xs mt-1">{errors.nome.message}</p>}
        </div>
        <button type="submit" disabled={mutation.isPending}
          className="px-4 py-2 bg-[#0c4a6e] text-white text-sm font-medium rounded-lg hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9]">
          {mutation.isPending ? 'Criando...' : 'Criar'}
        </button>
        <button type="button" onClick={onCancelar} className="p-2 text-slate-400 hover:text-slate-600">
          <X className="w-4 h-4" />
        </button>
      </form>
    </div>
  );
}

function FormEditarEmpresa({ empresa, onSalvo, onCancelar }: { empresa: Empresa; onSalvo: () => void; onCancelar: () => void }) {
  const { register, handleSubmit, formState: { errors } } = useForm({
    resolver: zodResolver(empresaSchema),
    defaultValues: { nome: empresa.nome },
  });
  const mutation = useMutation({
    mutationFn: (d: z.infer<typeof empresaSchema>) => api.put(`/empresas/${empresa.id}`, { ...d, ativo: empresa.ativo }),
    onSuccess: onSalvo,
  });

  return (
    <div className="border-t border-slate-100 bg-[#e0f2fe]/50 p-4">
      <form onSubmit={handleSubmit(d => mutation.mutate(d))} className="flex gap-3 items-end">
        <div className="flex-1">
          <label className="block text-xs font-medium text-slate-600 mb-1">Nome</label>
          <input {...register('nome')}
            className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]" />
          {errors.nome && <p className="text-red-500 text-xs mt-1">{errors.nome.message}</p>}
        </div>
        <button type="submit" disabled={mutation.isPending}
          className="px-4 py-2 bg-[#0c4a6e] text-white text-sm font-medium rounded-lg hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9]">
          Salvar
        </button>
        <button type="button" onClick={onCancelar} className="p-2 text-slate-400 hover:text-slate-600">
          <X className="w-4 h-4" />
        </button>
      </form>
    </div>
  );
}

function FormCriarUsuario({ empresaId, empresaNome, onSalvo, onCancelar }: { empresaId: string; empresaNome: string; onSalvo: () => void; onCancelar: () => void }) {
  const { register, handleSubmit, reset, formState: { errors } } = useForm<z.infer<typeof usuarioSchema>>({
    resolver: zodResolver(usuarioSchema),
    defaultValues: { perfil: 'OPERADOR' },
  });
  const mutation = useMutation({
    mutationFn: (d: z.infer<typeof usuarioSchema>) => api.post(`/usuarios/empresa/${empresaId}`, d),
    onSuccess: () => { onSalvo(); reset(); },
  });

  return (
    <div className="bg-[#e0f2fe] border-b border-blue-100 p-4">
      {mutation.isError && (
        <div className="mb-3 bg-red-50 border border-red-200 rounded-lg px-4 py-2.5 text-sm text-red-600">
          {(mutation.error as any)?.response?.data?.detail || 'Erro ao criar usuário'}
        </div>
      )}
      <p className="text-xs font-medium text-slate-500 mb-3">Novo usuário em <span className="text-slate-700">{empresaNome}</span></p>
      <form onSubmit={handleSubmit(d => mutation.mutate(d))} className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-slate-600 mb-1">Nome</label>
          <input {...register('nome')} placeholder="Nome"
            className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]" />
          {errors.nome && <p className="text-red-500 text-xs mt-1">{errors.nome.message}</p>}
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-600 mb-1">E-mail</label>
          <input {...register('email')} type="email" placeholder="email@exemplo.com"
            className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]" />
          {errors.email && <p className="text-red-500 text-xs mt-1">{errors.email.message}</p>}
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-600 mb-1">Senha</label>
                          <input {...register('senha')} type="password" placeholder="8+ chars, maiúscula, número"
            className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]" />
          {errors.senha && <p className="text-red-500 text-xs mt-1">{errors.senha.message}</p>}
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-600 mb-1">Perfil</label>
          <select {...register('perfil')}
            className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]">
            <option value="OPERADOR">Operador</option>
            <option value="ADMIN">Admin</option>
            <option value="VIEWER">Viewer</option>
          </select>
        </div>
        <div className="sm:col-span-2 flex gap-2">
          <button type="submit" disabled={mutation.isPending}
            className="px-4 py-2 bg-[#0c4a6e] text-white text-sm font-medium rounded-lg hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9]">
            {mutation.isPending ? 'Criando...' : 'Criar usuário'}
          </button>
          <button type="button" onClick={onCancelar} className="px-4 py-2 text-sm text-slate-600 hover:text-slate-800">
            Cancelar
          </button>
        </div>
      </form>
    </div>
  );
}

function UsuariosEmpresa({ empresaId }: { empresaId: string }) {
  const { data: usuarios = [] } = useQuery({
    queryKey: ['usuarios-por-empresa', empresaId],
    queryFn: () => api.get(`/usuarios/empresa/${empresaId}`).then(r => r.data),
  });

  if (usuarios.length === 0) return <p className="text-sm text-slate-400 text-center py-4">Nenhum usuário nesta empresa</p>;

  return (
    <div className="divide-y divide-slate-100">
      {usuarios.map((u: Usuario) => (
        <div key={u.id} className="flex items-center gap-3 py-2">
          <div className="w-8 h-8 bg-slate-100 rounded-full flex items-center justify-center">
            <User className="w-4 h-4 text-slate-500" />
          </div>
          <div className="flex-1">
            <p className="text-sm font-medium text-slate-900">{u.nome}</p>
            <p className="text-xs text-slate-400">{u.email}</p>
          </div>
          <span className="text-xs font-medium px-2 py-1 rounded-full bg-slate-100 text-slate-600 capitalize">
            {u.perfil.toLowerCase()}
          </span>
        </div>
      ))}
    </div>
  );
}
