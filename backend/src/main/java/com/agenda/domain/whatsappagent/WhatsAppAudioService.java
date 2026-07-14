package com.agenda.domain.whatsappagent;

import com.agenda.security.RateLimiterService;
import com.agenda.whatsapp.WhatsAppMediaDownloader;
import com.agenda.whatsapp.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Etapa de áudio do agente: baixa a nota de voz da Meta, transcreve e entrega
 * o texto ao {@link WhatsAppAgentService}, que daí em diante trata tudo igual
 * a uma mensagem digitada.
 * <p>
 * <b>Por que é um bean separado do agente</b>: download + transcrição são
 * chamadas HTTP lentas (segundos) e não tocam no banco. Se rodassem dentro de
 * {@code WhatsAppAgentService.processarMensagem}, ficariam DENTRO da transação
 * {@code readOnly} dele — prendendo uma conexão do pool (que tem só 5) durante
 * toda a transcrição, sem necessidade nenhuma. Aqui, a transação só abre depois,
 * quando o agente já tem o texto em mãos.
 * <p>
 * <b>Teto por remetente</b>: o mesmo bucket do fluxo de texto
 * ({@code "wa:" + telefone}) é consumido AQUI, antes de baixar e transcrever.
 * Isso é essencial — transcrição é paga, e sem o teto antes um flood de áudios
 * custaria dinheiro mesmo sendo descartado depois. Por isso o agente é chamado
 * pela porta {@code processarTranscricao}, que já não consome token de novo:
 * cada mensagem (texto OU áudio) custa exatamente 1 token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppAudioService {

    private final WhatsAppMediaDownloader mediaDownloader;
    private final TranscricaoAudioService transcricaoService;
    private final WhatsAppAgentService agentService;
    private final WhatsAppService whatsAppService;
    private final RateLimiterService rateLimiter;

    /**
     * Teto de tamanho do áudio. A Meta NÃO informa a duração no webhook — só o
     * tamanho em bytes, e só na chamada de metadados. Nota de voz em Opus roda
     * a ~16-24 kbps, então 500 KB ≈ 3 minutos: folgado para qualquer pergunta
     * real, e ainda barra um áudio gigante mandado por engano antes de pagar a
     * transcrição.
     */
    private static final long TAMANHO_MAXIMO_BYTES = 500 * 1024L;

    /**
     * Processa uma mensagem de voz. Não lança exceção — toda falha vira uma
     * mensagem amigável ao cliente, para nunca deixá-lo no vácuo depois de
     * mandar um áudio.
     * <p>
     * {@code @Async} pelo mesmo motivo do agente: o controller já respondeu 200
     * para a Meta (que exige resposta em poucos segundos) e este método é lento
     * — download + transcrição.
     */
    @Async
    public void processarAudio(String telefoneOrigem, String mediaId) {
        if (!rateLimiter.tentarConsumir("wa:" + telefoneOrigem,
                WhatsAppAgentService.WEBHOOK_MAX_MENSAGENS, WhatsAppAgentService.WEBHOOK_JANELA)) {
            // Descarta em silêncio, igual ao fluxo de texto: sem baixar, sem
            // transcrever, sem responder (responder alimentaria um loop de eco).
            log.warn("[WHATSAPP-AUDIO] Teto de mensagens por remetente atingido, ignorando áudio de {}",
                    mascarar(telefoneOrigem));
            return;
        }

        var metadados = mediaDownloader.obterMetadados(mediaId);
        if (metadados == null) {
            responder(telefoneOrigem, "Não consegui baixar seu áudio. Pode tentar de novo ou mandar por texto?");
            return;
        }

        if (metadados.tamanhoBytes() > TAMANHO_MAXIMO_BYTES) {
            log.info("[WHATSAPP-AUDIO] Áudio de {} recusado por tamanho: {} bytes",
                    mascarar(telefoneOrigem), metadados.tamanhoBytes());
            responder(telefoneOrigem, "Esse áudio ficou muito longo pra mim. Pode resumir num áudio mais curto ou mandar por texto?");
            return;
        }

        byte[] conteudo = mediaDownloader.baixarConteudo(metadados.url());
        if (conteudo == null) {
            responder(telefoneOrigem, "Não consegui baixar seu áudio. Pode tentar de novo ou mandar por texto?");
            return;
        }

        String transcricao = transcricaoService.transcrever(conteudo, metadados.mimeType());
        if (transcricao == null || transcricao.isBlank()) {
            responder(telefoneOrigem, "Não consegui entender o áudio. Pode falar de novo ou mandar por texto?");
            return;
        }

        log.info("[WHATSAPP-AUDIO] Áudio de {} transcrito ({} caracteres).",
                mascarar(telefoneOrigem), transcricao.length());
        agentService.processarTranscricao(telefoneOrigem, transcricao);
    }

    private void responder(String telefone, String texto) {
        whatsAppService.enviarMensagemTexto(telefone, texto);
    }

    private String mascarar(String telefone) {
        if (telefone == null || telefone.length() < 4) {
            return "****";
        }
        return "****" + telefone.substring(telefone.length() - 4);
    }
}
