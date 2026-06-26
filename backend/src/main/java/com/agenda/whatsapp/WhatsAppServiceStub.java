package com.agenda.whatsapp;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Implementação temporária: apenas loga a mensagem que seria enviada.
 * Use isso enquanto a integração com a Meta Cloud API ainda não está pronta.
 * Quando a integração real estiver feita, troque esta classe porgit
 * WhatsAppCloudApiService (ou remova o @Service daqui e adicione lá).
 */
@Slf4j
@Profile("dev | test")
@Service
public class WhatsAppServiceStub implements WhatsAppService {

    @Override
    public void enviar(String telefoneDestino, String mensagem) {
        log.info("[WHATSAPP STUB] Para: {} | Mensagem:\n{}", telefoneDestino, mensagem);
    }
    @Override
    public void enviarTemplate(String telefoneDestino, String nomeTemplate, List<String> parametros) {
        log.info("[WHATSAPP STUB] Template: {} | Para: {} | Params: {}",
                nomeTemplate, telefoneDestino, parametros);
    }
}