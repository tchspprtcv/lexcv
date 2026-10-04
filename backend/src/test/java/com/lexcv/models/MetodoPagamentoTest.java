package com.lexcv.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 134 (EMIS-10, D-04): método de pagamento fechado com o respetivo código de meio eFatura. */
class MetodoPagamentoTest {

    @Test
    void temExatamenteOsCincoMetodos() {
        assertEquals(5, MetodoPagamento.values().length);
        assertEquals(List.of("DINHEIRO", "TRANSFERENCIA", "CHEQUE", "CARTAO", "OUTRO"),
                Arrays.stream(MetodoPagamento.values()).map(Enum::name).toList());
    }

    @Test
    void cadaMetodoTemRotuloECodigoDeMeio() {
        for (MetodoPagamento m : MetodoPagamento.values()) {
            assertNotNull(m.rotulo());
            assertFalse(m.rotulo().isBlank(), m.name());
            String codigo = m.codigoMeioPagamento();
            assertNotNull(codigo, m.name());
            assertTrue(codigo.length() >= 1 && codigo.length() <= 3, m.name() + " -> " + codigo);
        }
    }

    @Test
    void codigosDeMeioDePagamento() {
        assertEquals("10", MetodoPagamento.DINHEIRO.codigoMeioPagamento());
        assertEquals("30", MetodoPagamento.TRANSFERENCIA.codigoMeioPagamento());
        assertEquals("20", MetodoPagamento.CHEQUE.codigoMeioPagamento());
        assertEquals("48", MetodoPagamento.CARTAO.codigoMeioPagamento());
        assertEquals("ZZZ", MetodoPagamento.OUTRO.codigoMeioPagamento());
    }

    @Test
    void porNomeIgnoraEspacosEMaiusculas() {
        assertEquals(Optional.of(MetodoPagamento.TRANSFERENCIA), MetodoPagamento.porNome("transferencia "));
        assertEquals(Optional.of(MetodoPagamento.DINHEIRO), MetodoPagamento.porNome("DINHEIRO"));
        assertEquals(Optional.of(MetodoPagamento.CARTAO), MetodoPagamento.porNome(" Cartao"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "Transferência", "PIX", "Dinheiro em especie"})
    void porNomeRecusaValoresDesconhecidos(String valor) {
        assertEquals(Optional.empty(), MetodoPagamento.porNome(valor));
    }
}
