import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import api from '../api/client';
import { Building2, Mail, ArrowLeft, MailCheck } from 'lucide-react';

const schema = z.object({
  email: z.string().email('E-mail inválido'),
});

type FormData = z.infer<typeof schema>;

export default function EsqueciSenhaPage() {
  const mutation = useMutation({
    mutationFn: (data: FormData) => api.post('/auth/senha/esqueci', { email: data.email }),
  });

  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  // O backend responde 204 exista o email ou não. A tela repete essa
  // neutralidade: mostrar "email não encontrado" aqui reabriria, no
  // frontend, o oráculo de clientes que o backend fecha de propósito.
  const enviado = mutation.isSuccess;
  const excedeuTentativas = (mutation.error as AxiosError | null)?.response?.status === 429;

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-950 to-blue-800 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-white rounded-2xl shadow-lg mb-4">
            <Building2 className="w-8 h-8 text-[#0c4a6e]" />
          </div>
          <h1 className="text-2xl font-bold text-white">Recuperar acesso</h1>
          <p className="text-blue-200 text-sm mt-1">Enviaremos um link para redefinir sua senha</p>
        </div>

        <div className="bg-white rounded-2xl shadow-2xl p-8">
          {enviado ? (
            <div className="text-center">
              <div className="inline-flex items-center justify-center w-12 h-12 bg-green-50 rounded-full mb-4">
                <MailCheck className="w-6 h-6 text-green-600" />
              </div>
              <p className="text-slate-700 text-sm">
                Se este e-mail estiver cadastrado, você receberá um link para redefinir a senha.
              </p>
              <p className="text-slate-400 text-xs mt-2">
                O link vale por 2 horas e só pode ser usado uma vez. Confira também a caixa de spam.
              </p>
            </div>
          ) : (
            <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="space-y-5">
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

              {mutation.isError && (
                <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3">
                  <p className="text-red-600 text-sm">
                    {excedeuTentativas
                      ? 'Muitas tentativas. Aguarde um pouco e tente novamente.'
                      : 'Não foi possível enviar agora. Tente novamente.'}
                  </p>
                </div>
              )}

              <button
                type="submit"
                disabled={mutation.isPending}
                className="w-full bg-[#0c4a6e] hover:bg-[#0a3d5c] disabled:bg-[#0ea5e9] text-white font-semibold py-2.5 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-[#0ea5e9] focus:ring-offset-2"
              >
                {mutation.isPending ? 'Enviando...' : 'Enviar link'}
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
