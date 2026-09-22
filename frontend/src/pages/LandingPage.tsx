import { Link } from 'react-router-dom';
import './registro/registro.css';

/**
 * Landing pública (/comecar) — a tela que o tráfego de anúncio abre. Existe
 * porque mandar quem clicou direto para o formulário de cadastro é pedir dado
 * antes de dizer o que o produto faz e quanto custa.
 *
 * NÃO é uma página "viral" — o público é dono de loja 40+, e para ele metade da
 * venda é TIRAR O MEDO, não criar desejo. Por isso o eixo é o reasseguramento
 * ("a gente não mexe no seu dinheiro, só avisa"), e a prova é o mockup da
 * conversa chegando no WhatsApp — o único formato que vende sozinho para quem
 * vive no WhatsApp. Novidade aqui assusta; familiaridade converte.
 *
 * As mensagens do mockup espelham os templates reais aprovados na Meta
 * (MensagemNotificacaoBuilder): primeiro o RESUMO, depois um ITEM por pendência.
 *
 * Dois pontos de manutenção que não se enxergam daqui:
 *
 * - O PREÇO está escrito à mão neste arquivo porque é texto corrido dentro de
 *   uma frase. Mudá-lo exige mudar `TermosPage.tsx` e `assinatura.preco-*`
 *   junto — o termo de uso é contrato de adesão, então divergir ali é problema
 *   jurídico, não estético. Ver `docs/adr/0004-preco-como-fonte-unica.md`.
 * - O TEXTO FIXO do template de RESUMO não está no código: mora no painel da
 *   Meta, que é quem aprova os templates. O balão abaixo é uma reconstrução a
 *   partir dos parâmetros, e pode divergir da redação aprovada.
 */
/**
 * Suporte pré-venda. Quem chega do anúncio e ainda não tem conta não consegue
 * falar com ninguém: o agente do WhatsApp identifica o usuário pelo telefone
 * antes de qualquer coisa e encerra a conversa com quem não é cadastrado
 * (WhatsAppAgentService), e o único e-mail publicado é o da LGPD, escondido
 * na política de privacidade.
 *
 * O número vem de VITE_WHATSAPP_SUPORTE, e não do código, por dois motivos: é
 * um número pessoal enquanto não houver chip dedicado, e trocá-lo não devia
 * exigir um commit.
 *
 * Sem a variável o convite inteiro some, em vez de virar um link vazio: um
 * "Chama no WhatsApp" que não abre nada é pior do que não oferecer o canal.
 * Como toda VITE_*, é lida na COMPILAÇÃO — ver os ARG/ENV do Dockerfile.
 */
const NUMERO_SUPORTE = String(import.meta.env.VITE_WHATSAPP_SUPORTE ?? '').replace(/\D/g, '');

/**
 * O wa.me exige o número internacional COMPLETO, com o 55 na frente.
 *
 * Sem essa conferência, cadastrar a variável sem o DDI monta um link que
 * parece certo, abre normalmente e não chega em ninguém — e ninguém percebe,
 * porque nada quebra na tela. Já aconteceu em produção: a variável entrou sem
 * o `55` e o link virou `wa.me/<DDD><número>`, que o WhatsApp lê como outro
 * país.
 *
 * Número malformado esconde o convite, em vez de publicar um canal de suporte
 * que não existe. Ausência de botão alguém nota; botão que não leva a lugar
 * nenhum, não.
 */
const NUMERO_VALIDO = /^55\d{10,11}$/.test(NUMERO_SUPORTE);

if (import.meta.env.DEV && NUMERO_SUPORTE && !NUMERO_VALIDO) {
  console.warn(
    `[landing] VITE_WHATSAPP_SUPORTE="${NUMERO_SUPORTE}" não é um número com DDI. ` +
      'Esperado: 55 + DDD + número (ex.: 5583999999999). O convite ao WhatsApp não será exibido.'
  );
}

