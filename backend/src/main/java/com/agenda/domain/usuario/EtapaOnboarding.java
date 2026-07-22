package com.agenda.domain.usuario;

/**
 * Em que ponto do onboarding guiado o usuário parou.
 * <p>
 * As etapas anteriores (nome do negócio, acesso) não aparecem aqui: elas
 * acontecem ANTES do usuário existir. O onboarding persistido começa no
 * primeiro passo pós-login.
 * <p>
 * O default no banco é {@link #CONCLUIDO} — só o cadastro público opta por
 * iniciar o wizard. Ver V23.
 */
public enum EtapaOnboarding {
    /** Perguntar sobre lembretes no WhatsApp. Primeira etapa de quem se cadastrou sozinho. */
    LEMBRETES,

    /** Perguntar se trabalha com cheque e, se sim, quais bancos. */
    CHEQUES,

    /** Nada a perguntar: quem já usava o sistema, quem o admin cadastrou, ou quem terminou. */
    CONCLUIDO
}
