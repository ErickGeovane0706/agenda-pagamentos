package com.agenda.whatsapp;
import java.util.List;
/**
 * Abstração do envio de WhatsApp. A implementação real (Meta Cloud API) será
 * feita depois — por enquanto, use WhatsAppServiceStub para testar a lógica
 * de agendamento sem precisar da integração completa.
 */
public interface WhatsAppService {
    void enviar(String telefoneDestino, String mensagem);
    void enviarTemplate(String telefone, String nomeTemplate,  // disparo inicial
                        List<String> parametros);
}