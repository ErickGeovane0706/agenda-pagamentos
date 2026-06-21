import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Settings, User, Shield, Building2, Plus, Pencil, Trash2,
  Mail, X, AlertTriangle, MessageCircle, Store, Check
} from 'lucide-react';
import api from '../api/client';
import { useAuthStore } from '../store/authStore';
import { Usuario, Banco, Loja } from '../types';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

export default function ConfiguracoesPage() {
  const { usuario } = useAuthStore();
  const queryClient = useQueryClient();
  const [aba, setAba] = useState<'perfil' | 'bancos' | 'usuarios' | 'notificacoes'>('perfil');
  const [mostrarCriarBanco, setMostrarCriarBanco] = useState(false);
  const [mostrarCriarUsuario, setMostrarCriarUsuario] = useState(false);
  const [solicitouExclusao, setSolicitouExclusao] = useState(false);

  const { data: bancos = [] } = useQuery({
    queryKey: ['bancos', usuario?.empresaId],
    queryFn: () => api.get('/bancos').then(r => r.data),
    enabled: aba === 'bancos',
  });

  const { data: usuarios = [] } = useQuery({
    queryKey: ['usuarios', usuario?.empresaId],
    queryFn: () => api.get('/usuarios').then(r => r.data),
    enabled: aba === 'usuarios' && usuario?.perfil === 'ADMIN',
  });

  const { data: lojas = [] } = useQuery({
    queryKey: ['lojas', usuario?.empresaId],
    queryFn: () => api.get('/lojas').then(r => r.data),
    enabled: aba === 'notificacoes',
  });

  const exclusaoMutation = useMutation({
    mutationFn: () => api.post('/usuarios/minha-conta/solicitar-exclusao'),
    onSuccess: () => setSolicitouExclusao(true),
  });

  const tabs = [
    { id: 'perfil' as const, label: 'Perfil', icon: User },
    { id: 'notificacoes' as const, label: 'Notificações', icon: MessageCircle },
    { id: 'bancos' as const, label: 'Bancos', icon: Building2 },
    ...(usuario?.perfil === 'ADMIN' ? [{ id: 'usuarios' as const, label: 'Usuários', icon: Shield }] : []),
  ];

  return (
      <div className="max-w-3xl mx-auto px-4 py-6">
        <div className="flex items-center gap-3 mb-6">
          <Settings className="w-6 h-6 text-[#0c4a6e]" />
          <h1 className="text-xl font-bold text-slate-900">Configurações</h1>
        </div>

        <div className="flex gap-1 mb-6 border-b border-slate-200">
          {tabs.map(tab => (
              <button
                  key={tab.id}
                  onClick={() => setAba(tab.id)}
                  className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                      aba === tab.id
                          ? 'border-[#0c4a6e] text-[#0c4a6e]'
                          : 'border-transparent text-slate-500 hover:text-slate-700'
                  }`}
              >
                <tab.icon className="w-4 h-4" />
                {tab.label}
              </button>
          ))}
        </div>

        {aba === 'perfil' && <PerfilTab usuario={usuario} solicitouExclusao={solicitouExclusao} onSolicitarExclusao={() => exclusaoMutation.mutate()} />}
        {aba === 'notificacoes' && <NotificacoesTab lojas={lojas} />}
        {aba === 'bancos' && <BancosTab bancos={bancos} mostrarCriar={mostrarCriarBanco} setMostrarCriar={setMostrarCriarBanco} />}
        {aba === 'usuarios' && <UsuariosTab usuarios={usuarios} mostrarCriar={mostrarCriarUsuario} setMostrarCriar={setMostrarCriarUsuario} />}
      </div>
  );
}

function PerfilTab({ usuario, solicitouExclusao, onSolicitarExclusao }: {
  usuario: Usuario | null;
  solicitouExclusao: boolean;
  onSolicitarExclusao: () => void;
}) {
  return (
      <div className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
        <div className="p-6 border-b border-slate-100">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 bg-[#e0f2fe] rounded-2xl flex items-center justify-center">
              <User className="w-7 h-7 text-[#0c4a6e]" />
            </div>
            <div>
              <p className="font-semibold text-slate-900 text-lg">{usuario?.nome}</p>
              <div className="flex items-center gap-3 mt-0.5 text-sm text-slate-500">
                <span className="flex items-center gap-1"><Mail className="w-3.5 h-3.5" />{usuario?.email}</span>
              </div>
            </div>
          </div>
        </div>

        <div className="p-6 border-b border-slate-100">
          <div className="flex items-center gap-3">
            <Shield className="w-5 h-5 text-slate-400" />
            <div>
              <p className="font-medium text-slate-900">Perfil</p>
              <p className="text-sm text-slate-500 capitalize">{usuario?.perfil?.toLowerCase()}</p>
            </div>
          </div>
        </div>

        <div className="p-6">
          <h3 className="text-sm font-medium text-slate-700 mb-3">Dados pessoais</h3>
          <div className="space-y-2 text-sm text-slate-600">
            <p><span className="text-slate-400">Solicitar exclusão:</span></p>
            {solicitouExclusao ? (
                <div className="flex items-center gap-2 text-amber-600 bg-amber-50 px-4 py-3 rounded-lg">
                  <AlertTriangle className="w-4 h-4" />
                  <span className="text-sm">Solicitação enviada. Processaremos em até 30 dias.</span>
                </div>
            ) : (
                <button
                    onClick={() => { if (confirm('Tem certeza? Esta ação solicitará a exclusão de todos os dados da sua empresa.')) onSolicitarExclusao(); }}
                    className="text-red-600 hover:text-red-700 text-sm font-medium hover:underline"
                >
                  Solicitar exclusão de dados
                </button>
            )}
          </div>
        </div>
      </div>
  );
}

const bancoSchema = z.object({
  nome: z.string().min(2, 'Mínimo 2 caracteres'),
  codigo: z.string().optional(),
});

function BancosTab({ bancos, mostrarCriar, setMostrarCriar }: {
  bancos: Banco[];
  mostrarCriar: boolean;
  setMostrarCriar: (v: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const { register, handleSubmit, reset, formState: { errors } } = useForm<z.infer<typeof bancoSchema>>({
    resolver: zodResolver(bancoSchema),
  });

  const criarMutation = useMutation({
    mutationFn: (d: z.infer<typeof bancoSchema>) => api.post('/bancos', d),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bancos'] });
      setMostrarCriar(false);
      reset();
    },
  });

  return (
      <div className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
        <div className="p-4 border-b border-slate-100 flex items-center justify-between">
          <h2 className="font-semibold text-slate-900">Bancos cadastrados</h2>
          <button onClick={() => setMostrarCriar(true)}
                  className="flex items-center gap-1.5 text-sm font-medium text-[#0c4a6e] hover:text-blue-800">
            <Plus className="w-4 h-4" /> Novo banco
          </button>
        </div>

        {mostrarCriar && (
            <div className="p-4 bg-[#e0f2fe] border-b border-blue-100">
              <form onSubmit={handleSubmit(d => criarMutation.mutate(d))} className="flex gap-3 items-end">
                <div className="flex-1">
                  <label className="block text-xs font-medium text-slate-600 mb-1">Nome</label>
                  <input {...register('nome')} placeholder="Nome do banco"
                         className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]" />
                  {errors.nome && <p className="text-red-500 text-xs mt-1">{errors.nome.message}</p>}
                </div>
                <div className="w-24">
                  <label className="block text-xs font-medium text-slate-600 mb-1">Código</label>
                  <input {...register('codigo')} placeholder="001"
                         className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]" />
                </div>
                <button type="submit" disabled={criarMutation.isPending}
                        className="px-4 py-2 bg-[#0c4a6e] text-white text-sm font-medium rounded-lg hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9]">
                  Salvar
                </button>
                <button type="button" onClick={() => { setMostrarCriar(false); reset(); }}
                        className="p-2 text-slate-400 hover:text-slate-600">
                  <X className="w-4 h-4" />
                </button>
              </form>
            </div>
        )}

        {bancos.length === 0 ? (
            <div className="text-center py-8 text-slate-400 text-sm">Nenhum banco cadastrado</div>
        ) : (
            <div className="divide-y divide-slate-50">
              {bancos.map((banco: Banco) => (
                  <div key={banco.id} className="flex items-center gap-3 px-4 py-3">
                    <Building2 className="w-5 h-5 text-slate-400" />
                    <div className="flex-1">
                      <p className="text-sm font-medium text-slate-900">{banco.nome}</p>
                      {banco.codigo && <p className="text-xs text-slate-400">Código: {banco.codigo}</p>}
                    </div>
                  </div>
              ))}
            </div>
        )}
      </div>
  );
}

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

function UsuariosTab({ usuarios, mostrarCriar, setMostrarCriar }: {
  usuarios: Usuario[];
  mostrarCriar: boolean;
  setMostrarCriar: (v: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const { register, handleSubmit, reset, formState: { errors } } = useForm<z.infer<typeof usuarioSchema>>({
    resolver: zodResolver(usuarioSchema),
    defaultValues: { perfil: 'OPERADOR' },
  });

  const criarMutation = useMutation({
    mutationFn: (d: z.infer<typeof usuarioSchema>) => api.post('/usuarios', d),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['usuarios'] });
      setMostrarCriar(false);
      reset();
    },
  });

  return (
      <div className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
        <div className="p-4 border-b border-slate-100 flex items-center justify-between">
          <h2 className="font-semibold text-slate-900">Usuários da empresa</h2>
          <button onClick={() => setMostrarCriar(true)}
                  className="flex items-center gap-1.5 text-sm font-medium text-[#0c4a6e] hover:text-blue-800">
            <Plus className="w-4 h-4" /> Novo usuário
          </button>
        </div>

        {mostrarCriar && (
            <div className="p-4 bg-[#e0f2fe] border-b border-blue-100">
              <form onSubmit={handleSubmit(d => criarMutation.mutate(d))} className="space-y-3">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-slate-600 mb-1">Nome</label>
                    <input {...register('nome')} placeholder="Nome completo"
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
                </div>
                <div className="flex gap-2">
                  <button type="submit" disabled={criarMutation.isPending}
                          className="px-4 py-2 bg-[#0c4a6e] text-white text-sm font-medium rounded-lg hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9]">
                    {criarMutation.isPending ? 'Criando...' : 'Criar usuário'}
                  </button>
                  <button type="button" onClick={() => { setMostrarCriar(false); reset(); }}
                          className="px-4 py-2 text-sm text-slate-600 hover:text-slate-800">
                    Cancelar
                  </button>
                </div>
              </form>
            </div>
        )}

        {usuarios.length === 0 ? (
            <div className="text-center py-8 text-slate-400 text-sm">Nenhum usuário cadastrado</div>
        ) : (
            <div className="divide-y divide-slate-50">
              {usuarios.map((u: Usuario) => (
                  <div key={u.id} className="flex items-center gap-3 px-4 py-3">
                    <div className="w-9 h-9 bg-slate-100 rounded-full flex items-center justify-center">
                      <User className="w-4 h-4 text-slate-500" />
                    </div>
                    <div className="flex-1">
                      <p className="text-sm font-medium text-slate-900">{u.nome}</p>
                      <p className="text-xs text-slate-400">{u.email}</p>
                    </div>
                    <span className="text-xs font-medium px-2.5 py-1 rounded-full bg-slate-100 text-slate-600 capitalize">
                {u.perfil.toLowerCase()}
              </span>
                  </div>
              ))}
            </div>
        )}
      </div>
  );
}

interface PreferenciaNotificacao {
  id: string | null;
  telefoneWhatsapp: string | null;
  whatsappAtivo: boolean;
  horario1: string | null;
  horario2: string | null;
  horario3: string | null;
  horario4: string | null;
  lojaIds: string[];
}

const notificacaoSchema = z.object({
  telefoneWhatsapp: z.string()
      .regex(/^\+?[0-9]{10,15}$/, 'Use o formato com DDI e DDD, ex: 5583999999999')
      .or(z.literal('')),
  whatsappAtivo: z.boolean(),
  horario1: z.string().optional().or(z.literal('')),
  horario2: z.string().optional().or(z.literal('')),
  horario3: z.string().optional().or(z.literal('')),
  horario4: z.string().optional().or(z.literal('')),
  lojaIds: z.array(z.string()),
});

type NotificacaoForm = z.infer<typeof notificacaoSchema>;

function NotificacoesTab({ lojas }: { lojas: Loja[] }) {
  const queryClient = useQueryClient();

  const { data: preferencia, isLoading } = useQuery<PreferenciaNotificacao>({
    queryKey: ['preferencia-notificacao'],
    queryFn: () => api.get('/preferencias-notificacao/minha').then(r => r.data),
  });

  const { register, handleSubmit, control, watch, reset, formState: { errors, isDirty } } =
      useForm<NotificacaoForm>({
        resolver: zodResolver(notificacaoSchema),
        values: preferencia ? {
          telefoneWhatsapp: preferencia.telefoneWhatsapp ?? '',
          whatsappAtivo: preferencia.whatsappAtivo,
          horario1: preferencia.horario1?.slice(0, 5) ?? '',
          horario2: preferencia.horario2?.slice(0, 5) ?? '',
          horario3: preferencia.horario3?.slice(0, 5) ?? '',
          horario4: preferencia.horario4?.slice(0, 5) ?? '',
          lojaIds: preferencia.lojaIds ?? [],
        } : undefined,
      });

  const whatsappAtivo = watch('whatsappAtivo');
  const lojaIdsSelecionadas = watch('lojaIds') ?? [];

  const salvarMutation = useMutation({
    mutationFn: (d: NotificacaoForm) => api.put('/preferencias-notificacao/minha', {
      telefoneWhatsapp: d.telefoneWhatsapp || null,
      whatsappAtivo: d.whatsappAtivo,
      horario1: d.horario1 || null,
      horario2: d.horario2 || null,
      horario3: d.horario3 || null,
      horario4: d.horario4 || null,
      lojaIds: d.lojaIds,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['preferencia-notificacao'] });
    },
  });

  const toggleLoja = (lojaId: string, atuais: string[], onChange: (v: string[]) => void) => {
    if (atuais.includes(lojaId)) {
      onChange(atuais.filter(id => id !== lojaId));
    } else {
      onChange([...atuais, lojaId]);
    }
  };

  if (isLoading) {
    return (
        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-8 text-center text-slate-400 text-sm">
          Carregando preferências...
        </div>
    );
  }

  return (
      <form onSubmit={handleSubmit(d => salvarMutation.mutate(d))} className="space-y-4">
        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
          <div className="p-4 border-b border-slate-100 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 bg-emerald-50 rounded-xl flex items-center justify-center">
                <MessageCircle className="w-4.5 h-4.5 text-emerald-600" />
              </div>
              <div>
                <h2 className="font-semibold text-slate-900">Lembretes por WhatsApp</h2>
                <p className="text-xs text-slate-400">Receba avisos de boletos, PIX e cheques pendentes</p>
              </div>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" className="sr-only peer" {...register('whatsappAtivo')} />
              <div className="w-11 h-6 bg-slate-200 peer-checked:bg-[#0c4a6e] rounded-full transition-colors
                            after:content-[''] after:absolute after:top-0.5 after:left-0.5 after:bg-white
                            after:rounded-full after:h-5 after:w-5 after:transition-all
                            peer-checked:after:translate-x-5"></div>
            </label>
          </div>

          {whatsappAtivo && (
              <div className="p-4 space-y-5">
                <div>
                  <label className="block text-xs font-medium text-slate-600 mb-1">
                    Número do WhatsApp (com DDI e DDD)
                  </label>
                  <input {...register('telefoneWhatsapp')} placeholder="5583999999999"
                         className="w-full sm:w-72 px-3 py-2 border border-slate-200 rounded-lg text-sm
                           focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]" />
                  {errors.telefoneWhatsapp && (
                      <p className="text-red-500 text-xs mt-1">{errors.telefoneWhatsapp.message}</p>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-medium text-slate-600 mb-2">
                    Horários de envio (até 4 por dia)
                  </label>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                    {(['horario1', 'horario2', 'horario3', 'horario4'] as const).map((campo, i) => (
                        <div key={campo}>
                          <span className="block text-[11px] text-slate-400 mb-1">{i + 1}º horário</span>
                          <input type="time" {...register(campo)}
                                 className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm
                                 focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]" />
                        </div>
                    ))}
                  </div>
                  <p className="text-xs text-slate-400 mt-2">
                    Deixe em branco os horários que não quiser usar.
                  </p>
                </div>

                <div>
                  <label className="block text-xs font-medium text-slate-600 mb-2">
                    Lojas que entram no lembrete
                  </label>
                  <Controller
                      control={control}
                      name="lojaIds"
                      render={({ field }) => (
                          <div className="space-y-1.5">
                            {lojas.length === 0 ? (
                                <p className="text-sm text-slate-400">Nenhuma loja cadastrada ainda.</p>
                            ) : (
                                lojas.map(loja => {
                                  const selecionada = (field.value ?? []).includes(loja.id);
                                  return (
                                      <button
                                          type="button"
                                          key={loja.id}
                                          onClick={() => toggleLoja(loja.id, field.value ?? [], field.onChange)}
                                          className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg border text-left transition-colors ${
                                              selecionada
                                                  ? 'border-[#0ea5e9] bg-[#e0f2fe]'
                                                  : 'border-slate-200 hover:bg-slate-50'
                                          }`}
                                      >
                                        <div className={`w-5 h-5 rounded-md flex items-center justify-center border-2 flex-shrink-0 ${
                                            selecionada ? 'bg-[#0c4a6e] border-[#0c4a6e]' : 'border-slate-300'
                                        }`}>
                                          {selecionada && <Check className="w-3.5 h-3.5 text-white" />}
                                        </div>
                                        <Store className="w-4 h-4 text-slate-400 flex-shrink-0" style={{ color: loja.cor }} />
                                        <span className="text-sm text-slate-700">{loja.nome}</span>
                                      </button>
                                  );
                                })
                            )}
                          </div>
                      )}
                  />
                  {lojaIdsSelecionadas.length === 0 && whatsappAtivo && (
                      <p className="text-amber-600 text-xs mt-2 flex items-center gap-1">
                        <AlertTriangle className="w-3.5 h-3.5" />
                        Selecione ao menos uma loja para receber os lembretes.
                      </p>
                  )}
                </div>
              </div>
          )}
        </div>

        <div className="flex items-center gap-3">
          <button type="submit" disabled={salvarMutation.isPending || !isDirty}
                  className="px-4 py-2 bg-[#0c4a6e] text-white text-sm font-medium rounded-lg
                     hover:bg-[#0a3d5c] disabled:bg-slate-300 disabled:cursor-not-allowed">
            {salvarMutation.isPending ? 'Salvando...' : 'Salvar preferências'}
          </button>
          {salvarMutation.isSuccess && (
              <span className="text-sm text-emerald-600 flex items-center gap-1">
            <Check className="w-4 h-4" /> Salvo
          </span>
          )}
        </div>
      </form>
  );
}