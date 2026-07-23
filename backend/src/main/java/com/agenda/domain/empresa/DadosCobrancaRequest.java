package com.agenda.domain.empresa;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Dados que o gateway exige para emitir a cobrança, informados pelo próprio
 * cliente na hora de assinar.
 * <p>
 * Ficam fora do cadastro público de propósito (decisão nº 2 do
 * PLANO-REGISTRO.md): pedir documento a quem ainda está decidindo se vai usar o
 * sistema é a maior fricção possível no funil. São exigidos só aqui, quando ele
 * já decidiu pagar.
 */
public record DadosCobrancaRequest(
    @NotBlank
    @Pattern(regexp = "\\d{11}|\\d{14}", message = "Informe CPF (11 dígitos) ou CNPJ (14 dígitos), só números")
    String cpfCnpj,

    @NotBlank
    @Pattern(regexp = "\\d{10,15}", message = "Informe o telefone com DDD, só números")
    String telefone
) {

    /**
     * Confere os dígitos verificadores antes de mandar ao gateway.
     * <p>
     * Sem isto, um CPF digitado errado só é recusado lá — e o cliente recebe
     * "falha na comunicação com o gateway", que é genérica de propósito (não
     * vazar detalhe) e não diz o que corrigir. Errar o documento no instante em
     * que ele decidiu pagar é comum demais para depender de uma ida à rede.
     */
    @AssertTrue(message = "CPF ou CNPJ inválido — confira os números.")
    public boolean isCpfCnpjValido() {
        if (cpfCnpj == null || !cpfCnpj.matches("\\d{11}|\\d{14}")) {
            return true; // o @Pattern já reprova; não duplicar a mensagem
        }
        return cpfCnpj.length() == 11 ? cpfValido(cpfCnpj) : cnpjValido(cpfCnpj);
    }

    private static boolean cpfValido(String cpf) {
        if (cpf.chars().distinct().count() == 1) {
            return false;
        }
        return digitoVerificador(cpf, 9, 10) && digitoVerificador(cpf, 10, 11);
    }

    /** Dígito por soma ponderada decrescente, o algoritmo do CPF. */
    private static boolean digitoVerificador(String cpf, int ate, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < ate; i++) {
            soma += (cpf.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = soma % 11;
        int esperado = resto < 2 ? 0 : 11 - resto;
        return cpf.charAt(ate) - '0' == esperado;
    }

    private static boolean cnpjValido(String cnpj) {
        if (cnpj.chars().distinct().count() == 1) {
            return false;
        }
        int[] pesos1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] pesos2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return digitoCnpj(cnpj, pesos1, 12) && digitoCnpj(cnpj, pesos2, 13);
    }

    private static boolean digitoCnpj(String cnpj, int[] pesos, int posicao) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (cnpj.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        int esperado = resto < 2 ? 0 : 11 - resto;
        return cnpj.charAt(posicao) - '0' == esperado;
    }
}
