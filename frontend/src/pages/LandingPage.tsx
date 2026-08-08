import { Link } from 'react-router-dom';
import './registro/registro.css';

/**
 * Landing pública (/comecar) — a tela que o anúncio do Facebook/Instagram deveria
 * abrir, e que hoje não existe: o tráfego cai direto no formulário de /registro
 * sem nunca dizer o que o produto faz nem quanto custa.
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
 * PLACEHOLDERS (decisão de negócio, não de código — ver POS-REGISTRO §5):
 * - marca/nome do agente ainda não decidida (o avatar da conversa usa um rótulo
 *   neutro; nada de marca gritada);
 * - a linha do fundador entra com nome/rosto quando existir;
 * - preço (R$48,60) ainda não validado com pagante — texto, não bandeira.
 *   Baixado de R$79 em 08/08 por decisão de negócio; se mudar de novo, o
 *   TermosPage e o `assinatura.preco-*` mudam junto;
 * - o TEXTO FIXO do template de RESUMO não está no código (mora no painel da
 *   Meta); o balão abaixo é uma reconstrução a partir dos parâmetros — CONFIRMAR
 *   a redação exata com o painel da Meta antes de divulgar.
 */
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
