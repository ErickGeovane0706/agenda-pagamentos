import { useState, useCallback } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import api from '../../api/client';
import Turnstile from '../../components/Turnstile';
import './registro.css';

/**
 * Cadastro público, tela 1 do funil. Duas perguntas antes do email — o nome do
 * negócio (maior retorno emocional, é o nome dele na tela) e o acesso — e depois
 * a tela neutra de "confira seu email".
 *
 * Nada toca o servidor até o envio: a resposta do passo 1 vive só no estado do
 * componente e sobe junto com o cadastro. Perder essa resposta num refresh é
 * irrelevante; por isso o trecho pré-email é curto de propósito.
 */

const schema = z.object({
  nome: z.string().trim().min(2, 'Como podemos te chamar?'),
  email: z.string().trim().email('E-mail inválido'),
  senha: z.string()
    .min(8, 'Mínimo 8 caracteres')
    .regex(/[A-Z]/, 'Precisa de uma letra maiúscula')
    .regex(/[a-z]/, 'Precisa de uma letra minúscula')
    .regex(/[0-9]/, 'Precisa de um número'),
  aceiteTermos: z.literal(true, {
    errorMap: () => ({ message: 'É preciso aceitar os termos para continuar' }),
  }),
});

type FormData = z.infer<typeof schema>;

type Etapa = 'negocio' | 'acesso' | 'enviado';

