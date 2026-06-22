package com.agenda.domain.empresa;

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
