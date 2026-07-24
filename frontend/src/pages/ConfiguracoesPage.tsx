import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
    Settings, User, Shield, Building2, Plus, Pencil, Trash2,
    Mail, X, AlertTriangle, MessageCircle, Store, Check, Download, CreditCard
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
    const [aba, setAba] = useState<'perfil' | 'bancos' | 'usuarios' | 'notificacoes' | 'assinatura' | 'meus-dados'>('perfil');
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
        ...(usuario?.perfil === 'ADMIN' ? [{ id: 'assinatura' as const, label: 'Assinatura', icon: CreditCard }] : []),
        { id: 'meus-dados' as const, label: 'Meus dados', icon: Download },
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
            {aba === 'assinatura' && <AssinaturaTab empresaId={usuario?.empresaId} />}
            {aba === 'meus-dados' && <MeusDadosTab />}
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
                            <span className="text-sm">Solicitação enviada. Seus dados são anonimizados no processamento da próxima madrugada.</span>
                        </div>
                    ) : (
                        /* O texto fala em usuário, e não em empresa, porque é o que o backend
                           faz: solicitarExclusao marca APENAS o usuário logado e nunca toca na
                           empresa nem nos outros usuários. */
                        <button
                            onClick={() => { if (confirm('Tem certeza? Seus dados pessoais serão anonimizados e você perderá o acesso. As contas registradas e os demais usuários da empresa não são afetados.')) onSolicitarExclusao(); }}
                            className="text-red-600 hover:text-red-700 text-sm font-medium hover:underline"
                        >
                            Solicitar exclusão dos meus dados
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}

const correcaoSchema = z.object({
    campo: z.string().min(2, 'Diga qual informação está errada'),
    valorAtual: z.string().min(1, 'Informe o que está registrado hoje'),
    valorCorrigido: z.string().min(1, 'Informe o valor correto'),
});

/**
 * Direitos do titular (LGPD). Os endpoints existiam desde sempre no backend,
 * mas nenhuma tela os usava — na prática o direito só era exercível por e-mail.
 *
 * A rota /api/lgpd é isenta do gate de assinatura de propósito: direito legal
 * não depende de estar em dia com o pagamento.
 */