const WHATSAPP_SUPORTE = NUMERO_VALIDO
  ? `https://wa.me/${NUMERO_SUPORTE}?text=` +
    encodeURIComponent('Oi! Vim pelo site do Dia de Pagar e queria tirar uma dúvida.')
  : null;

export default function LandingPage() {
  return (
    <div className="reg-root reg-land">
      <div className="reg-topbar">
        <p className="reg-eyebrow"><b>[ ● ]</b> Contas sempre em dia</p>
        <Link to="/login" className="reg-link">Entrar</Link>
      </div>

      <div className="reg-body reg-land-grid">
        {/* Ato 1 — a tese em palavras */}
        <div className="reg-land-copy reg-fade">
          <h1 className="reg-h1 reg-land-h1">Nunca mais pague multa por esquecer uma conta.</h1>

          <p className="reg-lede">
            Boleto, cheque, Pix — quando a loja é sua, é você que lembra de tudo.
            A gente lembra por você e avisa no WhatsApp antes de cada vencimento.
          </p>

          {/* O medo nº 1 desse público: "esse app mexe no meu dinheiro?". */}
          <ul className="reg-land-trust">
            <li>A gente <b>não</b> mexe no seu dinheiro — só avisa.</li>
            <li>O aviso chega no <b>WhatsApp</b>, onde você já está o dia todo.</li>
            <li>Cancela quando quiser, sem letra miúda.</li>
          </ul>

          <p className="reg-land-price">
            <b>30 dias grátis</b> para testar. Depois, R$ 48,60 por mês.
          </p>

          <div className="reg-actions">
            <Link to="/registro" className="reg-btn reg-land-cta">
              Começar meus 30 dias grátis
            </Link>
          </div>

          {/* Fica DEPOIS do CTA de propósito: quem já decidiu não deve tropeçar
              numa segunda opção antes de clicar. Quem tem dúvida lê tudo e
              chega aqui. */}
          {WHATSAPP_SUPORTE && (
            <p className="reg-land-suporte">
              Ficou com dúvida?{' '}
              <a href={WHATSAPP_SUPORTE} target="_blank" rel="noopener noreferrer">
                Chama no WhatsApp
              </a>{' '}
              — responde uma pessoa, não um robô.
            </p>
          )}

          <p className="reg-lede reg-land-founder">
            Feito por quem cansou de ver dono de loja pagando multa por esquecimento.
          </p>
        </div>

        {/* Ato 2 — a prova: os lembretes reais chegando no WhatsApp */}
        <figure className="reg-chat reg-fade" aria-label="Exemplo dos lembretes no WhatsApp">
          <div className="reg-chat-head">
            <span className="reg-chat-avatar" aria-hidden="true">🔔</span>
            <div>
              <b>Lembretes da sua loja</b>
              <span className="reg-chat-status">online agora</span>
            </div>
          </div>
          <div className="reg-chat-body">
            <div className="reg-chat-day">HOJE</div>

            {/* Balão 1 — resumo (reconstruído dos parâmetros; confirmar na Meta) */}
            <div className="reg-chat-msg">
              <p>{`Olá, João! Você tem 5 pagamentos pendentes em 2 lojas, somando R$ 1.240,00. Desse total, R$ 348,00 já está vencido.`}</p>
              <span className="reg-chat-meta">08:00 <span className="reg-chat-tick">✓✓</span></span>
            </div>

            {/* Balão 2 — item (template lembrete_detalhe_loja, texto exato) */}
            <div className="reg-chat-msg">
              <p>{`📍 Loja: Mercado do João
🔔 Lembrete de pagamento pendente:
📄 Referente a: Boleto Light
💰 Valor a pagar: R$ 348,00
📅 25/07/2026 Vencimento.`}</p>
              <span className="reg-chat-meta">08:00 <span className="reg-chat-tick">✓✓</span></span>
            </div>
          </div>
        </figure>
      </div>
    </div>
  );
}
