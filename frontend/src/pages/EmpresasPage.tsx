import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Building2, Plus, Pencil, Trash2, X, User, ChevronDown, ChevronRight, Shield, CreditCard, AlertTriangle
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

/** O que o GET /assinaturas devolve (AssinaturaDTO). Só o MASTER enxerga. */
interface Assinatura {
  id: string;
  empresaId: string;
  empresaNome: string;
  status: 'TRIAL' | 'ATIVA' | 'INADIMPLENTE' | 'CANCELADA';
  lojasContratadas: number;
  vigenteAte: string | null;
  gatewayCustomerId: string | null;
  /** Há subscription no gateway? É o que separa alteração que cobra de alteração que não cobra. */
  assinaturaIniciada: boolean;
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

  // Uma consulta só para todas as assinaturas, e não uma por empresa expandida:
  // o endpoint já devolve a lista inteira e o painel costuma abrir várias linhas.
  const { data: assinaturas = [] } = useQuery<Assinatura[]>({
    queryKey: ['assinaturas'],
    queryFn: () => api.get('/assinaturas').then(r => r.data),
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
                  <AssinaturaEmpresa assinatura={assinaturas.find(a => a.empresaId === empresa.id)} />
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

const dataBR = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString('pt-BR');

const CORES_STATUS: Record<Assinatura['status'], string> = {
  TRIAL: 'bg-amber-100 text-amber-800',
  ATIVA: 'bg-emerald-100 text-emerald-800',
  INADIMPLENTE: 'bg-red-100 text-red-800',
  CANCELADA: 'bg-slate-200 text-slate-600',
};

/**
 * Assinatura da empresa no painel do MASTER: mostrar e editar.
 *
 * Existe porque o backend expunha `GET /assinaturas` e `PUT /assinaturas/{id}`
 * desde 23/07 e nenhuma tela chamava — estender o prazo de um cliente só dava
 * por curl ou direto no banco, e o banco pula a propagação ao Asaas.
 */
function AssinaturaEmpresa({ assinatura }: { assinatura: Assinatura | undefined }) {
  const [editando, setEditando] = useState(false);

  if (!assinatura) {
    return <p className="text-xs text-slate-400">Sem assinatura registrada</p>;
  }

  return (
    <div className="rounded-xl border border-slate-100 bg-white px-3 py-2.5">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 flex-wrap">
          <CreditCard className="w-4 h-4 text-slate-400" />
          <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${CORES_STATUS[assinatura.status]}`}>
            {assinatura.status}
          </span>
          <span className="text-xs text-slate-500">
            {assinatura.lojasContratadas} {assinatura.lojasContratadas === 1 ? 'loja' : 'lojas'}
          </span>
          <span className="text-xs text-slate-500">
            {assinatura.vigenteAte ? `até ${dataBR(assinatura.vigenteAte)}` : 'sem prazo'}
          </span>
        </div>
        <button
          onClick={() => setEditando(!editando)}
          className="p-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 flex-shrink-0"
          title="Editar assinatura"
        >
          {editando ? <X className="w-4 h-4" /> : <Pencil className="w-4 h-4" />}
        </button>
      </div>
      {editando && <FormAssinatura assinatura={assinatura} onSalvo={() => setEditando(false)} />}
    </div>
  );
}

/**
 * O PUT do backend é substituição COMPLETA (status e lojasContratadas são
 * @NotNull), não patch. Por isso todo campo nasce com o valor atual: enviar
 * `lojasContratadas` errado não erra só o nosso banco — dispara
 * `atualizarValorAssinatura` e muda quanto o cliente paga por mês no Asaas.
 *
 * `vigenteAte` é campo só nosso: mudar a data não fala com o gateway. O que
 * ela NÃO faz é impedir a cobrança — quem tem subscription ativa continua
 * recebendo fatura no ciclo dele, então "mês de cortesia" para cliente pagante
 * exige adiar a cobrança no painel do Asaas também.
 */
function FormAssinatura({ assinatura, onSalvo }: { assinatura: Assinatura; onSalvo: () => void }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState(assinatura.status);
  const [lojas, setLojas] = useState(String(assinatura.lojasContratadas));
  const [vigenteAte, setVigenteAte] = useState(assinatura.vigenteAte ?? '');
  // Acesso sem prazo é escolha declarada, nunca um campo que ficou em branco:
  // no `acessivel()` do backend, vigenteAte nulo em TRIAL/ATIVA é grátis para sempre.
  const [semPrazo, setSemPrazo] = useState(assinatura.vigenteAte === null);

  const salvar = useMutation({
    mutationFn: () => api.put(`/assinaturas/${assinatura.empresaId}`, {
      status,
      lojasContratadas: Number(lojas),
      vigenteAte: semPrazo ? null : vigenteAte,
      gatewayCustomerId: assinatura.gatewayCustomerId,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['assinaturas'] });
      onSalvo();
    },
  });

  /** O caso de uso que motivou a tela: dar um mês a mais a partir do prazo atual. */
  const maisUmMes = () => {
    const base = new Date(`${vigenteAte || new Date().toISOString().slice(0, 10)}T00:00:00`);
    base.setMonth(base.getMonth() + 1);
    setSemPrazo(false);
    setVigenteAte(base.toISOString().slice(0, 10));
  };

  const lojasMudaram = Number(lojas) !== assinatura.lojasContratadas;
  const invalido = !Number.isInteger(Number(lojas)) || Number(lojas) < 1 || (!semPrazo && !vigenteAte);

  return (
    <div className="mt-3 pt-3 border-t border-slate-100 space-y-3">
      <div className="flex flex-wrap gap-3">
        <div>
          <label className="block text-xs font-medium text-slate-600 mb-1">Status</label>
          <select
            value={status}
            onChange={e => setStatus(e.target.value as Assinatura['status'])}
            className="px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]"
          >
            {(['TRIAL', 'ATIVA', 'INADIMPLENTE', 'CANCELADA'] as const).map(s => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-600 mb-1">Lojas contratadas</label>
          <input
            type="number" min={1} value={lojas}
            onChange={e => setLojas(e.target.value)}
            className="w-28 px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-600 mb-1">Vigente até</label>
          <div className="flex items-center gap-2">
            <input
              type="date" value={vigenteAte} disabled={semPrazo}
              onChange={e => setVigenteAte(e.target.value)}
              className="px-3 py-2 border border-slate-200 rounded-lg text-sm disabled:bg-slate-100 disabled:text-slate-400 focus:outline-none focus:ring-2 focus:ring-[#0ea5e9]"
            />
            <button
              type="button" onClick={maisUmMes}
              className="px-2.5 py-2 text-xs font-medium rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 whitespace-nowrap"
            >
              +1 mês
            </button>
          </div>
        </div>
      </div>

      <label className="flex items-center gap-2 text-xs text-slate-600">
        <input type="checkbox" checked={semPrazo} onChange={e => setSemPrazo(e.target.checked)} />
        Sem prazo — acesso liberado por tempo indeterminado
      </label>

      {/* Os dois avisos dependem de haver subscription no gateway: em
          `atualizar`, tanto o cancelamento quanto o recálculo de valor estão
          atrás de `preenchido(subscriptionId)`. Avisar sobre cobrança a quem
          está em trial seria assustar à toa no caso mais comum — dar cortesia. */}
      {status === 'CANCELADA' && assinatura.assinaturaIniciada && (
        <p className="flex items-start gap-2 text-xs text-amber-800 bg-amber-50 rounded-lg px-3 py-2">
          <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-px" />
          Salvar como CANCELADA <b>remove a assinatura no Asaas</b> e o cliente para de ser cobrado. Não tem desfazer: para voltar, ele assina de novo.
        </p>
      )}
      {lojasMudaram && assinatura.assinaturaIniciada && (
        <p className="flex items-start gap-2 text-xs text-amber-800 bg-amber-50 rounded-lg px-3 py-2">
          <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-px" />
          Mudar de {assinatura.lojasContratadas} para {lojas} lojas <b>altera o valor cobrado no Asaas</b>, inclusive na fatura pendente.
        </p>
      )}
      {!assinatura.assinaturaIniciada && (
        <p className="text-xs text-slate-500 bg-slate-50 rounded-lg px-3 py-2">
          Sem assinatura no gateway — lojas e vencimento aqui são <b>só nossos</b>. Nada é cobrado e o Asaas não é consultado.
        </p>
      )}
      {salvar.isError && (
        <p className="text-xs text-red-600">Não foi possível salvar. Se o erro veio do gateway, confira o Asaas antes de repetir.</p>
      )}

      <button
        onClick={() => salvar.mutate()}
        disabled={salvar.isPending || invalido}
        className="px-4 py-2 bg-[#0c4a6e] text-white text-sm font-medium rounded-lg hover:bg-[#0a3d5c] disabled:bg-slate-300"
      >
        {salvar.isPending ? 'Salvando...' : 'Salvar assinatura'}
      </button>
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