function MeusDadosTab() {
    const [baixando, setBaixando] = useState(false);
    const [erroDownload, setErroDownload] = useState('');

    const { register, handleSubmit, reset, formState: { errors } } =
        useForm<z.infer<typeof correcaoSchema>>({ resolver: zodResolver(correcaoSchema) });

    const correcao = useMutation({
        mutationFn: (d: z.infer<typeof correcaoSchema>) => api.post('/lgpd/corrigir', d),
        onSuccess: () => reset(),
    });

    // O endpoint devolve o JSON com Content-Disposition, mas quem faz a
    // requisição é o axios (com cookie), não o navegador — então o arquivo
    // precisa ser materializado aqui.
    const baixar = async () => {
        setBaixando(true);
        setErroDownload('');
        try {
            const r = await api.get('/lgpd/portabilidade', { responseType: 'blob' });
            const url = URL.createObjectURL(new Blob([r.data], { type: 'application/json' }));
            const a = document.createElement('a');
            a.href = url;
            a.download = 'meus-dados.json';
            a.click();
            URL.revokeObjectURL(url);
        } catch {
            setErroDownload('Não foi possível gerar o arquivo agora. Tente de novo.');
        } finally {
            setBaixando(false);
        }
    };

    return (
        <div className="space-y-4">
            <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-6">
                <h3 className="font-medium text-slate-900 mb-1">Baixar meus dados</h3>
                <p className="text-sm text-slate-500 mb-4">
                    Gera um arquivo com seu cadastro, sua empresa, suas lojas e todos os
                    pagamentos registrados. É seu, dá para levar para outro sistema.
                </p>
                <button
                    onClick={baixar}
                    disabled={baixando}
                    className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#0c4a6e] text-white text-sm font-medium hover:bg-[#0a3d5c] transition-colors disabled:opacity-60"
                >
                    <Download className="w-4 h-4" />
                    {baixando ? 'Gerando...' : 'Baixar arquivo'}
                </button>
                {erroDownload && <p className="text-sm text-red-600 mt-3">{erroDownload}</p>}
            </div>

            <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-6">
                <h3 className="font-medium text-slate-900 mb-1">Corrigir uma informação</h3>
                <p className="text-sm text-slate-500 mb-4">
                    Achou algo errado no seu cadastro? Diga o que é e nós corrigimos.
                </p>

                {correcao.isSuccess ? (
                    <div className="flex items-center gap-2 text-green-700 bg-green-50 px-4 py-3 rounded-lg">
                        <Check className="w-4 h-4" />
                        <span className="text-sm">Pedido registrado. Vamos analisar e responder em até 15 dias.</span>
                    </div>
                ) : (
                    <form onSubmit={handleSubmit(d => correcao.mutate(d))} className="space-y-3">
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1.5">O que está errado</label>
                            <input {...register('campo')} placeholder="Ex.: meu nome, meu e-mail"
                                className="w-full px-4 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#0c4a6e]/20 focus:border-[#0c4a6e]" />
                            {errors.campo && <p className="text-xs text-red-600 mt-1">{errors.campo.message}</p>}
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1.5">Como está hoje</label>
                            <input {...register('valorAtual')}
                                className="w-full px-4 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#0c4a6e]/20 focus:border-[#0c4a6e]" />
                            {errors.valorAtual && <p className="text-xs text-red-600 mt-1">{errors.valorAtual.message}</p>}
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1.5">Como deveria ser</label>
                            <input {...register('valorCorrigido')}
                                className="w-full px-4 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-[#0c4a6e]/20 focus:border-[#0c4a6e]" />
                            {errors.valorCorrigido && <p className="text-xs text-red-600 mt-1">{errors.valorCorrigido.message}</p>}
                        </div>
                        {correcao.isError && (
                            <p className="text-sm text-red-600">Não foi possível enviar agora. Tente de novo.</p>
                        )}
                        <button type="submit" disabled={correcao.isPending}
                            className="px-4 py-2.5 rounded-xl bg-[#0c4a6e] text-white text-sm font-medium hover:bg-[#0a3d5c] transition-colors disabled:opacity-60">
                            {correcao.isPending ? 'Enviando...' : 'Pedir correção'}
                        </button>
                    </form>
                )}
            </div>

            <p className="text-xs text-slate-400 px-1">
                Para apagar seus dados, use "Solicitar exclusão dos meus dados" na aba Perfil.
                O que fazemos com suas informações está na{' '}
                <Link to="/privacidade" className="underline">Política de Privacidade</Link>.
            </p>
        </div>
    );
}

interface MinhaAssinatura {
    status: 'TRIAL' | 'ATIVA' | 'INADIMPLENTE' | 'CANCELADA';
    lojasContratadas: number;
    vigenteAte: string | null;
    precoBase: number;
    precoLojaAdicional: number;
    assinaturaIniciada: boolean;
}

const reais = (v: number) => v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const dataBR = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString('pt-BR');

const rotuloStatus: Record<MinhaAssinatura['status'], string> = {
    TRIAL: 'Período grátis',
    ATIVA: 'Ativa',
    INADIMPLENTE: 'Pagamento pendente',
    CANCELADA: 'Cancelada',
};

/**
 * Assinatura da própria empresa. O foco é o cancelamento self-service: o
 * endpoint DELETE já existia, mas nenhuma tela o chamava — sem botão, o cliente
 * que quer sair abre chargeback (pior que um cancelamento limpo). Só ADMIN vê a
 * aba, batendo com a autorização do endpoint.
 */
