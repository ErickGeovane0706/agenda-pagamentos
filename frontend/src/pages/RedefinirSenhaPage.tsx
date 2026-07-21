import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import api from '../api/client';
import { Building2, Lock, ArrowLeft, CheckCircle2 } from 'lucide-react';

// Espelha o PasswordValidator do backend. O backend continua sendo a
// autoridade — isto só evita uma ida ao servidor para errar o óbvio.
const schema = z.object({
  novaSenha: z.string()
    .min(8, 'Mínimo 8 caracteres')
    .regex(/[A-Z]/, 'Precisa de uma letra maiúscula')
    .regex(/[a-z]/, 'Precisa de uma letra minúscula')
    .regex(/\d/, 'Precisa de um número'),
  confirmacao: z.string(),
}).refine((d) => d.novaSenha === d.confirmacao, {
  message: 'As senhas não conferem',
  path: ['confirmacao'],
});

type FormData = z.infer<typeof schema>;

export default function RedefinirSenhaPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token') ?? '';

  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  const mutation = useMutation({
    mutationFn: (data: FormData) =>
      api.post('/auth/senha/redefinir', { token, novaSenha: data.novaSenha }),
    onSuccess: () => {
      setTimeout(() => navigate('/login'), 2500);
    },
  });

  const erro = mutation.error as AxiosError<{ mensagem?: string }> | null;
  const mensagemErro = erro?.response?.data?.mensagem ?? 'Não foi possível redefinir a senha.';

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-950 to-blue-800 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-white rounded-2xl shadow-lg mb-4">
            <Building2 className="w-8 h-8 text-[#0c4a6e]" />
          </div>
          <h1 className="text-2xl font-bold text-white">Nova senha</h1>
          <p className="text-blue-200 text-sm mt-1">Escolha a senha que você vai usar a partir de agora</p>
        </div>

        <div className="bg-white rounded-2xl shadow-2xl p-8">
          {!token ? (
            <div className="text-center">
              <p className="text-slate-700 text-sm">
                Link inválido ou incompleto. Peça um novo link para redefinir sua senha.
              </p>
              <Link
                to="/esqueci-senha"
                className="inline-block mt-4 text-sm font-medium text-[#0c4a6e] hover:underline"
              >
                Pedir novo link
              </Link>
            </div>
          ) : mutation.isSuccess ? (
            <div className="text-center">
              <div className="inline-flex items-center justify-center w-12 h-12 bg-green-50 rounded-full mb-4">
                <CheckCircle2 className="w-6 h-6 text-green-600" />
              </div>
              <p className="text-slate-700 text-sm">Senha alterada com sucesso.</p>
              <p className="text-slate-400 text-xs mt-2">
                Suas outras sessões foram encerradas. Redirecionando para o login...
              </p>
            </div>
          ) : (
            <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="space-y-5">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Nova senha</label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                  <input
                    {...register('novaSenha')}
                    type="password"
                    placeholder="••••••••"
                    className="w-full pl-10 pr-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9] focus:border-transparent transition-all"
                  />
                </div>
                {errors.novaSenha && <p className="text-red-500 text-xs mt-1">{errors.novaSenha.message}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">Confirmar nova senha</label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                  <input
                    {...register('confirmacao')}
                    type="password"
                    placeholder="••••••••"
                    className="w-full pl-10 pr-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9] focus:border-transparent transition-all"
                  />
                </div>
                {errors.confirmacao && <p className="text-red-500 text-xs mt-1">{errors.confirmacao.message}</p>}
              </div>

              {mutation.isError && (
                <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3">
                  <p className="text-red-600 text-sm">{mensagemErro}</p>
                  <Link to="/esqueci-senha" className="text-red-700 text-xs underline mt-1 inline-block">
                    Pedir um novo link
                  </Link>
                </div>
              )}

              <button
                type="submit"
                disabled={mutation.isPending}
                className="w-full bg-[#0c4a6e] hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9] text-white font-semibold py-2.5 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-[#0ea5e9] focus:ring-offset-2"
              >
                {mutation.isPending ? 'Salvando...' : 'Salvar nova senha'}
              </button>
            </form>
          )}

          <p className="text-center text-sm text-slate-500 mt-6">
            <Link to="/login" className="inline-flex items-center gap-1 hover:text-[#0c4a6e] hover:underline">
              <ArrowLeft className="w-3.5 h-3.5" /> Voltar para o login
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
