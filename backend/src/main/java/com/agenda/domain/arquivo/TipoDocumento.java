package com.agenda.domain.arquivo;

/**
 * Mapeia tipo de documento à pasta no bucket R2:
 * BOLETO → "boletos/", PIX → "pix/", CHEQUE → "cheques/".
 * A organização por loja (UUID) é feita na key em ArquivoService.
 */
public enum TipoDocumento {
    BOLETO("boletos"),
    PIX("pix"),
    CHEQUE("cheques");

    private final String pasta;

    TipoDocumento(String pasta) { this.pasta = pasta; }
    public String getPasta() { return pasta; }
}
