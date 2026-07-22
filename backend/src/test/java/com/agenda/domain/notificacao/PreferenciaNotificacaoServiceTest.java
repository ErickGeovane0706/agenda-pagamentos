package com.agenda.domain.notificacao;

import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.domain.uso.LimiteUsoService;
import com.agenda.domain.usuario.Usuario;
import com.agenda.domain.usuario.UsuarioRepository;
import com.agenda.shared.TenantContext;
import com.agenda.shared.UserContext;
import com.agenda.shared.exception.PagamentoRequeridoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre o teto de destinatários de lembrete. Cada telefone ativo custa um
 * template pago por disparo, até 4 vezes ao dia — sem teto, uma empresa em
 * trial multiplica a conta cadastrando usuários.
 */
@ExtendWith(MockitoExtension.class)
class PreferenciaNotificacaoServiceTest {

    @Mock private PreferenciaNotificacaoRepository preferenciaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private LojaRepository lojaRepository;
    @Mock private LimiteUsoService limiteUsoService;

    private PreferenciaNotificacaoService service;

    private Empresa empresa;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new PreferenciaNotificacaoService(
                preferenciaRepository, usuarioRepository, lojaRepository, limiteUsoService);

        empresa = Empresa.builder().id(UUID.randomUUID()).nome("Empresa Teste").build();
        usuario = Usuario.builder().id(UUID.randomUUID()).nome("João").empresa(empresa).build();

        TenantContext.setEmpresaId(empresa.getId());
        UserContext.set(usuario.getId(), usuario.getNome(), "ADMIN");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        UserContext.clear();
    }

    private AtualizarPreferenciaNotificacaoRequest requisicao(boolean ativo) {
        return new AtualizarPreferenciaNotificacaoRequest(
                "5583999990000", ativo, LocalTime.of(9, 0), null, null, null, Set.of());
    }

    private void usuarioExiste() {
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
    }

    @Test
    void ativarDentroDoLimite_deveSalvar() {
        usuarioExiste();
        when(preferenciaRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.empty());
        when(limiteUsoService.limiteDestinatarios(empresa.getId())).thenReturn(2);
        when(preferenciaRepository.contarAtivosDaEmpresa(empresa.getId())).thenReturn(1L);
        when(preferenciaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> service.atualizar(requisicao(true)));
    }

    @Test
    void ativarAcimaDoLimite_deveRecusarCom402() {
        usuarioExiste();
        when(preferenciaRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.empty());
        when(limiteUsoService.limiteDestinatarios(empresa.getId())).thenReturn(2);
        when(preferenciaRepository.contarAtivosDaEmpresa(empresa.getId())).thenReturn(2L);

        assertThrows(PagamentoRequeridoException.class, () -> service.atualizar(requisicao(true)));
        verify(preferenciaRepository, never()).save(any());
    }

    /**
     * Quem já recebe lembrete não pode perder a vaga ao mexer em horário ou
     * loja: a contagem inclui o próprio registro, então revalidar num usuário
     * já ativo bloquearia justamente quem está dentro do limite.
     */
    @Test
    void editarQuemJaEstavaAtivo_naoDeveRevalidarOTeto() {
        usuarioExiste();
        var existente = PreferenciaNotificacao.builder()
                .id(UUID.randomUUID()).usuario(usuario).whatsappAtivo(true).build();
        when(preferenciaRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.of(existente));
        when(preferenciaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> service.atualizar(requisicao(true)));

        verify(preferenciaRepository, never()).contarAtivosDaEmpresa(any());
    }

    @Test
    void desativar_naoDeveConsultarOTeto() {
        usuarioExiste();
        when(preferenciaRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.empty());
        when(preferenciaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.atualizar(requisicao(false));

        verify(preferenciaRepository, never()).contarAtivosDaEmpresa(any());
    }
}
