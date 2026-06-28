package com.agenda.whatsapp;

import java.util.List;

/**
 * Abstração do envio de WhatsApp.
 * <p>
 * Mensagens iniciadas pela empresa (o usuário não escreveu primeiro) só
 * podem ser enviadas como <b>template</b> pré-aprovado pela Meta — texto
 * livre é rejeitado pela API (erro 131047) fora da janela de 24h de uma
 * conversa já aberta pelo usuário. Por isso este contrato não aceita mais
 * uma "mensagem" solta; ele recebe o nome do template aprovado e a lista
 * ordenada de valores que preenchem {{1}}, {{2}}, {{3}}... do corpo.
 * <p>
 * Cada chamada a {@code enviarTemplate} corresponde a UMA mensagem do
 * WhatsApp. Quando o scheduler precisa mandar o resumo + um item por
 * pendência, ele chama este método uma vez para o resumo e uma vez por
 * pendência — ver {@link com.agenda.domain.notificacao.NotificacaoWhatsAppScheduler}.
 */
public interface WhatsAppService {

    /**
     * Envia uma mensagem de template aprovado pela Meta.
     *
     * @param telefoneDestino número do destinatário (com ou sem formatação;
     *                        a implementação normaliza para apenas dígitos)
     * @param nomeTemplate    nome exato do template cadastrado na Meta
     *                        (ex.: "lembrete_resumo_pendencias")
     * @param parametros      valores que preenchem {{1}}, {{2}}, {{3}}... do
     *                        corpo do template, NA ORDEM em que aparecem. Não
     *                        podem conter quebra de linha nem mais de 4
     *                        espaços consecutivos (regra da Meta).
     */
    void enviarTemplate(String telefoneDestino, String nomeTemplate, List<String> parametros);
}