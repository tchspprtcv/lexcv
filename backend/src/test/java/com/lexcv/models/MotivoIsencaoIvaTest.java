package com.lexcv.models;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 133 (CFG-02/CFG-04): pins the 21 official eFatura TaxExemptionReasonCode values and the
 * labels of the other fiscal enums. Any change to the official list must be deliberate.
 */
class MotivoIsencaoIvaTest {

    @Test
    void temExatamente21CodigosOficiaisPorOrdem() {
        MotivoIsencaoIva[] valores = MotivoIsencaoIva.values();
        assertEquals(21, valores.length);
        Set<String> codigos = new HashSet<>();
        for (int i = 0; i < valores.length; i++) {
            assertEquals(String.valueOf(i + 1), valores[i].codigo());
            assertEquals("M" + (i + 1), valores[i].name());
            codigos.add(valores[i].codigo());
        }
        assertEquals(21, codigos.size());
    }

    @Test
    void descricaoEMencaoNuncaVazias() {
        for (MotivoIsencaoIva m : MotivoIsencaoIva.values()) {
            assertNotNull(m.descricao());
            assertFalse(m.descricao().isBlank(), m.name());
            assertNotNull(m.mencao());
            assertFalse(m.mencao().isBlank(), m.name());
        }
    }

    @Test
    void porCodigoEncontraM20() {
        Optional<MotivoIsencaoIva> m = MotivoIsencaoIva.porCodigo("20");
        assertTrue(m.isPresent());
        assertEquals(MotivoIsencaoIva.M20, m.get());
        assertEquals("Tributo Especial Unificado", m.get().mencao());
    }

    @Test
    void porCodigoDevolveVazioParaDesconhecidos() {
        assertTrue(MotivoIsencaoIva.porCodigo("0").isEmpty());
        assertTrue(MotivoIsencaoIva.porCodigo("22").isEmpty());
        assertTrue(MotivoIsencaoIva.porCodigo(null).isEmpty());
        assertTrue(MotivoIsencaoIva.porCodigo("").isEmpty());
        assertTrue(MotivoIsencaoIva.porCodigo("  ").isEmpty());
    }

    @Test
    void ambienteFiscalSoTemSimulado() {
        assertEquals(1, AmbienteFiscal.values().length);
        assertEquals(AmbienteFiscal.SIMULADO, AmbienteFiscal.values()[0]);
        assertEquals("SIM-", AmbienteFiscal.SIMULADO.prefixoSerie());
        assertEquals("Simulado", AmbienteFiscal.SIMULADO.rotulo());
    }

    @Test
    void tipoDocumentoFiscalRotulos() {
        assertEquals(Set.of("FR", "NC"),
                new HashSet<>(Arrays.stream(TipoDocumentoFiscal.values()).map(Enum::name).toList()));
        assertEquals("Fatura-Recibo", TipoDocumentoFiscal.FR.rotulo());
        assertEquals("Nota de Crédito", TipoDocumentoFiscal.NC.rotulo());
    }

    @Test
    void regimeIvaRotulos() {
        assertEquals(2, RegimeIva.values().length);
        assertEquals("Normal", RegimeIva.NORMAL.rotulo());
        assertEquals("Isento", RegimeIva.ISENTO.rotulo());
    }
}
