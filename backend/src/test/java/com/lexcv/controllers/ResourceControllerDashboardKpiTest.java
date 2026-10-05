package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.DashboardKpiResponse;
import com.lexcv.models.Honorario;
import com.lexcv.models.Pagamento;
import com.lexcv.models.Processo;
import com.lexcv.repositories.ContaCorrenteRepository;
import com.lexcv.repositories.HonorarioRepository;
import com.lexcv.repositories.PagamentoRepository;
import com.lexcv.repositories.ProcessoRepository;
import com.lexcv.services.fiscal.DocumentoFiscalService;
import com.lexcv.services.fiscal.PagamentoFaturadoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 135 (NCRD-03, P-14): o KPI {@code valores_recebidos_mes} do {@code GET /dashboard} soma só os
 * pagamentos do ano E mês correntes em Atlantic/Cape_Verde, ignora valores nulos e o estorno de uma
 * Nota de Crédito subtrai no mês da sua emissão.
 */
class ResourceControllerDashboardKpiTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    private HonorarioRepository honorarioRepository;
    private ProcessoRepository processoRepository;
    private PagamentoRepository pagamentoRepository;
    private PagamentoFaturadoService pagamentoFaturadoService;
    private ResourceController controller;

    /** IN-04 da revisão: o mês vem do Clock injetado (via o serviço), por isso fica fixo no teste. */
    private static final YearMonth MES = YearMonth.of(2026, 6);
    private static final LocalDate HOJE = LocalDate.of(2026, 6, 15);

    @BeforeEach
    void preparar() {
        honorarioRepository = mock(HonorarioRepository.class);
        processoRepository = mock(ProcessoRepository.class);
        pagamentoRepository = mock(PagamentoRepository.class);
        pagamentoFaturadoService = mock(PagamentoFaturadoService.class);
        when(pagamentoFaturadoService.mesCorrente()).thenReturn(MES);
        controller = ResourceControllerPagamentoTest.novoController(honorarioRepository, processoRepository,
                pagamentoRepository, mock(ContaCorrenteRepository.class), pagamentoFaturadoService,
                mock(DocumentoFiscalService.class));

        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID(), TENANT_ID, "Ana", "ana@example.cv",
                Set.of(), Set.of("processos:view"), Set.of());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private static Pagamento pag(int id, LocalDate data, String valor) {
        return Pagamento.builder().id(id).honorarioId(5).dataPagamento(data)
                .valorPago(valor == null ? null : new BigDecimal(valor)).metodo("DINHEIRO").build();
    }

    private void umHonorarioCom(List<Pagamento> pagamentos) {
        Processo processo = Processo.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).estado("ATIVO").build();
        Honorario hon = new Honorario();
        hon.setId(5);
        hon.setProcessoId(processo.getId());
        when(processoRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(processo));
        when(honorarioRepository.findByProcessoId(processo.getId())).thenReturn(List.of(hon));
        when(pagamentoRepository.findByHonorarioId(5)).thenReturn(pagamentos);
    }

    private BigDecimal recebidoNoMes() {
        ResponseEntity<?> r = controller.getDashboard();
        assertEquals(HttpStatus.OK, r.getStatusCode());
        DashboardKpiResponse kpis = (DashboardKpiResponse) r.getBody();
        assertNotNull(kpis);
        return kpis.getValores_recebidos_mes();
    }

    @Test
    void contaSoOAnoEMesCorrentesDeCaboVerdeESubtraiOEstorno() {
        umHonorarioCom(List.of(
                pag(1, HOJE, "500"),
                pag(2, HOJE.minusYears(1), "900"),   // mesmo mês do ano passado: não conta
                pag(3, HOJE, "-200"),                 // estorno de uma NC emitida hoje
                pag(4, HOJE.plusMonths(1), "70")));   // mês seguinte: não conta

        assertEquals(0, new BigDecimal("300").compareTo(recebidoNoMes()));
    }

    @Test
    void valorOuDataNulosNaoRebentamOKpi() {
        umHonorarioCom(List.of(pag(1, HOJE, "500"), pag(2, HOJE, null), pag(3, null, "40")));

        assertEquals(0, new BigDecimal("500").compareTo(recebidoNoMes()));
    }

    @Test
    void semProcessosDaZero() {
        when(processoRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        assertEquals(0, BigDecimal.ZERO.compareTo(recebidoNoMes()));
    }
}
