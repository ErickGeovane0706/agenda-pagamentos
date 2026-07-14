package com.agenda.domain.whatsappagent;

import com.agenda.security.RateLimiterService;
import com.agenda.whatsapp.WhatsAppMediaDownloader;
import com.agenda.whatsapp.WhatsAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Cobre as regras que protegem CUSTO no fluxo de áudio — as que não podem
 * regredir em silêncio, porque cada uma delas é uma chamada paga (download da
 * Meta + transcrição da OpenAI):
 * <ul>
 *   <li>áudio acima do teto de tamanho nunca é baixado nem transcrito;</li>
 *   <li>remetente que estourou o teto de mensagens nem chega a baixar;</li>
 *   <li>falha de transcrição nunca deixa o cliente no vácuo.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class WhatsAppAudioServiceTest {

    @Mock private WhatsAppMediaDownloader mediaDownloader;
    @Mock private TranscricaoAudioService transcricaoService;
    @Mock private WhatsAppAgentService agentService;
    @Mock private WhatsAppService whatsAppService;

    private WhatsAppAudioService audioService;

    private static final String TELEFONE = "5583999999999";
    private static final String MEDIA_ID = "media-123";
    private static final String URL = "https://lookaside.fbsbx.com/whatsapp/media-123";
    private static final String MIME = "audio/ogg; codecs=opus";

    @BeforeEach
    void setUp() {
        audioService = new WhatsAppAudioService(
                mediaDownloader, transcricaoService, agentService, whatsAppService, new RateLimiterService());
    }

    @Test
    void audioValido_DeveBaixarTranscreverEEntregarAoAgente() {
        byte[] bytes = new byte[100_000];
        when(mediaDownloader.obterMetadados(MEDIA_ID))
                .thenReturn(new WhatsAppMediaDownloader.MetadadosMidia(URL, MIME, bytes.length));
        when(mediaDownloader.baixarConteudo(URL)).thenReturn(bytes);
        when(transcricaoService.transcrever(bytes, MIME)).thenReturn("quanto tenho pra pagar essa semana");

        audioService.processarAudio(TELEFONE, MEDIA_ID);

        verify(agentService).processarTranscricao(TELEFONE, "quanto tenho pra pagar essa semana");
        verify(whatsAppService, never()).enviarMensagemTexto(any(), any());
    }

    @Test
    void audioAcimaDoTeto_NaoDeveBaixarNemTranscrever() {
        // 600 KB — acima do teto de 500 KB.
        when(mediaDownloader.obterMetadados(MEDIA_ID))
                .thenReturn(new WhatsAppMediaDownloader.MetadadosMidia(URL, MIME, 600 * 1024L));

        audioService.processarAudio(TELEFONE, MEDIA_ID);

        verify(mediaDownloader, never()).baixarConteudo(any());
        verify(transcricaoService, never()).transcrever(any(), any());
        verify(agentService, never()).processarTranscricao(any(), any());
        verify(whatsAppService).enviarMensagemTexto(eq(TELEFONE), contains("muito longo"));
    }

    @Test
    void transcricaoFalhou_DeveAvisarOClienteENaoChamarOAgente() {
        byte[] bytes = new byte[1_000];
        when(mediaDownloader.obterMetadados(MEDIA_ID))
                .thenReturn(new WhatsAppMediaDownloader.MetadadosMidia(URL, MIME, bytes.length));
        when(mediaDownloader.baixarConteudo(URL)).thenReturn(bytes);
        when(transcricaoService.transcrever(bytes, MIME)).thenReturn(null);

        audioService.processarAudio(TELEFONE, MEDIA_ID);

        verify(agentService, never()).processarTranscricao(any(), any());
        verify(whatsAppService).enviarMensagemTexto(eq(TELEFONE), contains("Não consegui entender"));
    }

    @Test
    void downloadFalhou_DeveAvisarOClienteENaoTranscrever() {
        when(mediaDownloader.obterMetadados(MEDIA_ID)).thenReturn(null);

        audioService.processarAudio(TELEFONE, MEDIA_ID);

        verify(transcricaoService, never()).transcrever(any(), any());
        verify(agentService, never()).processarTranscricao(any(), any());
        verify(whatsAppService).enviarMensagemTexto(eq(TELEFONE), contains("Não consegui baixar"));
    }

    /**
     * O teto por remetente é consumido ANTES do download — senão um flood de
     * áudios pagaria transcrição mesmo sendo descartado depois.
     */
    @Test
    void tetoPorRemetenteEstourado_NaoDeveNemBaixarOAudio() {
        byte[] bytes = new byte[1_000];
        when(mediaDownloader.obterMetadados(MEDIA_ID))
                .thenReturn(new WhatsAppMediaDownloader.MetadadosMidia(URL, MIME, bytes.length));
        when(mediaDownloader.baixarConteudo(URL)).thenReturn(bytes);
        when(transcricaoService.transcrever(bytes, MIME)).thenReturn("oi");

        // Gasta todos os tokens do bucket do remetente.
        for (int i = 0; i < WhatsAppAgentService.WEBHOOK_MAX_MENSAGENS; i++) {
            audioService.processarAudio(TELEFONE, MEDIA_ID);
        }
        clearInvocations(mediaDownloader, transcricaoService, agentService);

        audioService.processarAudio(TELEFONE, MEDIA_ID);

        verify(mediaDownloader, never()).obterMetadados(anyString());
        verify(transcricaoService, never()).transcrever(any(), any());
        verify(agentService, never()).processarTranscricao(any(), any());
        // Descarta em silêncio — responder alimentaria um loop de eco.
        verify(whatsAppService, never()).enviarMensagemTexto(any(), any());
    }
}
