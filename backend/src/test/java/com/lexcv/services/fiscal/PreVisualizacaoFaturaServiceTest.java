package com.lexcv.services.fiscal;

import com.lexcv.dtos.EstadoEmissaoResponse;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.dtos.PreVisualizacaoFaturaResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.Cliente;
import com.lexcv.models.CodigoParametroFiscal;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.Honorario;
import com.lexcv.models.Processo;
import com.lexcv.models.RegimeIva;
import com.lexcv.repositories.ClienteRepository;
import com.lexcv.repositories.ConfiguracaoFiscalRepository;
import com.lexcv.repositories.HonorarioRepository;
import com.lexcv.repositories.ProcessoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Phase 134 (EMIS-02, D-01, D-03, R-05): pré-visualização sem efeitos e estado de emissão. */
class PreVisualizacaoFaturaServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 4);

    private final UUID tenant = UUID.randomUUID();
    private ConfiguracaoFiscalRepository configuracaoRepo;
    private HonorarioRepository honorarioRepo;
    private ProcessoRepository processoRepo;
    private ClienteRepository clienteRepo;
    private ParametroFiscalService parametros;

    private Cliente cliente;
    private Processo processo;
    private Honorario honorario;

    @BeforeEach
    void preparar() {
        configuracaoRepo = mock(ConfiguracaoFiscalRepository.class);
        honorarioRepo = mock(HonorarioRepository.class);
        processoRepo = mock(ProcessoRepository.class);
        clienteRepo = mock(ClienteRepository.class);
        parametros = mock(ParametroFiscalService.class);

        cliente = Cliente.builder().id(UUID.randomUUID()).tenantId(tenant).nif("234567891")
                .nome("Maria Lopes").morada("Rua da Praia 5").build();
        processo = Processo.builder().id(UUID.randomUUID()).tenantId(tenant).clienteId(cliente.getId())
                .numeroProcesso("P-1").build();
        honorario = new Honorario();
        honorario.setId(7);
        honorario.setProcessoId(processo.getId());
        honorario.setDescricao("SEGREDO PROFISSIONAL");
    }

    private PreVisualizacaoFaturaService servico(Clock clock) {
        return new PreVisualizacaoFaturaService(configuracaoRepo, honorarioRepo, processoRepo, clienteRepo,
                parametros, clock);
    }

    private static ConfiguracaoFiscal.ConfiguracaoFiscalBuilder cfg(RegimeIva regime, boolean ativa) {
        return ConfiguracaoFiscal.builder().nif("123456789").firma("Silva").morada("Av. 1").localidade("Praia")
                .emailContacto("a@b.cv").telefoneContacto("260").regimeIva(regime)
                .motivoIsencaoCodigo(regime == RegimeIva.ISENTO ? "3" : null).ativa(ativa);
    }

    private void tudoPresente(ConfiguracaoFiscal c) {
        when(configuracaoRepo.findByTenantId(tenant)).thenReturn(Optional.of(c));
        when(honorarioRepo.findById(7)).thenReturn(Optional.of(honorario));
        when(processoRepo.findById(processo.getId())).thenReturn(Optional.of(processo));
        when(clienteRepo.findById(cliente.getId())).thenReturn(Optional.of(cliente));
    }

    private static PagamentoRequest req(Integer honorarioId) {
        return new PagamentoRequest(honorarioId, new BigDecimal("120000"), null, "TRANSFERENCIA",
                new BigDecimal("20"), null);
    }

    private static void assertRecusa(RecusaFiscalException e, HttpStatus status, String codigo) {
        assertEquals(status, e.getStatus());
        assertEquals(codigo, e.getCodigo());
    }

    // ------------------------------------------------------------------ preVisualizar

    @Test
    void normalDevolveOsValoresCalculadosNoServidor() {
        tudoPresente(cfg(RegimeIva.NORMAL, true).build());
        when(parametros.valorVigente(CodigoParametroFiscal.IVA_TAXA_NORMAL, HOJE)).thenReturn(new BigDecimal("15.0000"));

        PreVisualizacaoFaturaResponse r = servico(RELOGIO).preVisualizar(tenant, req(7));

        assertEquals(new BigDecimal("104347.83"), r.base());
        assertEquals(new BigDecimal("15652.17"), r.iva());
        assertEquals(new BigDecimal("20869.57"), r.retencao());
        assertEquals(new BigDecimal("99130.43"), r.liquidoRecebido());
        assertEquals("Maria Lopes", r.adquirenteNome());
        assertEquals("234567891", r.adquirenteNif());
        assertEquals(HOJE, r.dataEmissao());
        assertFalse(r.descricaoLinha().contains("SEGREDO"));
    }

    @Test
    void preVisualizacaoNaoTemEfeitos() {
        tudoPresente(cfg(RegimeIva.NORMAL, true).build());
        when(parametros.valorVigente(CodigoParametroFiscal.IVA_TAXA_NORMAL, HOJE)).thenReturn(new BigDecimal("15"));

        servico(RELOGIO).preVisualizar(tenant, req(7));

        verify(configuracaoRepo).findByTenantId(tenant);
        verify(honorarioRepo).findById(7);
        verify(processoRepo).findById(processo.getId());
        verify(clienteRepo).findById(cliente.getId());
        verify(parametros).valorVigente(CodigoParametroFiscal.IVA_TAXA_NORMAL, HOJE);
        // nenhuma outra chamada: nem save, nem bloquear*, nem criarSeNaoExiste, nem numeração
        verifyNoMoreInteractions(configuracaoRepo, honorarioRepo, processoRepo, clienteRepo, parametros);
    }

    @Test
    void metodosSaoReadOnly() throws NoSuchMethodException {
        for (Method m : new Method[]{
                PreVisualizacaoFaturaService.class.getMethod("preVisualizar", UUID.class, PagamentoRequest.class),
                PreVisualizacaoFaturaService.class.getMethod("estadoEmissao", UUID.class)}) {
            Transactional t = m.getAnnotation(Transactional.class);
            assertNotNull(t, m.getName() + " sem @Transactional");
            assertTrue(t.readOnly(), m.getName() + " tem de ser readOnly");
        }
    }

    @Test
    void isentoNuncaLeATaxaDeIva() {
        tudoPresente(cfg(RegimeIva.ISENTO, true).build());

        PreVisualizacaoFaturaResponse r = servico(RELOGIO).preVisualizar(tenant, req(7));

        assertEquals(new BigDecimal("120000.00"), r.base());
        assertEquals(new BigDecimal("0.00"), r.iva());
        assertEquals("3", r.motivoIsencaoCodigo());
        verifyNoInteractions(parametros);
    }

    @Test
    void faturacaoAusenteOuDesligadaRecusa409() {
        when(configuracaoRepo.findByTenantId(tenant)).thenReturn(Optional.empty());
        assertRecusa(assertThrows(RecusaFiscalException.class, () -> servico(RELOGIO).preVisualizar(tenant, req(7))),
                HttpStatus.CONFLICT, "FATURACAO_DESLIGADA");

        when(configuracaoRepo.findByTenantId(tenant)).thenReturn(Optional.of(cfg(RegimeIva.NORMAL, false).build()));
        assertRecusa(assertThrows(RecusaFiscalException.class, () -> servico(RELOGIO).preVisualizar(tenant, req(7))),
                HttpStatus.CONFLICT, "FATURACAO_DESLIGADA");
        verifyNoInteractions(honorarioRepo, parametros);
    }

    @Test
    void honorarioObrigatorio() {
        when(configuracaoRepo.findByTenantId(tenant)).thenReturn(Optional.of(cfg(RegimeIva.NORMAL, true).build()));
        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> servico(RELOGIO).preVisualizar(tenant, req(null)));
        assertRecusa(e, HttpStatus.UNPROCESSABLE_ENTITY, "HONORARIO_OBRIGATORIO");
        assertEquals("honorarioId", e.getCampo());
    }

    @Test
    void honorarioAusenteOuDeOutroTenantDaOMesmo404() {
        when(configuracaoRepo.findByTenantId(tenant)).thenReturn(Optional.of(cfg(RegimeIva.NORMAL, true).build()));
        when(honorarioRepo.findById(7)).thenReturn(Optional.empty());
        RecusaFiscalException ausente = assertThrows(RecusaFiscalException.class,
                () -> servico(RELOGIO).preVisualizar(tenant, req(7)));
        assertRecusa(ausente, HttpStatus.NOT_FOUND, "HONORARIO_NAO_ENCONTRADO");

        Processo alheio = Processo.builder().id(processo.getId()).tenantId(UUID.randomUUID())
                .clienteId(cliente.getId()).build();
        when(honorarioRepo.findById(7)).thenReturn(Optional.of(honorario));
        when(processoRepo.findById(processo.getId())).thenReturn(Optional.of(alheio));
        RecusaFiscalException deOutro = assertThrows(RecusaFiscalException.class,
                () -> servico(RELOGIO).preVisualizar(tenant, req(7)));
        assertRecusa(deOutro, HttpStatus.NOT_FOUND, "HONORARIO_NAO_ENCONTRADO");
        assertEquals(ausente.getMessage(), deOutro.getMessage());

        when(processoRepo.findById(processo.getId())).thenReturn(Optional.empty());
        assertRecusa(assertThrows(RecusaFiscalException.class, () -> servico(RELOGIO).preVisualizar(tenant, req(7))),
                HttpStatus.NOT_FOUND, "HONORARIO_NAO_ENCONTRADO");
        verifyNoInteractions(clienteRepo, parametros);
    }

    @Test
    void clienteAusenteOuDeOutroTenant404() {
        tudoPresente(cfg(RegimeIva.NORMAL, true).build());
        when(clienteRepo.findById(cliente.getId())).thenReturn(Optional.empty());
        assertRecusa(assertThrows(RecusaFiscalException.class, () -> servico(RELOGIO).preVisualizar(tenant, req(7))),
                HttpStatus.NOT_FOUND, "CLIENTE_NAO_ENCONTRADO");

        cliente.setTenantId(UUID.randomUUID());
        when(clienteRepo.findById(cliente.getId())).thenReturn(Optional.of(cliente));
        assertRecusa(assertThrows(RecusaFiscalException.class, () -> servico(RELOGIO).preVisualizar(tenant, req(7))),
                HttpStatus.NOT_FOUND, "CLIENTE_NAO_ENCONTRADO");
        verifyNoInteractions(parametros);
    }

    @Test
    void hojeEADataDeCaboVerdeEAMesmaParaValidarEParaATaxa() {
        tudoPresente(cfg(RegimeIva.NORMAL, true).build());
        LocalDate dia4 = LocalDate.of(2026, 10, 4);
        LocalDate dia5 = LocalDate.of(2026, 10, 5);
        when(parametros.valorVigente(any(), any())).thenReturn(new BigDecimal("15"));

        // 00:59:59Z em Cabo Verde (UTC-1) ainda é dia 4
        Clock antes = Clock.fixed(Instant.parse("2026-10-05T00:59:59Z"), ZoneOffset.UTC);
        assertEquals(dia4, servico(antes).preVisualizar(tenant, req(7)).dataEmissao());
        verify(parametros).valorVigente(CodigoParametroFiscal.IVA_TAXA_NORMAL, dia4);
        PagamentoRequest comDia5 = new PagamentoRequest(7, new BigDecimal("10"), dia5, "DINHEIRO", null, null);
        assertRecusa(assertThrows(RecusaFiscalException.class, () -> servico(antes).preVisualizar(tenant, comDia5)),
                HttpStatus.UNPROCESSABLE_ENTITY, "DATA_PAGAMENTO_RETROATIVA");

        // 01:00:01Z já é dia 5
        Clock depois = Clock.fixed(Instant.parse("2026-10-05T01:00:01Z"), ZoneOffset.UTC);
        assertEquals(dia5, servico(depois).preVisualizar(tenant, comDia5).dataEmissao());
        verify(parametros).valorVigente(CodigoParametroFiscal.IVA_TAXA_NORMAL, dia5);
    }

    // ------------------------------------------------------------------ estadoEmissao

    @Test
    void estadoDesligadoNaoLeParametros() {
        when(configuracaoRepo.ativaPorTenant(tenant)).thenReturn(Optional.empty());
        EstadoEmissaoResponse semConfig = servico(RELOGIO).estadoEmissao(tenant);
        assertFalse(semConfig.ativa());
        assertNull(semConfig.ambiente());
        assertNull(semConfig.taxaRetencaoSugerida());

        when(configuracaoRepo.ativaPorTenant(tenant)).thenReturn(Optional.of(false));
        EstadoEmissaoResponse desligada = servico(RELOGIO).estadoEmissao(tenant);
        assertFalse(desligada.ativa());
        assertNull(desligada.ambiente());
        assertNull(desligada.taxaRetencaoSugerida());
        verifyNoInteractions(parametros);
    }

    @Test
    void estadoAtivoTrazAmbienteEATaxaSugeridaDoParametro() {
        when(configuracaoRepo.ativaPorTenant(tenant)).thenReturn(Optional.of(true));
        when(parametros.valorVigente(CodigoParametroFiscal.RETENCAO_SUGERIDA, HOJE)).thenReturn(new BigDecimal("17.5000"));

        EstadoEmissaoResponse r = servico(RELOGIO).estadoEmissao(tenant);

        assertTrue(r.ativa());
        assertEquals("SIMULADO", r.ambiente());
        assertEquals(new BigDecimal("17.5000"), r.taxaRetencaoSugerida());
        verify(configuracaoRepo).ativaPorTenant(tenant);
        verifyNoMoreInteractions(configuracaoRepo);
    }
}