function AssinaturaTab({ empresaId }: { empresaId?: string }) {
    const queryClient = useQueryClient();

    const { data: a, isLoading } = useQuery<MinhaAssinatura>({
        queryKey: ['minha-assinatura'],
        queryFn: () => api.get('/assinaturas/minha').then(r => r.data),
    });

    const cancelar = useMutation({
        mutationFn: () => api.delete(`/assinaturas/${empresaId}/assinar`),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['minha-assinatura'] }),
    });

    if (isLoading || !a) {
        return (
            <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-8 text-center text-slate-400 text-sm">
                Carregando assinatura...
            </div>
        );
    }

    const total = a.precoBase + a.precoLojaAdicional * Math.max(0, a.lojasContratadas - 1);
    // Só há o que cancelar quando existe assinatura paga no gateway. Num trial
    // sem assinatura, "cancelar" só trancaria o cliente em somente-leitura de
    // graça — para sair do trial ele simplesmente para de usar.
    const podeCancelar = a.assinaturaIniciada && a.status !== 'CANCELADA';

    return (
        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
            <div className="p-6 border-b border-slate-100 space-y-3">
                <div className="flex items-center justify-between">
                    <span className="text-sm text-slate-500">Situação</span>
                    <span className="text-sm font-medium text-slate-900">{rotuloStatus[a.status]}</span>
                </div>
                <div className="flex items-center justify-between">
                    <span className="text-sm text-slate-500">Lojas contratadas</span>
                    <span className="text-sm font-medium text-slate-900">{a.lojasContratadas}</span>
                </div>
                <div className="flex items-center justify-between">
                    <span className="text-sm text-slate-500">Valor mensal</span>
                    <span className="text-sm font-medium text-slate-900">{reais(total)}</span>
                </div>
                {a.vigenteAte && (
                    <div className="flex items-center justify-between">
                        <span className="text-sm text-slate-500">
                            {a.status === 'TRIAL' ? 'Grátis até' : 'Vigente até'}
                        </span>
                        <span className="text-sm font-medium text-slate-900">{dataBR(a.vigenteAte)}</span>
                    </div>
                )}
            </div>

            <div className="p-6">
                {cancelar.isSuccess ? (
                    <div className="flex items-center gap-2 text-amber-700 bg-amber-50 px-4 py-3 rounded-lg">
                        <AlertTriangle className="w-4 h-4 flex-shrink-0" />
                        <span className="text-sm">
                            Assinatura cancelada. A cobrança foi encerrada
                            {a.vigenteAte ? `, e você mantém acesso até ${dataBR(a.vigenteAte)}` : ''}.
                            Depois disso a conta fica somente-leitura até você assinar de novo.
                        </span>
                    </div>
                ) : a.status === 'CANCELADA' ? (
                    <div className="text-sm text-slate-600">
                        Sua assinatura está cancelada.{' '}
                        <Link to="/assinar" className="text-[#0c4a6e] font-medium hover:underline">
                            Assinar novamente
                        </Link>.
                    </div>
                ) : podeCancelar ? (
                    <>
                        <h3 className="text-sm font-medium text-slate-700 mb-1">Cancelar assinatura</h3>
                        <p className="text-sm text-slate-500 mb-3">
                            A cobrança para na hora, mas você continua com acesso normal até o fim
                            do período já pago{a.vigenteAte ? ` (${dataBR(a.vigenteAte)})` : ''}.
                            Depois disso a conta fica somente-leitura. Dá para voltar assinando de
                            novo quando quiser.
                        </p>
                        {cancelar.isError && (
                            <p className="text-sm text-red-600 mb-3">
                                Não foi possível cancelar agora. Tente de novo em instantes.
                            </p>
                        )}
                        <button
                            onClick={() => {
                                const ate = a.vigenteAte ? ` Você mantém acesso até ${dataBR(a.vigenteAte)}.` : '';
                                if (confirm(`Cancelar a assinatura? A cobrança para na hora.${ate}`)) {
                                    cancelar.mutate();
                                }
                            }}
                            disabled={cancelar.isPending}
                            className="text-red-600 hover:text-red-700 text-sm font-medium hover:underline disabled:opacity-60"
                        >
                            {cancelar.isPending ? 'Cancelando...' : 'Cancelar minha assinatura'}
                        </button>
                    </>
                ) : (
                    <p className="text-sm text-slate-500">
                        Você ainda não tem uma assinatura paga.{' '}
                        <Link to="/assinar" className="text-[#0c4a6e] font-medium hover:underline">
                            Assinar
                        </Link>.
                    </p>
                )}
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

    const { register, handleSubmit, control, watch, setValue, reset, formState: { errors, isDirty } } =
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
                    <button
                        type="button"
                        role="switch"
                        aria-checked={whatsappAtivo}
                        onClick={() => setValue('whatsappAtivo', !whatsappAtivo, { shouldDirty: true })}
                        className="relative inline-flex items-center w-11 h-6 rounded-full transition-colors flex-shrink-0"
                        style={{ backgroundColor: whatsappAtivo ? '#0c4a6e' : '#e2e8f0' }}
                    >
            <span
                className="absolute top-0.5 left-0.5 bg-white rounded-full h-5 w-5 transition-transform"
                style={{ transform: whatsappAtivo ? 'translateX(20px)' : 'translateX(0)' }}
            />
                    </button>
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