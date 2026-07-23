package com.agenda.domain.empresa;

import com.agenda.domain.assinatura.AssinaturaService;
import com.agenda.domain.banco.BancoRepository;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.domain.usuario.UsuarioRepository;
import com.agenda.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service de Empresa. A exclusão verifica se há vínculos ativos
 * (usuários, lojas, bancos) e recusa a operação até que sejam
 * removidos — evitando órfãos no banco.
 */
@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final LojaRepository lojaRepository;
    private final BancoRepository bancoRepository;
    private final AssinaturaService assinaturaService;

    @Transactional(readOnly = true)
    public List<EmpresaDTO> listar() {
        return empresaRepository.findAll().stream()
            .map(EmpresaDTO::from)
            .toList();
    }

    @Transactional
    public EmpresaDTO criar(CriarEmpresaRequest req) {
        var empresa = Empresa.builder()
            .nome(req.nome())
            .build();
        empresa = empresaRepository.save(empresa);
        // Toda empresa nasce com assinatura TRIAL (1 loja) — sem ela, os gates
        // de assinatura bloqueariam a empresa antes da primeira loja existir.
        assinaturaService.criarTrial(empresa);
        return EmpresaDTO.from(empresa);
    }

    @Transactional
    public EmpresaDTO editar(UUID id, EditarEmpresaRequest req) {
        var empresa = empresaRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Empresa não encontrada"));
        empresa.setNome(req.nome());
        empresa.setAtivo(req.ativo());
        empresa = empresaRepository.save(empresa);
        return EmpresaDTO.from(empresa);
    }

    /**
     * Grava os dados de cobrança da própria empresa, no momento de assinar.
     * <p>
     * Separado de {@link #editar} porque aquele é o painel do MASTER (mexe em
     * nome e no flag de ativo) e este é self-service do ADMIN — que não pode
     * desativar o próprio tenant nem renomeá-lo por um caminho de billing.
     */
    @Transactional
    public void salvarDadosCobranca(UUID empresaId, DadosCobrancaRequest req) {
        var empresa = empresaRepository.findById(empresaId)
            .orElseThrow(() -> new NotFoundException("Empresa não encontrada"));
        empresa.setCpfCnpj(req.cpfCnpj());
        empresa.setTelefone(req.telefone());
        empresaRepository.save(empresa);
    }

    @Transactional
    public void excluir(UUID id) {
        var empresa = empresaRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Empresa não encontrada"));

        var motivos = new ArrayList<String>();
        var usuarios = usuarioRepository.findByEmpresaId(id);
        if (!usuarios.isEmpty()) {
            motivos.add(usuarios.size() + " usuário(s)");
        }
        var lojas = lojaRepository.findByEmpresaIdOrderByNome(id);
        if (!lojas.isEmpty()) {
            motivos.add(lojas.size() + " loja(s)");
        }
        var bancos = bancoRepository.findByEmpresaIdOrderByNome(id);
        if (!bancos.isEmpty()) {
            motivos.add(bancos.size() + " banco(s)");
        }

        if (!motivos.isEmpty()) {
            throw new IllegalArgumentException(
                "Exclua primeiro os vínculos: " + String.join(", ", motivos));
        }

        empresaRepository.delete(empresa);
    }
}
