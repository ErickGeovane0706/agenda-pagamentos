package com.agenda.domain.lgpd;

import com.agenda.domain.usuario.UsuarioDTO;

import java.util.List;

/**
 * Response agregada com todos os dados pessoais do titular.
 * Inclui registros financeiros (boletos, PIX, cheques) como
 * exigido pelo princípio do acesso integral (Art. 9º LGPD).
 */
public record LgpdDadosResponse(
    UsuarioDTO usuario,
    EmpresaLgpd empresa,
    List<LojaLgpd> lojas,
    List<BoletoLgpd> boletos,
    List<PixLgpd> pagamentosPix,
    List<ChequeLgpd> cheques
) {
    public record EmpresaLgpd(String id, String nome, Boolean ativo) {}
    public record LojaLgpd(String id, String nome, String cnpj, String descricao) {}
    public record BoletoLgpd(String id, String lojaNome, java.math.BigDecimal valor, String vencimento, String status) {}
    public record PixLgpd(String id, String lojaNome, java.math.BigDecimal valor, String vencimento, String status) {}
    public record ChequeLgpd(String id, String lojaNome, java.math.BigDecimal valor, String vencimento, String status) {}
}
