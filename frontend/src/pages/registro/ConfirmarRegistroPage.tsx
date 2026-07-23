import { useEffect, useRef, useState } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import api from '../../api/client';
import { useAuthStore } from '../../store/authStore';
import './registro.css';

/**
 * Cadastro público, tela do clique do link. Aqui — e só aqui — há trabalho real
 * acontecendo: o POST provisiona empresa, loja, usuário e trial numa transação.
 *
 * A animação de montagem NÃO é teatro: as quatro linhas correspondem ao que o
 * backend faz de verdade. Como a transação leva menos de um segundo, a narração
 * é só ritmo — segurar cada passo dá peso ao momento sem inventar etapa nenhuma.
 * Este é o único ponto do fluxo que mergulha no azul (.imersivo).
 */

const PASSOS = [
  'Criando sua empresa',
  'Preparando sua loja',
  'Configurando seu acesso',
  'Liberando seus 30 dias',
];
const INTERVALO_MS = 850;

type Estado = 'montando' | 'erro';

export default function ConfirmarRegistroPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const token = params.get('token');

  const [estado, setEstado] = useState<Estado>('montando');
  const [passo, setPasso] = useState(0);
  const [erroMsg, setErroMsg] = useState('');
  const jaDisparou = useRef(false);

  useEffect(() => {
    // StrictMode monta o efeito duas vezes em dev; o token é de uso único, então
    // a segunda chamada falharia. O guard garante um disparo só.
    if (jaDisparou.current) return;
    jaDisparou.current = true;

    if (!token) {
      setErroMsg('Link inválido ou expirado.');
      setEstado('erro');
      return;
    }

    const inicio = Date.now();

    // A barra avança sozinha enquanto a requisição corre — dá o ritmo. O envio
    // logado só acontece quando as DUAS coisas terminam: a animação e o backend.
    const timer = setInterval(() => {
      setPasso((p) => (p < PASSOS.length - 1 ? p + 1 : p));
    }, INTERVALO_MS);

    api.post('/auth/registro/confirmar', { token })
      .then((res) => {
        const restante = PASSOS.length * INTERVALO_MS - (Date.now() - inicio);
        window.setTimeout(() => {
          clearInterval(timer);
          setPasso(PASSOS.length); // marca todos concluídos
          setAuth(res.data.usuario);
          // Pequena pausa para o último check aparecer antes de sair do mergulho.
          window.setTimeout(() => navigate('/lojas', { replace: true }), 600);
        }, Math.max(restante, 0));
      })
      .catch((err) => {
        clearInterval(timer);
        setErroMsg(err?.response?.data?.mensagem || 'Não foi possível confirmar seu cadastro.');
        setEstado('erro');
      });

    return () => clearInterval(timer);
  }, [token, navigate, setAuth]);

  if (estado === 'erro') {
    return (
      <div className="reg-root">
        <div className="reg-topbar">
          <p className="reg-eyebrow"><b>[ ! ]</b> Confirmação</p>
        </div>
        <div className="reg-body reg-fade">
          <h1 className="reg-h1">Este link não funciona mais.</h1>
          <p className="reg-lede">
            {erroMsg} Ele pode ter expirado, já ter sido usado, ou sua conta já estar pronta.
          </p>
          <div className="reg-actions">
            <Link to="/login" className="reg-btn" style={{ textDecoration: 'none' }}>Ir para o login</Link>
            <Link to="/registro" className="reg-link">Começar de novo</Link>
          </div>
        </div>
      </div>
    );
  }

  const pct = Math.round((Math.min(passo + 1, PASSOS.length) / PASSOS.length) * 100);
  const rotulo = passo >= PASSOS.length ? 'Concluído' : PASSOS[passo];

  return (
    <div className="reg-root imersivo">
      <div className="reg-topbar">
        <p className="reg-eyebrow"><b>[ ⟳ ]</b> Montando</p>
      </div>
      <div className="reg-body reg-fade">
        <h1 className="reg-h1">Montando sua área.</h1>
        <ul className="reg-buildlist">
          {PASSOS.map((texto, i) => {
            const cls = i < passo ? 'done' : i === passo ? 'now' : '';
            const marca = i < passo ? '✓' : i === passo ? '⟳' : '·';
            return (
              <li key={texto} className={cls}>
                <span className="reg-mark">{marca}</span>
                {texto}
              </li>
            );
          })}
        </ul>
        <div>
          <div className="reg-meter"><i style={{ width: `${pct}%` }} /></div>
          <div className="reg-readout"><span>{rotulo}</span><span>{pct}%</span></div>
        </div>
      </div>
    </div>
  );
}
