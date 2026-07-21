import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate, Link } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import api from '../api/client';
import { useAuthStore } from '../store/authStore';
import { Building2, Lock, Mail } from 'lucide-react';

const schema = z.object({
  email: z.string().email('E-mail inválido'),
  senha: z.string().min(8, 'Mínimo 8 caracteres'),
});

type FormData = z.infer<typeof schema>;

export default function LoginPage() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);

  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  const loginMutation = useMutation({
    mutationFn: (data: FormData) =>
      api.post('/auth/login', { email: data.email, senha: data.senha }),
    onSuccess: (res) => {
      setAuth(res.data.usuario);
      navigate('/lojas');
    },
  });

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-950 to-blue-800 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-white rounded-2xl shadow-lg mb-4">
            <Building2 className="w-8 h-8 text-[#0c4a6e]" />
          </div>
          <h1 className="text-2xl font-bold text-white">Agenda de Pagamentos</h1>
          <p className="text-blue-200 text-sm mt-1">Faça login para continuar</p>
        </div>

        <div className="bg-white rounded-2xl shadow-2xl p-8">
          <form onSubmit={handleSubmit((d) => loginMutation.mutate(d))} className="space-y-5">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">E-mail</label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <input
                  {...register('email')}
                  type="email"
                  placeholder="seu@email.com"
                  className="w-full pl-10 pr-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9] focus:border-transparent transition-all"
                />
              </div>
              {errors.email && <p className="text-red-500 text-xs mt-1">{errors.email.message}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">Senha</label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <input
                  {...register('senha')}
                  type="password"
                  placeholder="••••••••"
                  className="w-full pl-10 pr-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0ea5e9] focus:border-transparent transition-all"
                />
              </div>
              {errors.senha && <p className="text-red-500 text-xs mt-1">{errors.senha.message}</p>}
            </div>

            {loginMutation.isError && (
              <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3">
                <p className="text-red-600 text-sm">E-mail ou senha incorretos.</p>
              </div>
            )}

            <button
              type="submit"
              disabled={loginMutation.isPending}
              className="w-full bg-[#0c4a6e] hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9] text-white font-semibold py-2.5 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-[#0ea5e9] focus:ring-offset-2"
            >
              {loginMutation.isPending ? 'Entrando...' : 'Entrar'}
            </button>
          </form>

          <p className="text-center text-sm text-slate-500 mt-5">
            <Link to="/esqueci-senha" className="hover:text-[#0c4a6e] hover:underline">
              Esqueci minha senha
            </Link>
          </p>

          <p className="text-center text-xs text-slate-400 mt-4">
            <Link to="/privacidade" className="hover:underline">Política de Privacidade</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
