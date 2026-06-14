package com.agenda.domain.arquivo;

public enum TipoDocumento {
    BOLETO("boletos"),
    PIX("pix"),
    CHEQUE("cheques");

    private final String pasta;

    TipoDocumento(String pasta) { this.pasta = pasta; }
    public String getPasta() { return pasta; }
}
