package com.lexcv.jobs;

import com.lexcv.models.Evento;
import com.lexcv.models.Honorario;
import com.lexcv.models.Prazo;
import com.lexcv.models.Processo;
import com.lexcv.models.Tenant;
import com.lexcv.models.User;
import com.lexcv.repositories.EventoRepository;
import com.lexcv.repositories.HonorarioRepository;
import com.lexcv.repositories.NotificacaoRepository;
import com.lexcv.repositories.PrazoRepository;
import com.lexcv.repositories.ProcessoRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.NotificacaoService;
import com.lexcv.services.RiscoPrazoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito behavioral proof for {@link AlertasDiariosJob}: drives the package-private
 * {@code executar(LocalDate hoje)} overload directly with a FIXED {@code hoje} — never the
 * no-arg {@code executar()} — mirroring {@code RiscoPrazoServiceTest}'s fixed-date determinism
 * convention. All 7 repositories + {@link NotificacaoService} are mocked; {@link RiscoPrazoService}
 * is used REAL ({@code new RiscoPrazoService()}) since it has no collaborators, exactly like
 * {@code RiscoPrazoServiceTest} itself.
 *
 * <p>Crucially, NO {@code SecurityContextHolder}/{@code UserPrincipal} setup exists anywhere in
 * this class — its absence is itself the proof of Pitfall 1: if the job ever touched
 * {@code SecurityContextHolder}, it would NPE with no mock/context present to catch it.
 *
 * <p>{@code @MockitoSettings(strictness = Strictness.LENIENT)}: during the RED phase the job's
 * {@code executar(LocalDate)} body is empty, so most stubs configured here are never actually
 * invoked. STRICT_STUBS (MockitoExtension's default) would fail every test with
 * {@code UnnecessaryStubbingException} before Task 2 exists. LENIENT keeps the behavioral
 * {@code verify(...)} assertions below as the real gate in both RED and GREEN.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertasDiariosJobTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private ProcessoRepository processoRepository;
    @Mock
    private PrazoRepository prazoRepository;
    @Mock
    private EventoRepository eventoRepository;
    @Mock
    private HonorarioRepository honorarioRepository;
    @Mock
    private NotificacaoRepository notificacaoRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificacaoService notificacaoService;

    // RiscoPrazoService não tem colaboradores — instância real, nunca mockada (mesmo padrão de
    // RiscoPrazoServiceTest).
    private final RiscoPrazoService riscoPrazoService = new RiscoPrazoService();

    private static final LocalDate HOJE = LocalDate.of(2026, 1, 15);
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROCESSO_ID = UUID.randomUUID();
    private static final UUID RESPONSAVEL_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    private AlertasDiariosJob buildJob() {
        return new AlertasDiariosJob(tenantRepository, processoRepository, prazoRepository,
                eventoRepository, honorarioRepository, notificacaoRepository, userRepository,
                riscoPrazoService, notificacaoService);
    }

    private void semPrazos(UUID tenantId) {
        when(prazoRepository.findByTenantId(tenantId)).thenReturn(List.of());
    }

    private void semEventos(UUID tenantId) {
        when(eventoRepository.findByTenantIdAndConcluido(tenantId, false)).thenReturn(List.of());
    }

    private void semHonorarios() {
        when(honorarioRepository.findByProcessoIdIn(any())).thenReturn(List.of());
    }

    private void nuncaAntesNotificado() {
        when(notificacaoRepository.existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria(
                any(), any(), any(), any(), any())).thenReturn(false);
    }

    @Test
    void executar_prazoProximoComResponsavelDefinido_notificaResponsavelEAdmin() {
        Tenant tenant = Tenant.builder().id(TENANT_ID).nome("Tenant A").build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));

        Processo processo = Processo.builder().id(PROCESSO_ID).tenantId(TENANT_ID)
                .responsavelId(RESPONSAVEL_ID).numeroProcesso("PROC-0001").build();
        when(processoRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(processo));

        UUID prazoId = UUID.randomUUID();
        // 2 dias restantes, prioridade MEDIA (limiar não-ALTA = 3) => "proximo".
        Prazo prazo = Prazo.builder().id(prazoId).tenantId(TENANT_ID).processoId(PROCESSO_ID)
                .descricao("Contestação").dataLimite(HOJE.plusDays(2)).prioridade("MEDIA")
                .concluido(false).build();
        when(prazoRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(prazo));

        semEventos(TENANT_ID);
        semHonorarios();
        nuncaAntesNotificado();

        User admin = User.builder().id(ADMIN_ID).tenantId(TENANT_ID).build();
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));

        buildJob().executar(HOJE);

        verify(notificacaoService, times(1)).criar(eq(TENANT_ID), eq(RESPONSAVEL_ID), eq("PRAZO_PROXIMO"),
                anyString(), anyString(), eq("prazo"), eq(prazoId.toString()), anyString());
        verify(notificacaoService, times(1)).criar(eq(TENANT_ID), eq(ADMIN_ID), eq("PRAZO_PROXIMO"),
                anyString(), anyString(), eq("prazo"), eq(prazoId.toString()), anyString());
        verify(notificacaoService, times(2)).criar(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void executar_notificacaoJaExistenteParaOMesmoNivel_naoDuplicaNotificacao() {
        Tenant tenant = Tenant.builder().id(TENANT_ID).nome("Tenant A").build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));

        Processo processo = Processo.builder().id(PROCESSO_ID).tenantId(TENANT_ID)
                .responsavelId(RESPONSAVEL_ID).numeroProcesso("PROC-0001").build();
        when(processoRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(processo));

        UUID prazoId = UUID.randomUUID();
        Prazo prazo = Prazo.builder().id(prazoId).tenantId(TENANT_ID).processoId(PROCESSO_ID)
                .descricao("Contestação").dataLimite(HOJE.plusDays(2)).prioridade("MEDIA")
                .concluido(false).build();
        when(prazoRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(prazo));

        semEventos(TENANT_ID);
        semHonorarios();
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN")).thenReturn(List.of());
        // Simula uma corrida anterior já ter notificado este nível exato para todo mundo —
        // correr o job de novo sobre dados inalterados não deve criar nada novo.
        when(notificacaoRepository.existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria(
                any(), any(), any(), any(), any())).thenReturn(true);

        buildJob().executar(HOJE);

        verify(notificacaoService, never()).criar(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void executar_prazoCruzaDeProximoParaVencido_criaApenasNotificacaoDoNovoNivel() {
        Tenant tenant = Tenant.builder().id(TENANT_ID).nome("Tenant A").build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));

        Processo processo = Processo.builder().id(PROCESSO_ID).tenantId(TENANT_ID)
                .responsavelId(RESPONSAVEL_ID).numeroProcesso("PROC-0001").build();
        when(processoRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(processo));

        UUID prazoId = UUID.randomUUID();
        // dataLimite ontem => "vencido", independentemente de já ter sido "proximo" antes.
        Prazo prazo = Prazo.builder().id(prazoId).tenantId(TENANT_ID).processoId(PROCESSO_ID)
                .descricao("Contestação").dataLimite(HOJE.minusDays(1)).prioridade("MEDIA")
                .concluido(false).build();
        when(prazoRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(prazo));

        semEventos(TENANT_ID);
        semHonorarios();
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN")).thenReturn(List.of());

        // Já existe notificação PRAZO_PROXIMO (nível anterior) mas NÃO existe ainda PRAZO_VENCIDO
        // (o novo nível) -- a mudança de estado deve gerar exatamente uma notificação nova.
        when(notificacaoRepository.existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria(
                TENANT_ID, RESPONSAVEL_ID, "prazo", prazoId.toString(), "PRAZO_PROXIMO")).thenReturn(true);
        when(notificacaoRepository.existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria(
                TENANT_ID, RESPONSAVEL_ID, "prazo", prazoId.toString(), "PRAZO_VENCIDO")).thenReturn(false);

        buildJob().executar(HOJE);

        verify(notificacaoService, times(1)).criar(eq(TENANT_ID), eq(RESPONSAVEL_ID), eq("PRAZO_VENCIDO"),
                anyString(), anyString(), eq("prazo"), eq(prazoId.toString()), anyString());
        verify(notificacaoService, never()).criar(eq(TENANT_ID), eq(RESPONSAVEL_ID), eq("PRAZO_PROXIMO"),
                anyString(), anyString(), eq("prazo"), eq(prazoId.toString()), anyString());
    }

    @Test
    void executar_umTenantLancaExcecao_outroTenantAindaEhProcessadoENenhumaExcecaoEscapa() {
        UUID tenantB = UUID.randomUUID();
        Tenant tenantAEntity = Tenant.builder().id(TENANT_ID).nome("Tenant A").build();
        Tenant tenantBEntity = Tenant.builder().id(tenantB).nome("Tenant B").build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenantAEntity, tenantBEntity));

        // Tenant A explode logo na primeira leitura do seu scan.
        when(processoRepository.findByTenantId(TENANT_ID)).thenThrow(new RuntimeException("boom"));

        // Tenant B deve ser processado normalmente, apesar da falha do Tenant A.
        UUID processoBId = UUID.randomUUID();
        UUID responsavelB = UUID.randomUUID();
        Processo processoB = Processo.builder().id(processoBId).tenantId(tenantB)
                .responsavelId(responsavelB).numeroProcesso("PROC-B").build();
        when(processoRepository.findByTenantId(tenantB)).thenReturn(List.of(processoB));

        UUID prazoBId = UUID.randomUUID();
        Prazo prazoB = Prazo.builder().id(prazoBId).tenantId(tenantB).processoId(processoBId)
                .descricao("Prazo B").dataLimite(HOJE.plusDays(1)).prioridade("ALTA")
                .concluido(false).build();
        when(prazoRepository.findByTenantId(tenantB)).thenReturn(List.of(prazoB));

        semEventos(tenantB);
        when(honorarioRepository.findByProcessoIdIn(any())).thenReturn(List.of());
        when(userRepository.findByTenantIdAndRoleName(tenantB, "ADMIN")).thenReturn(List.of());
        nuncaAntesNotificado();

        assertDoesNotThrow(() -> buildJob().executar(HOJE));

        verify(processoRepository, times(1)).findByTenantId(tenantB);
        verify(notificacaoService, times(1)).criar(eq(tenantB), eq(responsavelB), eq("PRAZO_PROXIMO"),
                anyString(), anyString(), eq("prazo"), eq(prazoBId.toString()), anyString());
    }

    @Test
    void executar_honorarioValorTotalNulo_ignoradoSemExcecao() {
        Tenant tenant = Tenant.builder().id(TENANT_ID).nome("Tenant A").build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));

        Processo processo = Processo.builder().id(PROCESSO_ID).tenantId(TENANT_ID)
                .responsavelId(RESPONSAVEL_ID).numeroProcesso("PROC-0001").build();
        when(processoRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(processo));

        semPrazos(TENANT_ID);
        semEventos(TENANT_ID);

        // valorTotal null: honorário ainda não preenchido na formalização (Phase 82 decision) —
        // deve ser ignorado silenciosamente, nunca lançar.
        Honorario honorario = Honorario.builder().id(1).processoId(PROCESSO_ID)
                .valorTotal(null).dataAcordo(HOJE.minusDays(60)).totalPago(BigDecimal.ZERO).build();
        when(honorarioRepository.findByProcessoIdIn(any())).thenReturn(List.of(honorario));

        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN")).thenReturn(List.of());

        assertDoesNotThrow(() -> buildJob().executar(HOJE));

        verify(notificacaoService, never()).criar(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void executar_honorarioAtrasado30DiasNaoPago_notificaApenasEsseHonorario() {
        Tenant tenant = Tenant.builder().id(TENANT_ID).nome("Tenant A").build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));

        Processo processo = Processo.builder().id(PROCESSO_ID).tenantId(TENANT_ID)
                .responsavelId(RESPONSAVEL_ID).numeroProcesso("PROC-0001").build();
        when(processoRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(processo));

        semPrazos(TENANT_ID);
        semEventos(TENANT_ID);

        // Pago integralmente (apesar de muito antigo) => ignorado.
        Honorario pagoIntegralmente = Honorario.builder().id(1).processoId(PROCESSO_ID)
                .valorTotal(new BigDecimal("1000.00")).dataAcordo(HOJE.minusDays(90))
                .totalPago(new BigDecimal("1000.00")).build();
        // Exatamente 30 dias sem pagamento total => notifica (limiar inclusivo, ">= 30").
        Honorario atrasado = Honorario.builder().id(2).processoId(PROCESSO_ID)
                .valorTotal(new BigDecimal("500.00")).dataAcordo(HOJE.minusDays(30))
                .totalPago(BigDecimal.ZERO).build();
        // Só 10 dias sem pagamento total => ainda não atinge o limiar, ignorado.
        Honorario recenteNaoPago = Honorario.builder().id(3).processoId(PROCESSO_ID)
                .valorTotal(new BigDecimal("200.00")).dataAcordo(HOJE.minusDays(10))
                .totalPago(BigDecimal.ZERO).build();
        when(honorarioRepository.findByProcessoIdIn(any()))
                .thenReturn(List.of(pagoIntegralmente, atrasado, recenteNaoPago));

        nuncaAntesNotificado();
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN")).thenReturn(List.of());

        buildJob().executar(HOJE);

        verify(notificacaoService, times(1)).criar(eq(TENANT_ID), eq(RESPONSAVEL_ID), eq("HONORARIO_ATRASADO"),
                anyString(), anyString(), eq("honorario"), eq("2"), anyString());
        // Confirma que NENHUM outro honorário (pago ou recente) gerou notificação.
        verify(notificacaoService, times(1)).criar(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void executar_eventoSemProcessoIdCritico_notificaApenasAdmins() {
        Tenant tenant = Tenant.builder().id(TENANT_ID).nome("Tenant A").build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));

        when(processoRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());
        semPrazos(TENANT_ID);
        semHonorarios();

        // Evento sem processoId (ex.: reunião interna) mas crítico -- sem Processo não há
        // responsavel a notificar, mas o fan-out ADMIN deve continuar a disparar (null-safe).
        Evento evento = Evento.builder().id(9).tenantId(TENANT_ID).processoId(null)
                .titulo("Audiência Geral").dataInicio(HOJE.plusDays(1).atTime(9, 0))
                .prioridade("ALTA").concluido(false).build();
        when(eventoRepository.findByTenantIdAndConcluido(TENANT_ID, false)).thenReturn(List.of(evento));

        User admin = User.builder().id(ADMIN_ID).tenantId(TENANT_ID).build();
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        nuncaAntesNotificado();

        buildJob().executar(HOJE);

        verify(notificacaoService, times(1)).criar(eq(TENANT_ID), eq(ADMIN_ID), eq("EVENTO_PROXIMO"),
                anyString(), anyString(), eq("evento"), eq("9"), anyString());
        // Total de 1 chamada -- prova que nenhuma notificação "responsavel" foi tentada (não há
        // Processo para resolver um responsavelId).
        verify(notificacaoService, times(1)).criar(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
