package com.lexcv.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Phase 135 (NCRD-01): motivo fechado da Nota de Crédito, com rótulo em português. */
class MotivoNotaCreditoTest {

    @Test
    void temExatamenteOsQuatroMotivosPorOrdem() {
        assertEquals(List.of("ANULACAO_TOTAL", "CORRECAO_VALOR", "ERRO_DADOS_CLIENTE", "OUTRO"),
                Arrays.stream(MotivoNotaCredito.values()).map(Enum::name).toList());
    }

    @Test
    void rotulosEmPortugues() {
        assertEquals("Anulação total", MotivoNotaCredito.ANULACAO_TOTAL.rotulo());
        assertEquals("Correção de valor", MotivoNotaCredito.CORRECAO_VALOR.rotulo());
        assertEquals("Erro nos dados do cliente", MotivoNotaCredito.ERRO_DADOS_CLIENTE.rotulo());
        assertEquals("Outro", MotivoNotaCredito.OUTRO.rotulo());
    }

    @Test
    void porNomeIgnoraEspacosEMaiusculas() {
        assertEquals(Optional.of(MotivoNotaCredito.CORRECAO_VALOR), MotivoNotaCredito.porNome("CORRECAO_VALOR"));
        assertEquals(Optional.of(MotivoNotaCredito.CORRECAO_VALOR), MotivoNotaCredito.porNome("  CORRECAO_VALOR "));
        assertEquals(Optional.of(MotivoNotaCredito.OUTRO), MotivoNotaCredito.porNome("outro"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "NAO_EXISTE", "Anulação total"})
    void porNomeRecusaValoresDesconhecidos(String valor) {
        assertEquals(Optional.empty(), MotivoNotaCredito.porNome(valor));
    }

    @Test
    void converterFazIdaEVoltaPeloNome() {
        MotivoNotaCreditoConverter conv = new MotivoNotaCreditoConverter();
        for (MotivoNotaCredito m : MotivoNotaCredito.values()) {
            assertEquals(m.name(), conv.convertToDatabaseColumn(m));
            assertEquals(m, conv.convertToEntityAttribute(m.name()));
        }
        assertNull(conv.convertToDatabaseColumn(null));
        assertNull(conv.convertToEntityAttribute(null));
    }
}
