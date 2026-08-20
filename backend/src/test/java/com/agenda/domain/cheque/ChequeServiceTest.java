package com.agenda.domain.cheque;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.arquivo.ArquivoService;
import com.agenda.domain.banco.BancoRepository;
import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.loja.Loja;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.shared.TenantContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChequeServiceTest {

    @Mock private ChequeRepository chequeRepository;
    @Mock private LojaRepository lojaRepository;
    @Mock private BancoRepository bancoRepository;
    @Mock private ArquivoService arquivoService;
    @Mock private AuditoriaService auditoriaService;

    private ChequeService chequeService;
    private Empresa empresa;
    private Loja loja;

    @BeforeEach
    void setUp() {
        chequeService = new ChequeService(chequeRepository, lojaRepository, bancoRepository, arquivoService, auditoriaService);
        empresa = Empresa.builder().id(UUID.randomUUID()).build();
        loja = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Loja Teste").cor("#3B82F6").build();
        TenantContext.setEmpresaId(empresa.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void criar_DeveSalvarCheque() {
        var req = new CriarChequeRequest(loja.getId(), "Fornecedor X",
            new BigDecimal("500.00"), LocalDate.now().plusDays(30), null, "12345", null);

        when(lojaRepository.findById(loja.getId())).thenReturn(Optional.of(loja));
        when(chequeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = chequeService.criar(req);

        assertNotNull(result);
        assertEquals("Fornecedor X", result.fornecedor());
        assertEquals(StatusCheque.PENDENTE, result.status());
        verify(auditoriaService).registrar(eq("CRIAR"), eq("CHEQUE"), any(), anyString());
    }

    @Test
    void mudarStatus_DeveAtualizarCompensadoEmQuandoCOMPENSADO() {
        var cheque = Cheque.builder()
            .id(UUID.randomUUID()).empresa(empresa).loja(loja)
            .fornecedor("Teste").valor(new BigDecimal("100"))
            .vencimento(LocalDate.now()).status(StatusCheque.PENDENTE)
            .build();

        when(chequeRepository.findById(cheque.getId())).thenReturn(Optional.of(cheque));
        when(chequeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = chequeService.mudarStatus(cheque.getId(), StatusCheque.COMPENSADO);

        assertEquals(StatusCheque.COMPENSADO, result.status());
        assertNotNull(result.compensadoEm());
        verify(auditoriaService).registrar(eq("MUDAR_STATUS"), eq("CHEQUE"), any(), anyString());
    }

    @Test
    void excluir_DeveRemoverCheque() {
        var cheque = Cheque.builder()
            .id(UUID.randomUUID()).empresa(empresa).loja(loja)
            .fornecedor("Teste").valor(new BigDecimal("100"))
            .vencimento(LocalDate.now()).build();

        when(chequeRepository.findById(cheque.getId())).thenReturn(Optional.of(cheque));

        chequeService.excluir(cheque.getId());

        verify(chequeRepository).delete(cheque);
        verify(auditoriaService).registrar(eq("EXCLUIR"), eq("CHEQUE"), eq(cheque.getId()), anyString());
    }

    @Test
    void editar_DeveAtualizarDados() {
        var cheque = Cheque.builder()
            .id(UUID.randomUUID()).empresa(empresa).loja(loja)
            .fornecedor("Antigo").valor(new BigDecimal("100"))
            .vencimento(LocalDate.now()).build();
        var req = new EditarChequeRequest(loja.getId(), "Novo Fornecedor",
            new BigDecimal("200"), LocalDate.now().plusDays(10), null, "99999", null);

        when(chequeRepository.findById(cheque.getId())).thenReturn(Optional.of(cheque));
        when(lojaRepository.findById(loja.getId())).thenReturn(Optional.of(loja));
        when(chequeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = chequeService.editar(cheque.getId(), req);

        assertEquals("Novo Fornecedor", result.fornecedor());
        assertEquals(0, new BigDecimal("200").compareTo(result.valor()));
        verify(auditoriaService).registrar(eq("EDITAR"), eq("CHEQUE"), eq(cheque.getId()), anyString());
    }

    @Test
    void listar_DeveFiltrarPorEmpresa() {
        var pageable = PageRequest.of(0, 10);
        when(chequeRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(Page.empty());

        var result = chequeService.listar(null, null, null, null, null, pageable);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * Mesma exigência do {@link com.agenda.domain.boleto.BoletoService#paginaPendente}:
     * a conta de "quantos vêm antes" precisa usar a ordenação da listagem inteira,
     * senão a aba abre numa página que não é a do primeiro pendente.
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void paginaPendente_DeveContarComOsMesmosCriteriosDaOrdenacao() {
        var alvo = Cheque.builder()
                .id(UUID.randomUUID()).empresa(empresa).loja(loja)
                .fornecedor("Fornecedor B").valor(new BigDecimal("100"))
                .vencimento(LocalDate.of(2026, 8, 20)).status(StatusCheque.PENDENTE)
                .build();

        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(chequeRepository.findAll(any(Specification.class), pageCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of(alvo)));
        ArgumentCaptor<Specification<Cheque>> antesCaptor = ArgumentCaptor.forClass(Specification.class);
        when(chequeRepository.count(antesCaptor.capture())).thenReturn(30L);

        assertEquals(2, chequeService.paginaPendente(null, null, null, null, null, 15));

        assertEquals(Sort.by(Sort.Direction.ASC, "vencimento", "fornecedor", "id"),
                pageCaptor.getValue().getSort());

        Root<Cheque> root = mock(Root.class);
        Path empresaPath = mock(Path.class);
        Path vencimentoPath = mock(Path.class);
        Path fornecedorPath = mock(Path.class);
        Path idPath = mock(Path.class);
        when(root.get("empresa")).thenReturn(empresaPath);
        when(empresaPath.get("id")).thenReturn(mock(Path.class));
        when(root.get("vencimento")).thenReturn(vencimentoPath);
        when(root.get("fornecedor")).thenReturn(fornecedorPath);
        when(root.get("id")).thenReturn(idPath);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        antesCaptor.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);

        verify(cb).lessThan(vencimentoPath, alvo.getVencimento());
        verify(cb).lessThan(fornecedorPath, alvo.getFornecedor());
        verify(cb).lessThan(idPath, alvo.getId());
    }
}