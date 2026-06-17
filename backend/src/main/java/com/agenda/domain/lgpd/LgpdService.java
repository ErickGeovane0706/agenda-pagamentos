package com.agenda.domain.lgpd;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.boleto.BoletoRepository;
import com.agenda.domain.cheque.ChequeRepository;
import com.agenda.domain.empresa.EmpresaRepository;
import com.agenda.domain.lgpd.LgpdDadosResponse.*;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.domain.pix.PagamentoPixRepository;
import com.agenda.domain.usuario.UsuarioDTO;
import com.agenda.domain.usuario.UsuarioRepository;
import com.agenda.shared.TenantContext;
import com.agenda.shared.UserContext;
import com.agenda.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LgpdService {

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final LojaRepository lojaRepository;
    private final BoletoRepository boletoRepository;
    private final PagamentoPixRepository pagamentoPixRepository;
    private final ChequeRepository chequeRepository;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public LgpdDadosResponse exportarDados() {
        var empresaId = TenantContext.getEmpresaId();
        var usuario = usuarioRepository.findById(UserContext.getUsuarioId())
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        var empresa = empresaRepository.findById(empresaId)
            .orElseThrow(() -> new NotFoundException("Empresa não encontrada"));

        java.util.List<LojaLgpd> lojas = lojaRepository.findByEmpresaIdOrderByNome(empresaId).stream()
            .map(l -> new LojaLgpd(l.getId().toString(), l.getNome(), l.getCnpj(), l.getDescricao()))
            .toList();

        java.util.List<BoletoLgpd> boletos = boletoRepository.findByEmpresa_Id(empresaId).stream()
            .map(b -> new BoletoLgpd(b.getId().toString(), b.getLoja().getNome(), b.getValor(),
                b.getVencimento().toString(), b.getStatus().name()))
            .toList();

        java.util.List<PixLgpd> pixs = pagamentoPixRepository.findByEmpresa_Id(empresaId).stream()
            .map(p -> new PixLgpd(p.getId().toString(), p.getLoja().getNome(), p.getValor(),
                p.getVencimento().toString(), p.getStatus().name()))
            .toList();

        java.util.List<ChequeLgpd> cheques = chequeRepository.findByEmpresa_Id(empresaId).stream()
            .map(c -> new ChequeLgpd(c.getId().toString(), c.getLoja().getNome(), c.getValor(),
                c.getVencimento().toString(), c.getStatus().name()))
            .toList();

        auditoriaService.registrar("LGPD_EXPORTAR", "USUARIO", usuario.getId(), "Exportação de dados solicitada");

        return new LgpdDadosResponse(
            UsuarioDTO.from(usuario),
            new EmpresaLgpd(empresa.getId().toString(), empresa.getNome(), empresa.getAtivo()),
            lojas, boletos, pixs, cheques
        );
    }

    @Transactional
    public void solicitarCorrecao(LgpdCorrigirRequest req) {
        var usuarioId = UserContext.getUsuarioId();
        auditoriaService.registrar("LGPD_CORRIGIR", "USUARIO", usuarioId,
            "Campo: " + req.campo() + " | Atual: " + req.valorAtual() + " | Novo: " + req.valorCorrigido());
    }
}
