package com.lexcv.fiscal.efatura;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Gera o IUD (Identificador Único do Documento, atributo {@code Dfe/@Id}) de 45 caracteres
 * (DFE-02).
 *
 * <p>Layout: {@code "CV"} + repositório(1) + AAMMDD(6) + NIF(9) + LED(5) + tipo(2) + número(9) +
 * aleatório(10) + dígito verificador Luhn(1). Fonte: excertos do manual eFatura v10 e o
 * {@code Iud.php} do SDK Kowts (portão G2/G3, confiança MEDIUM até à fonte primária). O XSD
 * ({@code stDfeId}) só restringe "CV", repositório, data e NIF; a divisão dos 27 dígitos finais
 * (LED, tipo, número, aleatório, DV) não é verificada pelo esquema.
 *
 * <p>O DV reproduz o exemplo oficial {@code CV1200520123456789000112345678901112345678904}
 * (DV 4). A parte aleatória vem de um {@link SecureRandom} injetado (T-136-18).
 */
@Component
public final class IudGerador {

    public static final int TAMANHO = 45;

    private static final long LIMITE_ALEATORIO = 10_000_000_000L;
    private static final long NUMERO_MAXIMO = 999_999_999L;
    private static final int LED_MAXIMO = 99_999;
    private static final Pattern NIF = Pattern.compile("[1-9]\\d{8}");

    private final SecureRandom aleatorio;

    @Autowired
    public IudGerador() {
        this(new SecureRandom());
    }

    IudGerador(SecureRandom aleatorio) {
        this.aleatorio = aleatorio;
    }

    /**
     * @param repositorio 1 = Principal, 2 = Homologação, 3 = Teste
     * @param dataEmissao data de emissão (hora local de Cabo Verde, já no snapshot)
     * @param nif         NIF do emitente, 9 dígitos sem zero à esquerda
     * @param led         código do LED (1..99999)
     * @param tipo        DocumentTypeCode (1..99), ex.: 2 = Fatura-Recibo, 5 = Nota de Crédito
     * @param numero      número do documento na série (1..999 999 999)
     */
    public String gerar(int repositorio, LocalDate dataEmissao, String nif, int led, int tipo, long numero) {
        if (repositorio < 1 || repositorio > 3) {
            throw new IllegalArgumentException("Repositório inválido para o IUD");
        }
        if (dataEmissao == null) {
            throw new IllegalArgumentException("Data de emissão em falta para o IUD");
        }
        if (nif == null || !NIF.matcher(nif).matches()) {
            throw new IllegalArgumentException("NIF inválido para o IUD");
        }
        if (led < 1 || led > LED_MAXIMO) {
            throw new IllegalArgumentException("LED inválido para o IUD");
        }
        if (tipo < 1 || tipo > 99) {
            throw new IllegalArgumentException("Tipo de documento inválido para o IUD");
        }
        if (numero < 1 || numero > NUMERO_MAXIMO) {
            throw new IllegalArgumentException("Número de documento inválido para o IUD");
        }
        String payload = repositorio
                + String.format(Locale.ROOT, "%02d%02d%02d", dataEmissao.getYear() % 100, dataEmissao.getMonthValue(),
                        dataEmissao.getDayOfMonth())
                + nif
                + String.format(Locale.ROOT, "%05d", led)
                + String.format(Locale.ROOT, "%02d", tipo)
                + String.format(Locale.ROOT, "%09d", numero)
                + String.format(Locale.ROOT, "%010d", aleatorio.nextLong(LIMITE_ALEATORIO));
        return "CV" + payload + luhn(payload);
    }

    /**
     * Dígito verificador Luhn: a partir do dígito mais à direita do payload, duplica os dígitos
     * em posições pares (posição 0 = mais à direita), subtrai 9 quando passa de 9; DV =
     * (10 - soma % 10) % 10.
     */
    public static int luhn(String payload) {
        int soma = 0;
        for (int i = 0; i < payload.length(); i++) {
            char c = payload.charAt(payload.length() - 1 - i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Payload do IUD não numérico");
            }
            int d = c - '0';
            if (i % 2 == 0) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            soma += d;
        }
        return (10 - soma % 10) % 10;
    }
}
