package com.agenda.domain.boleto;

/**
 * Reduz o código de um boleto aos 44 dígitos do código de barras — a chave da
 * detecção de duplicata.
 * <p>
 * O mesmo boleto chega em três formas: 44 (câmera), 47 (linha digitável bancária)
 * ou 48 (arrecadação), com ou sem pontos e espaços. A linha digitável é o código de
 * barras reagrupado com um DV por campo; aqui só se desfaz o reagrupamento. Os DVs
 * não são conferidos — isso é do leitor no front ({@code utils/boleto.ts}), e a
 * conversão é a mesma de lá.
 */
final class CodigoBoleto {

    private CodigoBoleto() {}

    /** Os 44 dígitos, ou {@code null} se o texto não tem comprimento de boleto. */
    static String normalizar(String codigo) {
        if (codigo == null) return null;
        String l = codigo.replaceAll("\\D", "");
        return switch (l.length()) {
            case 44 -> l;
            // banco+moeda, DV geral, fator+valor, e os três campos livres sem seus DVs
            case 47 -> l.substring(0, 4) + l.charAt(32) + l.substring(33, 47)
                    + l.substring(4, 9) + l.substring(10, 20) + l.substring(21, 31);
            // quatro blocos de 11 dígitos + 1 DV
            case 48 -> l.substring(0, 11) + l.substring(12, 23)
                    + l.substring(24, 35) + l.substring(36, 47);
            default -> null;
        };
    }
}
