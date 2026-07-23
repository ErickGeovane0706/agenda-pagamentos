import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, Clock } from 'lucide-react';
import api from '../api/client';
import { useAuthStore } from '../store/authStore';

/**
 * Faixa de aviso da assinatura, no topo de todas as telas do app.
 *
 * Existe porque o 402 sozinho avisa TARDE: sem isto, o cliente suspenso navega
 * por um sistema silencioso e só descobre o bloqueio ao esbarrar nele — e quem
 * está no fim do trial não descobre nada, simplesmente para de conseguir
 * trabalhar no dia 31. Aviso é melhor que surpresa quando o assunto é cobrança.
 *
 * Não aparece para quem está tranquilo: ATIVA some, e TRIAL só acende na última
 * semana. Uma faixa permanente vira paisagem e para de ser lida.
 */

const DIAS_PARA_AVISAR = 7;

interface MinhaAssinatura {
  status: 'TRIAL' | 'ATIVA' | 'INADIMPLENTE' | 'CANCELADA';
  vigenteAte: string | null;
}

function diasRestantes(vigenteAte: string) {
  const fim = new Date(`${vigenteAte}T23:59:59`).getTime();
  return Math.ceil((fim - Date.now()) / 86_400_000);
}

export default function AvisoAssinatura() {
  const usuario = useAuthStore((s) => s.usuario);
  // O MASTER não tem tenant próprio — /minha responderia 403. O painel dele é
  // a tela de Empresas.
  const ehCliente = usuario != null && usuario.perfil !== 'MASTER';

  const { data } = useQuery<MinhaAssinatura>({
    queryKey: ['minha-assinatura'],
    queryFn: () => api.get('/assinaturas/minha').then((r) => r.data),
    enabled: ehCliente,
  });

  if (!data) return null;

  const bloqueada = data.status === 'INADIMPLENTE' || data.status === 'CANCELADA';
  const dias = data.vigenteAte ? diasRestantes(data.vigenteAte) : null;
  const trialAcabando = data.status === 'TRIAL' && dias !== null && dias <= DIAS_PARA_AVISAR;

  if (!bloqueada && !trialAcabando) return null;

  const texto = bloqueada
    ? 'Sua conta está suspensa — você pode ver seus dados, mas não lançar nem editar.'
    : dias! <= 0
      ? 'Seu período grátis termina hoje.'
      : `Seu período grátis termina em ${dias} ${dias === 1 ? 'dia' : 'dias'}.`;

  const Icone = bloqueada ? AlertTriangle : Clock;

  return (
    <div
      role="status"
      className={`flex flex-wrap items-center gap-x-3 gap-y-2 px-4 py-2.5 text-sm ${
        bloqueada ? 'bg-red-50 text-red-800' : 'bg-amber-50 text-amber-900'
      }`}
    >
      <Icone className="w-4 h-4 flex-shrink-0" />
      <span className="flex-1 min-w-[12rem]">{texto}</span>
      <Link
        to="/assinar"
        className={`rounded-lg px-3 py-1.5 text-xs font-semibold text-white transition-colors ${
          bloqueada ? 'bg-red-600 hover:bg-red-700' : 'bg-amber-600 hover:bg-amber-700'
        }`}
      >
        {bloqueada ? 'Regularizar' : 'Assinar agora'}
      </Link>
    </div>
  );
}
