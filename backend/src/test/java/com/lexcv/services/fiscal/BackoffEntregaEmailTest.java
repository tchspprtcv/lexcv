package com.lexcv.services.fiscal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexcv.models.EntregaEmailFiscal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Phase 137 (ENTR-04): tabela de recuo determinística da entrega por email, limite de 5 tentativas. */
class BackoffEntregaEmailTest {

    @ParameterizedTest
    @CsvSource({"1, PT1M", "2, PT5M", "3, PT15M", "4, PT1H"})
    void tabela(int tentativas, String esperado) {
        assertThat(BackoffEntregaEmail.atraso(tentativas)).isEqualTo(Duration.parse(esperado));
    }

    @Test
    void tabelaCobreTodasAsTentativasAntesDoMaximo() {
        assertThat(EntregaEmailFiscal.MAX_TENTATIVAS).isEqualTo(5);
        assertThat(BackoffEntregaEmail.tamanhoTabela()).isEqualTo(EntregaEmailFiscal.MAX_TENTATIVAS - 1);
        for (int t = 1; t < EntregaEmailFiscal.MAX_TENTATIVAS; t++) {
            assertThat(BackoffEntregaEmail.atraso(t)).isPositive();
        }
    }

    @Test
    void deterministico() {
        assertThat(BackoffEntregaEmail.atraso(3)).isEqualTo(BackoffEntregaEmail.atraso(3));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 5, 6})
    void foraDaTabelaERecusado(int tentativas) {
        assertThatThrownBy(() -> BackoffEntregaEmail.atraso(tentativas))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
