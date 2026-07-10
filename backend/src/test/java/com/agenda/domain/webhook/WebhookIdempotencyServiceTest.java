package com.agenda.domain.webhook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookIdempotencyServiceTest {

    @Mock private WebhookEventoProcessadoRepository repository;
    @InjectMocks private WebhookIdempotencyService service;

    @Test
    void registrarSeNovo_DeveRetornarTrueEGravarNaPrimeiraVez() {
        when(repository.existsByOrigemAndEventoId("WHATSAPP", "wamid.1")).thenReturn(false);

        boolean novo = service.registrarSeNovo("WHATSAPP", "wamid.1");

        assertTrue(novo);
        verify(repository).save(any(WebhookEventoProcessado.class));
    }

    @Test
    void registrarSeNovo_DeveRetornarFalseENaoGravarSeJaProcessado() {
        when(repository.existsByOrigemAndEventoId("WHATSAPP", "wamid.1")).thenReturn(true);

        boolean novo = service.registrarSeNovo("WHATSAPP", "wamid.1");

        assertFalse(novo);
        verify(repository, never()).save(any());
    }
}