export default function RegistroPage() {
  const [etapa, setEtapa] = useState<Etapa>('negocio');
  const [nomeNegocio, setNomeNegocio] = useState('');
  const [emailEnviado, setEmailEnviado] = useState('');
  const [turnstileToken, setTurnstileToken] = useState<string | null>(null);
  const [honeypot, setHoneypot] = useState('');

  const handleToken = useCallback((t: string) => setTurnstileToken(t), []);

  const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  const mutation = useMutation({
    mutationFn: (data: FormData) =>
      api.post('/auth/registro', {
        nomeNegocio,
        nome: data.nome,
        email: data.email,
        senha: data.senha,
        aceiteTermos: data.aceiteTermos,
        turnstileToken,
        website: honeypot,
      }),
    onSuccess: (_res, data) => { setEmailEnviado(data.email.trim()); setEtapa('enviado'); },
  });

  const erroStatus = (mutation.error as AxiosError | null)?.response?.status;
  const erroMsg = ((mutation.error as AxiosError<{ mensagem?: string }> | null)?.response?.data?.mensagem);

  // Turnstile só é obrigatório quando há site key (produção). Em dev o token
  // vem vazio e o botão não deve travar — o backend aceita.
  const temSiteKey = Boolean(import.meta.env.VITE_TURNSTILE_SITE_KEY);
  const aguardandoDesafio = temSiteKey && !turnstileToken;

  return (
    <div className="reg-root">
      {etapa === 'negocio' && (
        <>
          <div className="reg-topbar">
            <p className="reg-eyebrow"><b>[01]</b> Seu negócio</p>
            <div className="reg-ticks"><i className="now" /><i /><i /></div>
          </div>
          <div className="reg-body reg-fade" key="negocio">
            <h1 className="reg-h1">Qual o nome do seu negócio?</h1>
            <div className="reg-field">
              <label htmlFor="negocio">Nome</label>
              <input
                id="negocio"
                autoFocus
                autoComplete="organization"
                value={nomeNegocio}
                onChange={(e) => setNomeNegocio(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter' && nomeNegocio.trim().length >= 2) setEtapa('acesso'); }}
                placeholder="Mercado do João"
              />
            </div>
            <p className="reg-lede">É assim que ele vai aparecer no sistema. Dá para mudar depois.</p>
            <div className="reg-actions">
              <button
                className="reg-btn"
                disabled={nomeNegocio.trim().length < 2}
                onClick={() => setEtapa('acesso')}
              >
                Continuar
              </button>
              <Link to="/login" className="reg-link">Já tenho conta</Link>
            </div>
          </div>
        </>
      )}

      {etapa === 'acesso' && (
        <>
          <div className="reg-topbar">
            <p className="reg-eyebrow"><b>[02]</b> Seu acesso</p>
            <div className="reg-ticks"><i className="done" /><i className="now" /><i /></div>
          </div>
          <form className="reg-body reg-fade" key="acesso" onSubmit={handleSubmit((d) => mutation.mutate(d))}>
            <h1 className="reg-h1">Pronto, {nomeNegocio.trim()}. Onde mandamos seu acesso?</h1>
            <div className="reg-stack">
              <div className="reg-field">
                <label htmlFor="nome">Seu nome</label>
                <input id="nome" autoComplete="name" placeholder="João Batista" {...register('nome')} />
                {errors.nome && <span className="reg-erro">{errors.nome.message}</span>}
              </div>
              <div className="reg-field">
                <label htmlFor="email">E-mail</label>
                <input id="email" type="email" autoComplete="email" placeholder="voce@exemplo.com" {...register('email')} />
                {errors.email && <span className="reg-erro">{errors.email.message}</span>}
              </div>
              <div className="reg-field">
                <label htmlFor="senha">Senha</label>
                <input id="senha" type="password" autoComplete="new-password" placeholder="mínimo 8 caracteres" {...register('senha')} />
                {errors.senha && <span className="reg-erro">{errors.senha.message}</span>}
              </div>

              {/* Honeypot: fora da tela, invisível a humano, ignorado por leitor
                  de tela. Bot que preenche é descartado em silêncio no backend. */}
              <input
                type="text"
                tabIndex={-1}
                autoComplete="off"
                aria-hidden="true"
                value={honeypot}
                onChange={(e) => setHoneypot(e.target.value)}
                style={{ position: 'absolute', left: '-9999px', width: 1, height: 1, opacity: 0 }}
              />

              <label className="reg-consent">
                <input type="checkbox" {...register('aceiteTermos')} />
                <span>
                  Li e aceito os <Link to="/termos" target="_blank">termos de uso</Link> e a{' '}
                  <Link to="/privacidade" target="_blank">política de privacidade</Link>.
                </span>
              </label>
              {errors.aceiteTermos && <span className="reg-erro">{errors.aceiteTermos.message}</span>}

              <Turnstile onToken={handleToken} />
            </div>

            {mutation.isError && (
              <div className="reg-alert">
                {erroStatus === 429
                  ? 'Muitas tentativas. Aguarde um pouco e tente de novo.'
                  : erroMsg || 'Não foi possível concluir agora. Tente novamente.'}
              </div>
            )}

            <div className="reg-actions">
              <button className="reg-btn" type="submit" disabled={mutation.isPending || aguardandoDesafio}>
                {mutation.isPending ? 'Criando...' : 'Criar minha conta'}
              </button>
              <button type="button" className="reg-link" onClick={() => setEtapa('negocio')}>Voltar</button>
            </div>
          </form>
        </>
      )}

      {etapa === 'enviado' && (
        <>
          <div className="reg-topbar">
            <p className="reg-eyebrow"><b>[ ✉ ]</b> Confirmação</p>
            <div className="reg-ticks"><i className="done" /><i className="done" /><i className="now" /></div>
          </div>
          <div className="reg-body reg-fade" key="enviado">
            <div className="reg-envelope">✉</div>
            <h1 className="reg-h1">Tudo pronto para montar a {nomeNegocio.trim()}.</h1>
            <p className="reg-lede">
              Clique no link que enviamos e sua área é criada na hora. O link vale por 48 horas.
            </p>
            <div className="reg-mailbox">enviado para <b>{emailEnviado}</b></div>
            <p className="reg-lede">Não chegou? Confira a caixa de spam.</p>
            <div className="reg-actions">
              <Link to="/login" className="reg-link">Voltar para o login</Link>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
