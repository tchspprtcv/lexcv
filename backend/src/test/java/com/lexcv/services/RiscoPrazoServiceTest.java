package com.lexcv.services;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Teste de caracterização (JUnit 5 puro, sem contexto Spring) da tabela de limiares
 * 7d-ALTA / 3d-outros usada por {@link RiscoPrazoService}. RiscoPrazoService não tem
 * dependências, logo não precisa de contexto Spring nem injeção de dependências no teste.
 *
 * `hoje` é fixado em 2026-01-15 para determinismo nos casos que usam o overload de 3 args;
 * os casos do overload de 2 args (que delega em LocalDate.now()) usam apenas cenários
 * estáveis independentes da data corrente (null e "ontem").
 */
class RiscoPrazoServiceTest {

    private final RiscoPrazoService service = new RiscoPrazoService();
    private static final LocalDate HOJE = LocalDate.of(2026, 1, 15);

    // ==========================================
    // computeRisco(LocalDate, String, LocalDate) — overload de 3 args, hoje fixo
    // ==========================================

    @Test
    void computeRisco_dataLimiteNull_retornaOk() {
        assertEquals("ok", service.computeRisco(null, "ALTA", HOJE));
    }

    @Test
    void computeRisco_dataLimiteAnteriorAHoje_retornaVencidoIndependenteDaPrioridade() {
        assertEquals("vencido", service.computeRisco(LocalDate.of(2026, 1, 14), "ALTA", HOJE));
        assertEquals("vencido", service.computeRisco(LocalDate.of(2026, 1, 14), "MEDIA", HOJE));
    }

    @Test
    void computeRisco_dataLimiteHoje_retornaProximo() {
        // diasRestantes = 0, inclusivo em ambos os limiares (<=7 e <=3)
        assertEquals("proximo", service.computeRisco(HOJE, "ALTA", HOJE));
        assertEquals("proximo", service.computeRisco(HOJE, "MEDIA", HOJE));
    }

    @Test
    void computeRisco_diaLimiarExatoAlta7Dias_retornaProximo() {
        assertEquals("proximo", service.computeRisco(LocalDate.of(2026, 1, 22), "ALTA", HOJE));
    }

    @Test
    void computeRisco_umDiaAposLimiarAlta8Dias_retornaOk() {
        assertEquals("ok", service.computeRisco(LocalDate.of(2026, 1, 23), "ALTA", HOJE));
    }

    @Test
    void computeRisco_diaLimiarExatoNaoAlta3Dias_retornaProximo() {
        assertEquals("proximo", service.computeRisco(LocalDate.of(2026, 1, 18), "MEDIA", HOJE));
    }

    @Test
    void computeRisco_umDiaAposLimiarNaoAlta4Dias_retornaOk() {
        assertEquals("ok", service.computeRisco(LocalDate.of(2026, 1, 19), "MEDIA", HOJE));
    }

    @Test
    void computeRisco_prioridadeAltaMinuscula_insensivelAMaiusculas() {
        // 5 dias: se equalsIgnoreCase falhasse, "alta" seria tratado como não-ALTA (limiar 3) => "ok"
        assertEquals("proximo", service.computeRisco(LocalDate.of(2026, 1, 20), "alta", HOJE));
    }

    @Test
    void computeRisco_prioridadeNula_tratadaComoLimiarNaoAlta() {
        // 5 dias > limiar 3 (não-ALTA, null tratado como não-ALTA) => ok
        assertEquals("ok", service.computeRisco(LocalDate.of(2026, 1, 20), null, HOJE));
    }

    // ==========================================
    // computeRisco(LocalDate, String) — overload de 2 args, delega para hoje = LocalDate.now()
    // Casos estáveis independentes da data corrente.
    // ==========================================

    @Test
    void computeRisco2Args_dataNull_retornaOk() {
        assertEquals("ok", service.computeRisco(null, "ALTA"));
    }

    @Test
    void computeRisco2Args_dataOntem_retornaVencido() {
        assertEquals("vencido", service.computeRisco(LocalDate.now().minusDays(1), "MEDIA"));
    }

    // ==========================================
    // computeRiscoEvento(LocalDateTime, String, LocalDate) — conversão .toLocalDate()
    // ==========================================

    @Test
    void computeRiscoEvento_dataEventoNull_retornaOk() {
        assertEquals("ok", service.computeRiscoEvento(null, "ALTA", HOJE));
    }

    @Test
    void computeRiscoEvento_convertLocalDateTimeParaLocalDate_igualAComputeRisco() {
        // Equivalente a computeRisco(2026-01-20, "alta", hoje): 5 dias <= 7 (ALTA) => proximo
        assertEquals("proximo", service.computeRiscoEvento(LocalDateTime.of(2026, 1, 20, 9, 0), "alta", HOJE));
    }

    @Test
    void computeRiscoEvento_dataPassadaComHoraTardia_aindaVencido() {
        // Data passada (01-14) apesar da hora tardia (23:59) — .toLocalDate() descarta a hora
        assertEquals("vencido", service.computeRiscoEvento(LocalDateTime.of(2026, 1, 14, 23, 59), "MEDIA", HOJE));
    }

    @Test
    void computeRiscoEvento_mesmoDiaHojeComHoraTardia_nuncaVencidoSempreProximo() {
        // Evento mais tarde HOJE (01-15 23:59): same-day nunca é "vencido"; diasRestantes=0, 0<=3
        assertEquals("proximo", service.computeRiscoEvento(LocalDateTime.of(2026, 1, 15, 23, 59), "MEDIA", HOJE));
    }
}
