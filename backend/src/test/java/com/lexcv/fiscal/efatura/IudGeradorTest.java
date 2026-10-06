package com.lexcv.fiscal.efatura;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IudGeradorTest {

    /** Exemplo oficial do pacote eFatura 2024-05-27 ("2 InvoiceReceipt.xml", atributo Id). */
    private static final String VETOR_OFICIAL = "CV1200520123456789000112345678901112345678904";

    /** stDfeId de CV_EFatura_Types_v1.0.xsd. */
    private static final Pattern ST_DFE_ID = Pattern.compile(
            "CV(\\d)(\\d{2})(0[1-9]|1[012])(0[1-9]|[12][0-9]|3[01])([1-9]\\d{8})\\d{27}");
    /** Iud::isValid do SDK Kowts. */
    private static final Pattern KOWTS = Pattern.compile(
            "^CV([1-3])(\\d{2})(\\d{2})(\\d{2})([1-9]\\d{8})(\\d{5})(\\d{2})(\\d{9})(\\d{10})(\\d)$");

    private static final LocalDate DATA = LocalDate.of(2026, 6, 15);
    private static final String NIF = "512345679";

    /** SecureRandom de teste: devolve os valores pela ordem dada. */
    static final class AleatorioFixo extends SecureRandom {
        private final Deque<Long> valores;

        AleatorioFixo(Long... valores) {
            this.valores = new ArrayDeque<>(List.of(valores));
        }

        @Override
        public long nextLong(long bound) {
            long v = valores.removeFirst();
            assertThat(bound).isEqualTo(10_000_000_000L);
            return v;
        }
    }

    @Test
    void luhnReproduzOVetorOficial() {
        assertThat(VETOR_OFICIAL).hasSize(45);
        String payload = VETOR_OFICIAL.substring(2, 44);
        assertThat(IudGerador.luhn(payload)).isEqualTo(4);
        assertThat(IudGerador.luhn(VETOR_OFICIAL.substring(2, 44))).isEqualTo(VETOR_OFICIAL.charAt(44) - '0');
    }

    @Test
    void geraUmIudDe45CaracteresComOLayoutOficial() {
        IudGerador gerador = new IudGerador(new AleatorioFixo(1234567890L));

        String iud = gerador.gerar(3, DATA, NIF, 99999, 2, 1L);

        assertThat(iud).hasSize(IudGerador.TAMANHO).startsWith("CV3260615512345679");
        assertThat(iud).contains("99999" + "02" + "000000001" + "1234567890");
        assertThat(ST_DFE_ID.matcher(iud).matches()).isTrue();
        assertThat(KOWTS.matcher(iud).matches()).isTrue();
        assertThat(iud.charAt(44) - '0').isEqualTo(IudGerador.luhn(iud.substring(2, 44)));
    }

    @Test
    void tipo5ProduzOSegmento05() {
        String iud = new IudGerador(new AleatorioFixo(0L)).gerar(3, DATA, NIF, 99999, 5, 42L);

        assertThat(iud.substring(2 + 1 + 6 + 9 + 5, 2 + 1 + 6 + 9 + 5 + 2)).isEqualTo("05");
    }

    @Test
    void parteAleatoriaTemSempre10DigitosEMudaEntreChamadas() {
        IudGerador gerador = new IudGerador(new AleatorioFixo(7L, 9_999_999_999L));

        String primeiro = gerador.gerar(3, DATA, NIF, 99999, 2, 1L);
        String segundo = gerador.gerar(3, DATA, NIF, 99999, 2, 1L);

        assertThat(primeiro).isNotEqualTo(segundo);
        assertThat(primeiro.substring(34, 44)).isEqualTo("0000000007");
        assertThat(segundo.substring(34, 44)).isEqualTo("9999999999");
        assertThat(KOWTS.matcher(primeiro).matches()).isTrue();
        assertThat(KOWTS.matcher(segundo).matches()).isTrue();
    }

    @Test
    void construtorDeProducaoUsaSecureRandom() {
        String iud = new IudGerador().gerar(3, DATA, NIF, 99999, 2, 1L);

        assertThat(KOWTS.matcher(iud).matches()).isTrue();
    }

    @Test
    void recusaArgumentosForaDoLayout() {
        IudGerador g = new IudGerador(new AleatorioFixo(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L));

        assertThatThrownBy(() -> g.gerar(3, DATA, "012345678", 99999, 2, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> g.gerar(3, DATA, "51234567", 99999, 2, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> g.gerar(3, DATA, null, 99999, 2, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> g.gerar(3, DATA, NIF, 99999, 2, 1_000_000_000L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> g.gerar(3, DATA, NIF, 99999, 2, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> g.gerar(0, DATA, NIF, 99999, 2, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> g.gerar(4, DATA, NIF, 99999, 2, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> g.gerar(3, DATA, NIF, 0, 2, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> g.gerar(3, DATA, NIF, 100_000, 2, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> g.gerar(3, DATA, NIF, 99999, 0, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> g.gerar(3, DATA, NIF, 99999, 100, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> g.gerar(3, null, NIF, 99999, 2, 1L)).isInstanceOf(IllegalArgumentException.class);
    }
}
