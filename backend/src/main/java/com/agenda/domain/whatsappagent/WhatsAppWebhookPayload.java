package com.agenda.domain.whatsappagent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Espelha o payload do webhook de mensagens da Meta Cloud API. Apenas os
 * campos que o agente realmente usa são mapeados — o resto é ignorado via
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} em cada nível,
 * porque a Meta envia bem mais campos (status de entrega, perfil, etc.)
 * do que precisamos para processar uma mensagem de texto.
 * <p>
 * Formato oficial (resumido):
 * <pre>
 * {
 *   "object": "whatsapp_business_account",
 *   "entry": [{
 *     "changes": [{
 *       "value": {
 *         "metadata": { "phone_number_id": "..." },
 *         "contacts": [{ "profile": { "name": "..." }, "wa_id": "..." }],
 *         "messages": [{ "from": "...", "type": "text", "text": { "body": "..." } }]
 *       },
 *       "field": "messages"
 *     }]
 *   }]
 * }
 * </pre>
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppWebhookPayload {

    private String object;
    private List<Entry> entry;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entry {
        private String id;
        private List<Change> changes;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Change {
        private ChangeValue value;
        private String field;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChangeValue {
        private List<IncomingMessage> messages;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IncomingMessage {
        /** ID único da mensagem na Meta (ex.: "wamid...."), usado para idempotência. */
        private String id;
        /** Número de quem enviou a mensagem, só dígitos (ex.: "5583999999999"). */
        private String from;
        /** "text", "audio", "image", "button", etc. — só "text" e "audio" são tratados. */
        private String type;
        private TextBody text;
        private AudioBody audio;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TextBody {
        private String body;
    }

    /**
     * Bloco presente quando {@code type == "audio"} (mensagem de voz). A Meta
     * NÃO manda o áudio no webhook — só um ID de mídia, que precisa ser
     * trocado pelos bytes em duas chamadas à Graph API (ver
     * {@code WhatsAppMediaDownloader}).
     */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AudioBody {
        private String id;
    }
}
