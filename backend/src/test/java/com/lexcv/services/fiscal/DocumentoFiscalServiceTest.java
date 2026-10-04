package com.lexcv.services.fiscal;

import com.lexcv.dtos.DocumentoFiscalDetalheResponse;
import com.lexcv.dtos.DocumentoFiscalRef;
import com.lexcv.dtos.DocumentoFiscalResumoResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.ComunicacaoFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.repositories.ComunicacaoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalLigacaoClienteRepository;
import com.lexcv.repositories.DocumentoFiscalLinhaRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Phase 134 (EMIS-09, EMIS-11, EMIS-12, D-14..D-19): leitura, guardas e fusão dos documentos fiscais. */
class DocumentoFiscalServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private DocumentoFiscalRepository documentoRepo;
    private DocumentoFiscalLinhaRepository linhaRepo;
    private ComunicacaoFiscalRepository comunicacaoRepo;
    private DocumentoFiscalLigacaoClienteRepository ligacaoRepo;
    private DocumentoFiscalService servico;

    @BeforeEach
    void preparar() {
        documentoRepo = mock(DocumentoFiscalRepository.class);
        linhaRepo = mock(DocumentoFiscalLinhaRepository.class);
        comunicacaoRepo = mock(ComunicacaoFiscalRepository.class);
        ligacaoRepo = mock(DocumentoFiscalLigacaoClienteRepository.class);
        servico = new DocumentoFiscalService(documentoRepo, linhaRepo, comunicacaoRepo, ligacaoRepo);
    }

    private DocumentoFiscal doc(long numero, Integer pagamentoId) {
        return DocumentoFiscal.builder()
                .id(UUID.randomUUID()).tenantId(tenant).tipo(TipoDocumentoFiscal.FR).ambiente(AmbienteFiscal.SIMULADO)
                .serieId(UUID.randomUUID()).serieCodigo("SIM-FR-2026").ano(2026).numero(numero)
                .numeroFormatado("SIM-FR-2026/" + numero).dataEmissao(LocalDate.of(2026, 10, 4))
                .emitidoEm(Instant.parse("2026-10-04T12:00:00Z"))
                .emitenteNif("123456789").emitenteFirma("Silva").emitenteMorada("Av. 1").emitenteRegimeIva(RegimeIva.NORMAL)
                .adquirenteNif("234567891").adquirenteNome("Maria").adquirenteMorada("Rua 2")
                .clienteId(UUID.randomUUID()).processoId(UUID.randomUUID()).honorarioId(3).pagamentoId(pagamentoId)
                .metodoPagamento("TRANSFERENCIA").meioPagamentoCodigo("30").moeda("CVE")
                .taxaIva(new BigDecimal("15")).totalBase(new BigDecimal("100.00")).totalIva(new BigDecimal("15.00"))
                .totalRetencao(new BigDecimal("0.00")).totalDocumento(new BigDecimal("115.00"))
                .valorLiquido(new BigDecimal("115.00")).chaveIdempotencia(UUID.randomUUID())
                .emitidoPorId(UUID.randomUUID()).emitidoPorNome("Ana")
                .build();
    }

    private ComunicacaoFiscal comunicacao(DocumentoFiscal d) {
        return ComunicacaoFiscal.builder().tenantId(tenant).documentoFiscalId(d.getId())
                .ambiente(AmbienteFiscal.SIMULADO).estado(EstadoComunicacaoFiscal.PENDENTE).build();
    }

    // ------------------------------------------------------------------ listar

    @Test
    void listarPassaOsFiltrosComoTextoEUmaPaginaSemOrdenacao() {
        UUID cliente = UUID.randomUUID();
        LocalDate de = LocalDate.of(2026, 1, 1);
        LocalDate ate = LocalDate.of(2026, 12, 31);
        when(documentoRepo.buscar(any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        servico.listar(tenant, cliente, TipoDocumentoFiscal.FR, EstadoComunicacaoFiscal.PENDENTE, de, ate, 2, 25);

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(documentoRepo).buscar(eq(tenant), eq(cliente.toString()), eq("FR"), eq("PENDENTE"), eq(de), eq(ate),
                pagina.capture());
        assertEquals(2, pagina.getValue().getPageNumber());
        assertEquals(25, pagina.getValue().getPageSize());
        assertTrue(pagina.getValue().getSort().isUnsorted(), "a ordem é a da query nativa");
    }

    @Test
    void listarSemFiltrosPassaNulos() {
        when(documentoRepo.buscar(any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());
        servico.listar(tenant, null, null, null, null, null, 0, 10);
        verify(documentoRepo).buscar(eq(tenant), eq(null), eq(null), eq(null), eq(null), eq(null), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarHidrataEstadosNumaSoChamadaEMantemAOrdem() {
        DocumentoFiscal d1 = doc(3, 30);
        DocumentoFiscal d2 = doc(2, 20);
        DocumentoFiscal d3 = doc(1, 10);
        when(documentoRepo.buscar(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(d1, d2, d3), org.springframework.data.domain.PageRequest.of(0, 3), 7));
        when(comunicacaoRepo.findByTenantIdAndDocumentoFiscalIdIn(eq(tenant), any()))
                .thenReturn(List.of(comunicacao(d1), comunicacao(d3)));

        Page<DocumentoFiscalResumoResponse> r = servico.listar(tenant, null, null, null, null, null, 0, 3);

        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(comunicacaoRepo, times(1)).findByTenantIdAndDocumentoFiscalIdIn(eq(tenant), ids.capture());
        assertEquals(Set.of(d1.getId(), d2.getId(), d3.getId()), Set.copyOf(ids.getValue()));
        assertEquals(List.of(d1.getId(), d2.getId(), d3.getId()),
                r.getContent().stream().map(DocumentoFiscalResumoResponse::id).toList());
        assertEquals("PENDENTE", r.getContent().get(0).estadoComunicacao());
        assertNull(r.getContent().get(1).estadoComunicacao());
        assertEquals(7, r.getTotalElements());
        assertEquals("SIM-FR-2026/3", r.getContent().get(0).numeroFormatado());
    }

    @Test
    void listarPaginaVaziaNaoConsultaEstados() {
        when(documentoRepo.buscar(any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());
        assertTrue(servico.listar(tenant, null, null, null, null, null, 0, 10).isEmpty());
        verifyNoInteractions(comunicacaoRepo);
    }

    @Test
    void listarRecusaPaginacaoForaDosLimites() {
        assertThrows(IllegalArgumentException.class, () -> servico.listar(tenant, null, null, null, null, null, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> servico.listar(tenant, null, null, null, null, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> servico.listar(tenant, null, null, null, null, null, 0, 101));
        verifyNoInteractions(documentoRepo);
    }

    // ------------------------------------------------------------------ detalhe

    @Test
    void detalheDeOutroTenantOuInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(documentoRepo.findByIdAndTenantId(id, tenant)).thenReturn(Optional.empty());
        RecusaFiscalException e = assertThrows(RecusaFiscalException.class, () -> servico.detalhe(tenant, id));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("DOCUMENTO_FISCAL_NAO_ENCONTRADO", e.getCodigo());
        assertEquals("Documento fiscal não encontrado.", e.getMessage());
        verifyNoInteractions(linhaRepo, comunicacaoRepo);
    }

    @Test
    void detalheCarregaLinhasEEstadoComOMesmoTenant() {
        DocumentoFiscal d = doc(1, 10);
        DocumentoFiscalLinha linha = DocumentoFiscalLinha.builder().tenantId(tenant).documentoFiscalId(d.getId())
                .numeroLinha(1).descricao("Honorários por serviços jurídicos").quantidade(BigDecimal.ONE)
                .precoUnitario(new BigDecimal("100.00")).valorBase(new BigDecimal("100.00"))
                .taxaIva(new BigDecimal("15")).valorIva(new BigDecimal("15.00")).valorRetencao(new BigDecimal("0.00"))
                .totalLinha(new BigDecimal("115.00")).build();
        when(documentoRepo.findByIdAndTenantId(d.getId(), tenant)).thenReturn(Optional.of(d));
        when(linhaRepo.findByTenantIdAndDocumentoFiscalIdOrderByNumeroLinhaAsc(tenant, d.getId())).thenReturn(List.of(linha));
        when(comunicacaoRepo.findByTenantIdAndDocumentoFiscalId(tenant, d.getId())).thenReturn(Optional.of(comunicacao(d)));

        DocumentoFiscalDetalheResponse r = servico.detalhe(tenant, d.getId());

        assertEquals(d.getId(), r.id());
        assertEquals("PENDENTE", r.estadoComunicacao());
        assertEquals(1, r.linhas().size());
        assertEquals("Honorários por serviços jurídicos", r.linhas().get(0).descricao());
        assertEquals(com.lexcv.models.MetodoPagamento.TRANSFERENCIA.rotulo(), r.metodoPagamentoRotulo());
        assertEquals("Ana", r.emitidoPorNome());
        assertEquals("NORMAL", r.emitenteRegimeIva());
    }

    @Test
    void detalheNaoExpoeAChaveNemOIdDeQuemEmitiu() {
        Set<String> componentes = java.util.Arrays.stream(DocumentoFiscalDetalheResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        assertFalse(componentes.contains("chaveIdempotencia"));
        assertFalse(componentes.contains("emitidoPorId"));
        Set<String> resumo = java.util.Arrays.stream(DocumentoFiscalResumoResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        assertFalse(resumo.contains("chaveIdempotencia"));
        assertFalse(resumo.contains("emitidoPorId"));
    }

    // ------------------------------------------------------------------ referências por pagamento

    @Test
    void referenciasSemIdsNaoConsultam() {
        assertTrue(servico.referenciasPorPagamento(tenant, List.of()).isEmpty());
        assertTrue(servico.referenciasPorPagamento(tenant, null).isEmpty());
        verifyNoInteractions(documentoRepo);
    }

    @Test
    void referenciasNumaSoConsulta() {
        DocumentoFiscal d = doc(5, 50);
        when(documentoRepo.findByTenantIdAndPagamentoIdIn(eq(tenant), any())).thenReturn(List.of(d));

        Map<Integer, DocumentoFiscalRef> r = servico.referenciasPorPagamento(tenant, List.of(50, 51));

        verify(documentoRepo, times(1)).findByTenantIdAndPagamentoIdIn(eq(tenant), any());
        assertEquals(Map.of(50, new DocumentoFiscalRef(d.getId(), "SIM-FR-2026/5")), r);
        assertNull(r.get(51), "pagamento sem faturação fica sem documento");
    }

    // ------------------------------------------------------------------ guardas e fusão

    @Test
    void existeParaDelegaNosExistsDoTenant() {
        UUID cliente = UUID.randomUUID();
        UUID processo = UUID.randomUUID();
        when(documentoRepo.existsByTenantIdAndPagamentoId(tenant, 1)).thenReturn(true);
        when(documentoRepo.existsByTenantIdAndClienteId(tenant, cliente)).thenReturn(true);
        when(documentoRepo.existsByTenantIdAndProcessoId(tenant, processo)).thenReturn(false);
        when(documentoRepo.existsByTenantIdAndHonorarioId(tenant, 2)).thenReturn(true);

        assertTrue(servico.existeParaPagamento(tenant, 1));
        assertTrue(servico.existeParaCliente(tenant, cliente));
        assertFalse(servico.existeParaProcesso(tenant, processo));
        assertTrue(servico.existeParaHonorario(tenant, 2));
        assertFalse(servico.existeParaPagamento(tenant, 99));
    }

    @Test
    void repontarClienteDelegaEExigeTransacaoExistente() throws NoSuchMethodException {
        UUID antigo = UUID.randomUUID();
        UUID novo = UUID.randomUUID();
        when(ligacaoRepo.repontarCliente(tenant, antigo, novo)).thenReturn(4);
        assertEquals(4, servico.repontarCliente(tenant, antigo, novo));
        verify(ligacaoRepo).repontarCliente(tenant, antigo, novo);

        Method m = DocumentoFiscalService.class.getMethod("repontarCliente", UUID.class, UUID.class, UUID.class);
        Transactional t = m.getAnnotation(Transactional.class);
        assertNotNull(t);
        assertEquals(Propagation.MANDATORY, t.propagation());
    }

    @Test
    void leiturasSaoReadOnlyETodoMetodoPublicoRecebeTenantPrimeiro() {
        for (Method m : DocumentoFiscalService.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic()) {
                continue;
            }
            assertTrue(m.getParameterCount() > 0 && m.getParameterTypes()[0].equals(UUID.class),
                    m.getName() + " tem de receber o tenantId (UUID) como primeiro parâmetro");
            Transactional t = m.getAnnotation(Transactional.class);
            assertNotNull(t, m.getName() + " sem @Transactional");
            if (!m.getName().equals("repontarCliente")) {
                assertTrue(t.readOnly(), m.getName() + " tem de ser readOnly");
            }
        }
    }

    @Test
    void naoLeOContextoDeSeguranca() throws IOException {
        String fonte = Files.readString(Path.of("src/main/java/com/lexcv/services/fiscal/DocumentoFiscalService.java"));
        assertFalse(fonte.contains("SecurityContextHolder"));
    }
}
