package com.agenda.domain.empresa;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

/**
 * Dados que o gateway exige para emitir a cobrança, informados pelo próprio
 * cliente na hora de assinar.
 * <p>
 * Ficam fora do cadastro público de propósito (decisão nº 2 do
 * docs/adr/0003): pedir documento a quem ainda está decidindo se vai usar o
 * sistema é a maior fricção possível no funil. São exigidos só aqui, quando ele
 * já decidiu pagar.
 */
public record DadosCobrancaRequest(
    @NotBlank
    @Pattern(regexp = "\\d{11}|\\d{14}", message = "Informe CPF (11 dígitos) ou CNPJ (14 dígitos), só números")
    String cpfCnpj,

    @NotBlank
    @Pattern(regexp = "\\d{11}", message = "Informe o celular com DDD (11 dígitos), só números")
    String telefone
) {

    /** DDDs que existem no Brasil. Os buracos são reais: não há 20, 23, 25, 26, 29, 30, 36, 39, 40, 50, 52, 56, 57, 58, 59, 60, 70, 72, 76, 78, 80 nem 90. */
    private static final Set<String> DDDS = Set.of(
        "11", "12", "13", "14", "15", "16", "17", "18", "19",
        "21", "22", "24", "27", "28",
        "31", "32", "33", "34", "35", "37", "38",
        "41", "42", "43", "44", "45", "46", "47", "48", "49",
        "51", "53", "54", "55",
        "61", "62", "63", "64", "65", "66", "67", "68", "69",
        "71", "73", "74", "75", "77", "79",
        "81", "82", "83", "84", "85", "86", "87", "88", "89",
        "91", "92", "93", "94", "95", "96", "97", "98", "99");

    /**
     * Confere os dígitos verificadores antes de mandar ao gateway.
     * <p>
     * Sem isto, um CPF digitado errado só é recusado lá — e o cliente recebe
     * "falha na comunicação com o gateway", que é genérica de propósito (não
     * vazar detalhe) e não diz o que corrigir. Errar o documento no instante em
     * que ele decidiu pagar é comum demais para depender de uma ida à rede.
     */
    /**
     * Confere que é celular, e não fixo, antes de mandar ao gateway.
     * <p>
     * Mesma razão do CPF acima, e o mesmo estrago: o Asaas envia este campo como
     * {@code mobilePhone} e recusa fixo com {@code invalid_mobilePhone}. Antes
     * disto, {@code \d{10,15}} deixava passar um fixo de 10 dígitos — e a
     * assinatura morria na rede com "falha na comunicação com o gateway".
     * <p>
     * Celular não tem dígito verificador; o que dá para conferir é o DDD existir
     * e o nono dígito estar lá (obrigatório em todo o país desde 2016).
     */
    @AssertTrue(message = "Celular inválido — confira o DDD e o número (11 dígitos, começando com 9).")
    public boolean isCelularValido() {
        if (telefone == null || !telefone.matches("\\d{11}")) {
            return true; // o @Pattern já reprova; não duplicar a mensagem
        }
        return DDDS.contains(telefone.substring(0, 2)) && telefone.charAt(2) == '9';
    }

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
