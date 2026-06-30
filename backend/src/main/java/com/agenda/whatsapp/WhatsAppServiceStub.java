package com.agenda.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementação temporária: apenas loga o template que seria enviado.
 * Use isso enquanto a integração com a Meta Cloud API ainda não está pronta.
 * Quando a integração real estiver feita, troque esta classe por
 * WhatsAppCloudApiService (ou remova o @Service daqui e adicione lá).
 */
@Slf4j
@Profile("dev | test")
@Service
public class WhatsAppServiceStub implements WhatsAppService {

    @Override
    public void enviarTemplate(String telefoneDestino, String nomeTemplate, List<String> parametros) {
        log.info("[WHATSAPP STUB] Para: {} | Template: {} | Parâmetros: {}",
                telefoneDestino, nomeTemplate, parametros);
    }

    @Override
    public void enviarMensagemTexto(String telefoneDestino, String texto) {
        log.info("[WHATSAPP STUB] Para: {} | Texto livre:\n{}", telefoneDestino, texto);
    }
}