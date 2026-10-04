package com.lexcv.models;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 133 (CFG-01/CFG-05): series code generation, ConfiguracaoFiscal builder defaults and the
 * completa() rule used to gate activation.
 */
class SerieFiscalCodigoTest {

    private static final String PADRAO_XSD = "^[A-Za-z0-9]+([_-][A-Za-z0-9]+)*$";

    @Test
    void gerarCodigoComPrefixoSimulado() {
        assertEquals("SIM-FR-2026", SerieFiscal.gerarCodigo(TipoDocumentoFiscal.FR, 2026, AmbienteFiscal.SIMULADO));
        assertEquals("SIM-NC-2027", SerieFiscal.gerarCodigo(TipoDocumentoFiscal.NC, 2027, AmbienteFiscal.SIMULADO));
    }

    @Test
    void codigosGeradosRespeitamTamanhoEPadraoXsd() {
        for (TipoDocumentoFiscal tipo : TipoDocumentoFiscal.values()) {
            for (AmbienteFiscal ambiente : AmbienteFiscal.values()) {
                for (int ano : new int[]{2026, 2099, 9999}) {
                    String codigo = SerieFiscal.gerarCodigo(tipo, ano, ambiente);
                    assertTrue(codigo.length() <= 20, codigo);
                    assertTrue(codigo.matches(PADRAO_XSD), codigo);
                }
            }
        }
    }

    @Test
    void builderTemDefaultsDesligados() {
        ConfiguracaoFiscal c = ConfiguracaoFiscal.builder().build();
        assertEquals(Boolean.FALSE, c.getAtiva());
        assertEquals(Boolean.FALSE, c.getEnvioEmailAutomatico());
        assertEquals(0L, SerieFiscal.builder().build().getUltimoNumero());
    }

    private static ConfiguracaoFiscal completaNormal() {
        return ConfiguracaoFiscal.builder()
                .nif("512345678")
                .firma("Escritório Exemplo, Lda")
                .morada("Rua 5 de Julho, 10")
                .localidade("Praia")
                .paisCodigo("CV")
                .emailContacto("geral@exemplo.cv")
                .telefoneContacto("+238 260 00 00")
                .regimeIva(RegimeIva.NORMAL)
                .build();
    }

    private static ConfiguracaoFiscal com(Consumer<ConfiguracaoFiscal> alteracao) {
        ConfiguracaoFiscal c = completaNormal();
        alteracao.accept(c);
        return c;
    }

    @Test
    void completaVerdadeiraParaNormalPreenchida() {
        assertTrue(completaNormal().completa());
    }

    @Test
    void nifInvalidoTornaIncompleta() {
        assertFalse(com(c -> c.setNif("012345678")).completa());
        assertFalse(com(c -> c.setNif("12345678")).completa());
        assertFalse(com(c -> c.setNif(null)).completa());
        assertFalse(com(c -> c.setNif("51234567a")).completa());
    }

    @Test
    void isentoExigeMotivoOficial() {
        assertFalse(com(c -> { c.setRegimeIva(RegimeIva.ISENTO); c.setMotivoIsencaoCodigo(null); }).completa());
        assertFalse(com(c -> { c.setRegimeIva(RegimeIva.ISENTO); c.setMotivoIsencaoCodigo("99"); }).completa());
        assertTrue(com(c -> { c.setRegimeIva(RegimeIva.ISENTO); c.setMotivoIsencaoCodigo("5"); }).completa());
    }

    @Test
    void regimeNuloTornaIncompleta() {
        assertFalse(com(c -> c.setRegimeIva(null)).completa());
    }

    @Test
    void moradaAcimaDe100Caracteres() {
        assertTrue(com(c -> c.setMorada("a".repeat(100))).completa());
        assertFalse(com(c -> c.setMorada("a".repeat(101))).completa());
    }

    @Test
    void camposObrigatoriosEmBranco() {
        assertFalse(com(c -> c.setFirma(" ")).completa());
        assertFalse(com(c -> c.setLocalidade("")).completa());
        assertFalse(com(c -> c.setEmailContacto(null)).completa());
        assertFalse(com(c -> c.setTelefoneContacto("  ")).completa());
        assertFalse(com(c -> c.setMorada(null)).completa());
    }
}
