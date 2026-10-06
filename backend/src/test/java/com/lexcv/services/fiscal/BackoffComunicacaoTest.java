package com.lexcv.services.fiscal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Phase 136 (DFE-04): tabela de recuo determinística, sem jitter. */
class BackoffComunicacaoTest {

    @ParameterizedTest
    @CsvSource({"1, PT30S", "2, PT2M", "3, PT5M", "4, PT15M", "5, PT30M", "6, PT1H", "7, PT3H"})
    void tabela(int tentativas, String esperado) {
        assertThat(BackoffComunicacao.atraso(tentativas)).isEqualTo(Duration.parse(esperado));
    }

    @Test
    void tabelaCobreTodasAsTentativasAntesDoMaximo() {
        for (int t = 1; t < EstadoComunicacaoMapper.MAX_TENTATIVAS; t++) {
            assertThat(BackoffComunicacao.atraso(t)).isPositive();
        }
    }

    @Test
    void deterministico() {
        assertThat(BackoffComunicacao.atraso(3)).isEqualTo(BackoffComunicacao.atraso(3));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 8, 9})
    void foraDaTabelaERecusado(int tentativas) {
        assertThatThrownBy(() -> BackoffComunicacao.atraso(tentativas))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
