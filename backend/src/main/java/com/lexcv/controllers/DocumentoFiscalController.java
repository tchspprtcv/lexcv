package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.DocumentoFiscalResumoResponse;
import com.lexcv.dtos.NotaCreditoRequest;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.fiscal.efatura.EfaturaGateway;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.services.fiscal.DescargaDocumentoFiscalService;
import com.lexcv.services.fiscal.DescargaDocumentoFiscalTransacoes;
import com.lexcv.services.fiscal.DocumentoFiscalService;
import com.lexcv.services.fiscal.NotaCreditoService;
import com.lexcv.services.fiscal.PreVisualizacaoFaturaService;
import com.lexcv.services.fiscal.ReenvioEmailFiscalService;
import com.lexcv.services.fiscal.RelatorioMensalFiscalService;
import com.lexcv.services.fiscal.ReprocessamentoComunicacaoService;
import com.lexcv.services.fiscal.ResultadoNotaCredito;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Phase 134 (D-01, D-16, D-17, D-18, R-05; EMIS-02, EMIS-11): leitura e pré-visualização dos
 * documentos fiscais do escritório do chamador.
 *
 * <ul>
 *   <li>{@code GET /api/v1/faturacao/estado-emissao}: a faturação está ativa? Ambiente e taxa de
 *       retenção sugerida, para o formulário de pagamento mudar de modo.</li>
 *   <li>{@code POST /api/v1/faturacao/pre-visualizacao}: a Fatura-Recibo que o pagamento vai
 *       emitir, calculada no backend e SEM efeitos (POST porque leva um corpo; não escreve
 *       nada).</li>
 *   <li>{@code GET /api/v1/documentos-fiscais}: listagem paginada no servidor, com filtros.</li>
 *   <li>{@code GET /api/v1/documentos-fiscais/{id}}: detalhe só de leitura.</li>
 *   <li>Phase 135 (NCRD-01, NCRD-02): {@code POST /api/v1/documentos-fiscais/{id}/notas-credito/pre-visualizacao}
 *       (a Nota de Crédito que a emissão vai produzir, SEM efeitos) e
 *       {@code POST /api/v1/documentos-fiscais/{id}/notas-credito} (emite a NC sobre a FR
 *       {@code id}: 201 para uma NC nova, 200 para a repetição pela chave de idempotência).</li>
 *   <li>Phase 136 (DFE-05): {@link #reprocessarComunicacao} (POST, debaixo do documento {@code id})
 *       repõe em PENDENTE a comunicação de um documento em ERRO ou REJEITADO (200 com
 *       {@code {estado, tentativas}}; 409 noutro estado; o mesmo 404 do detalhe para um id
 *       inválido ou de outro escritório). Phase 136 (DFE-06): o estado de emissão leva também
 *       {@code modoComunicacao}, o ambiente do gateway em execução (SIMULADO).</li>
 *   <li>Phase 137 (ENTR-04): {@link #reenviarEmail} ({@code POST /api/v1/documentos-fiscais/{id}/email/reenviar})
 *       repõe em PENDENTE a entrega por email de um documento em FALHOU, ENVIADO ou SEM_EMAIL, com
 *       o email atual da ficha do cliente (200 com {@code {estado, tentativas}}; 409/422 com
 *       códigos fixos; o mesmo 404 do detalhe para um id inválido ou de outro escritório).</li>
 *   <li>Phase 137 (ENTR-02): {@link #descarregarPdf} ({@code GET /api/v1/documentos-fiscais/{id}/pdf})
 *       devolve {@code {url, nomeFicheiro, expiresIn}} (URL pré-assinado, gerado a pedido se o PDF
 *       ainda não existir) e {@link #descarregarXml} ({@code GET /api/v1/documentos-fiscais/{id}/xml})
 *       devolve o XML guardado como anexo ({@code application/xml;charset=UTF-8}, nome série/número).
 *       Cada pedido fica na auditoria ({@code documento_fiscal_descarregar}); 503 com código fixo
 *       ({@code FICHEIRO_INDISPONIVEL}, {@code STORAGE_INDISPONIVEL}, {@code FALHA_PDF}) quando o
 *       ficheiro ainda não está disponível ou o armazenamento falhou; o mesmo 404 do detalhe para um
 *       id inválido ou de outro escritório.</li>
 *   <li>Phase 137 (RELF-01): {@link #exportarMes} ({@code GET /api/v1/documentos-fiscais/exportacao-mensal?mes=AAAA-MM})
 *       devolve o CSV do mês para o contabilista como anexo ({@code text/csv;charset=UTF-8}, nome
 *       {@code documentos-fiscais-simulacao-AAAA-MM.csv}); cada exportação fica na auditoria
 *       ({@code documento_fiscal_exportar_mes}). Um mês em falta, malformado ou futuro (no fuso de Cabo
 *       Verde) devolve 422 {@code MES_INVALIDO} sem chamar o serviço.</li>
 * </ul>
 *
 * <p><b>Porque um controlador novo:</b> {@link FaturacaoController} tem um gate de CLASSE
 * {@code financeiro:manage} e os seus testes fixam-no em 7 handlers; estes endpoints servem quem
 * regista pagamentos e consulta documentos, não só quem administra a faturação.
 *
 * <p><b>RBAC nas duas camadas:</b> lista e detalhe exigem {@code financeiro:view}; a
 * pré-visualização exige {@code financeiro:edit}, o mesmo gate de registar um pagamento. O estado
 * de emissão aceita {@code financeiro:view} OU {@code financeiro:edit} (WR-03 da revisão): quem
 * regista pagamentos tem de saber em que modo o formulário está, e o estado (ativa, ambiente, taxa
 * sugerida) não expõe dados de clientes nem documentos. O
 * frontend usa {@code hasScopedPermission(perms, "financeiro", "view"|"edit")}. O backend verifica
 * a autoridade exata (sem cadeia de equivalências), e os papéis semeados detêm sempre
 * {@code view} quando detêm {@code edit}, por isso as duas camadas concordam na prática. As duas
 * rotas da Nota de Crédito exigem a autoridade EXATA {@code financeiro:manage} (CONTEXT "Só
 * financeiro:manage"); o frontend usa {@code hasPermission(perms, "financeiro:manage")}, também
 * sem equivalências, para as duas camadas concordarem. Reprocessar a comunicação exige a
 * autoridade EXATA {@code financeiro:edit}; o frontend usa {@code podeReprocessarComunicacao}, que
 * aplica a mesma regra exata, por isso as duas camadas concordam (regra RBAC do CLAUDE.md).
 * Reenviar o email (Phase 137) exige a mesma autoridade EXATA {@code financeiro:edit}; o frontend
 * usa {@code podeReenviarEmail}, com a mesma regra exata. Descarregar o PDF/XML (Phase 137) exige
 * {@code financeiro:view} exato, o mesmo gate do detalhe. Exportar o CSV mensal (Phase 137) também
 * exige {@code financeiro:view} exato; o frontend usa {@code podeLerDocumentosFiscais}.
 *
 * <p><b>Tenant só do principal autenticado</b>, nunca do caminho, da query ou do corpo
 * ({@link PagamentoRequest} não tem tenant). Os parâmetros da listagem chegam como texto e são
 * analisados aqui: um valor malformado devolve 400 com mensagem fixa (o handler global não mapeia
 * erros de conversão, que cairiam em 500); um id que não é UUID devolve 404, como um documento
 * inexistente ou de outro escritório (sem oráculo).
 *
 * <p><b>Imutabilidade:</b> não há rotas para editar, anular ou apagar um documento fiscal, nem
 * para emitir um documento para um pagamento já registado (EMIS-12). A Nota de Crédito é um
 * documento NOVO que referencia a FR; a FR nunca é alterada. Um id que não é UUID devolve, nas
 * rotas da NC, o mesmo 404 do detalhe. Sem anotação transacional aqui: os serviços são donos
 * das suas transações (leitura, ou a emissão atómica da NC em {@code NotaCreditoService}).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentoFiscalController {

    static final int TAMANHO_POR_OMISSAO = 10;
    static final int TAMANHO_MAXIMO = 100;

    static final String MSG_PAGINACAO = "page deve ser >= 0 e size deve estar entre 1 e 100";
    static final String MSG_CLIENTE = "O cliente indicado não é válido.";
    static final String MSG_DATA = "As datas devem estar no formato AAAA-MM-DD.";
    static final String MSG_PERIODO = "A data final não pode ser anterior à inicial.";
    static final String MSG_TIPO = "O tipo de documento indicado não é válido.";
    static final String MSG_ESTADO = "O estado de comunicação indicado não é válido.";
    static final String MSG_NAO_ENCONTRADO = "Documento fiscal não encontrado.";
    static final String CODIGO_NAO_ENCONTRADO = "DOCUMENTO_FISCAL_NAO_ENCONTRADO";
    static final String MSG_MES_INVALIDO = "O mês escolhido não é válido. Escolha um mês até ao mês atual.";
    static final String CODIGO_MES_INVALIDO = "MES_INVALIDO";
    static final MediaType TEXT_CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private static final ZoneId FUSO_CABO_VERDE = ZoneId.of("Atlantic/Cape_Verde");
    /** Só {@code AAAA-MM}; o {@code YearMonth.parse} valida depois o mês 01..12. */
    private static final Pattern FORMATO_MES = Pattern.compile("\\d{4}-\\d{2}");

    private final PreVisualizacaoFaturaService preVisualizacaoFaturaService;
    private final DocumentoFiscalService documentoFiscalService;
    private final NotaCreditoService notaCreditoService;
    private final ReprocessamentoComunicacaoService reprocessamentoComunicacaoService;
    private final EfaturaGateway efaturaGateway;
    private final ReenvioEmailFiscalService reenvioEmailFiscalService;
    private final DescargaDocumentoFiscalService descargaDocumentoFiscalService;
    private final RelatorioMensalFiscalService relatorioMensalFiscalService;
    private final Clock clock;

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UserPrincipal) auth.getPrincipal();
    }

    private UUID getTenantId() {
        return getPrincipal().getTenantId();
    }

    @PreAuthorize("hasAnyAuthority('financeiro:view', 'financeiro:edit')")
    @GetMapping("/faturacao/estado-emissao")
    public ResponseEntity<?> estadoEmissao() {
        return ResponseEntity.ok(preVisualizacaoFaturaService.estadoEmissao(getTenantId())
                .comModo(efaturaGateway.ambiente().name()));
    }

    @PreAuthorize("hasAuthority('financeiro:edit')")
    @PostMapping("/faturacao/pre-visualizacao")
    public ResponseEntity<?> preVisualizar(@RequestBody PagamentoRequest req) {
        return ResponseEntity.ok(preVisualizacaoFaturaService.preVisualizar(getTenantId(), req));
    }

    @PreAuthorize("hasAuthority('financeiro:view')")
    @GetMapping("/documentos-fiscais")
    public ResponseEntity<?> listar(@RequestParam(required = false) String clienteId,
                                    @RequestParam(required = false) String de,
                                    @RequestParam(required = false) String ate,
                                    @RequestParam(required = false) String tipo,
                                    @RequestParam(required = false) String estado,
                                    @RequestParam(defaultValue = "0") String page,
                                    @RequestParam(defaultValue = "10") String size) {
        int pagina;
        int tamanho;
        try {
            pagina = vazio(page) ? 0 : Integer.parseInt(page.trim());
            tamanho = vazio(size) ? TAMANHO_POR_OMISSAO : Integer.parseInt(size.trim());
        } catch (NumberFormatException e) {
            return pedidoInvalido(MSG_PAGINACAO);
        }
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            return pedidoInvalido(MSG_PAGINACAO);
        }

        UUID cliente;
        try {
            cliente = vazio(clienteId) ? null : UUID.fromString(clienteId.trim());
        } catch (IllegalArgumentException e) {
            return pedidoInvalido(MSG_CLIENTE);
        }

        LocalDate desde;
        LocalDate atePeriodo;
        try {
            desde = vazio(de) ? null : LocalDate.parse(de.trim());
            atePeriodo = vazio(ate) ? null : LocalDate.parse(ate.trim());
        } catch (DateTimeParseException e) {
            return pedidoInvalido(MSG_DATA);
        }
        if (desde != null && atePeriodo != null && atePeriodo.isBefore(desde)) {
            return pedidoInvalido(MSG_PERIODO);
        }

        TipoDocumentoFiscal tipoDocumento;
        try {
            tipoDocumento = vazio(tipo) ? null : TipoDocumentoFiscal.valueOf(tipo.trim());
        } catch (IllegalArgumentException e) {
            return pedidoInvalido(MSG_TIPO);
        }

        EstadoComunicacaoFiscal estadoComunicacao;
        try {
            estadoComunicacao = vazio(estado) ? null : EstadoComunicacaoFiscal.valueOf(estado.trim());
        } catch (IllegalArgumentException e) {
            return pedidoInvalido(MSG_ESTADO);
        }

        Page<DocumentoFiscalResumoResponse> resultado = documentoFiscalService.listar(getTenantId(), cliente,
                tipoDocumento, estadoComunicacao, desde, atePeriodo, pagina, tamanho);
        return ResponseEntity.ok(Map.of(
                "content", resultado.getContent(),
                "totalElements", resultado.getTotalElements(),
                "totalPages", resultado.getTotalPages(),
                "page", pagina,
                "size", tamanho));
    }

    @PreAuthorize("hasAuthority('financeiro:view')")
    @GetMapping("/documentos-fiscais/{id}")
    public ResponseEntity<?> detalhe(@PathVariable String id) {
        Optional<UUID> documentoId = idDocumento(id);
        if (documentoId.isEmpty()) {
            return naoEncontrado();
        }
        return ResponseEntity.ok(documentoFiscalService.detalhe(getTenantId(), documentoId.get()));
    }

    @PreAuthorize("hasAuthority('financeiro:manage')")
    @PostMapping("/documentos-fiscais/{id}/notas-credito/pre-visualizacao")
    public ResponseEntity<?> preVisualizarNotaCredito(@PathVariable String id, @RequestBody NotaCreditoRequest req) {
        Optional<UUID> origemId = idDocumento(id);
        if (origemId.isEmpty()) {
            return naoEncontrado();
        }
        return ResponseEntity.ok(notaCreditoService.preVisualizar(getTenantId(), origemId.get(), req));
    }

    @PreAuthorize("hasAuthority('financeiro:manage')")
    @PostMapping("/documentos-fiscais/{id}/notas-credito")
    public ResponseEntity<?> emitirNotaCredito(@PathVariable String id, @RequestBody NotaCreditoRequest req) {
        Optional<UUID> origemId = idDocumento(id);
        if (origemId.isEmpty()) {
            return naoEncontrado();
        }
        ResultadoNotaCredito r = notaCreditoService.emitir(getTenantId(), getPrincipal(), origemId.get(), req);
        return ResponseEntity.status(r.novo() ? HttpStatus.CREATED : HttpStatus.OK).body(r.resposta());
    }

    @PreAuthorize("hasAuthority('financeiro:edit')")
    @PostMapping("/documentos-fiscais/{id}/comunicacao/reprocessar")
    public ResponseEntity<?> reprocessarComunicacao(@PathVariable String id) {
        Optional<UUID> documentoId = idDocumento(id);
        if (documentoId.isEmpty()) {
            return naoEncontrado();
        }
        return ResponseEntity.ok(reprocessamentoComunicacaoService.reprocessar(getTenantId(), getPrincipal(),
                documentoId.get()));
    }

    @PreAuthorize("hasAuthority('financeiro:edit')")
    @PostMapping("/documentos-fiscais/{id}/email/reenviar")
    public ResponseEntity<?> reenviarEmail(@PathVariable String id) {
        Optional<UUID> documentoId = idDocumento(id);
        if (documentoId.isEmpty()) {
            return naoEncontrado();
        }
        return ResponseEntity.ok(reenvioEmailFiscalService.reenviar(getTenantId(), getPrincipal(),
                documentoId.get()));
    }

    @PreAuthorize("hasAuthority('financeiro:view')")
    @GetMapping("/documentos-fiscais/{id}/pdf")
    public ResponseEntity<?> descarregarPdf(@PathVariable String id) {
        Optional<UUID> documentoId = idDocumento(id);
        if (documentoId.isEmpty()) {
            return naoEncontrado();
        }
        DescargaDocumentoFiscalService.DescargaPdf pdf = descargaDocumentoFiscalService.descarregarPdf(getTenantId(),
                getPrincipal(), documentoId.get());
        return ResponseEntity.ok(Map.of(
                "url", pdf.url(),
                "nomeFicheiro", pdf.nomeFicheiro(),
                "expiresIn", pdf.expiresIn()));
    }

    @PreAuthorize("hasAuthority('financeiro:view')")
    @GetMapping("/documentos-fiscais/{id}/xml")
    public ResponseEntity<?> descarregarXml(@PathVariable String id) {
        Optional<UUID> documentoId = idDocumento(id);
        if (documentoId.isEmpty()) {
            return naoEncontrado();
        }
        DescargaDocumentoFiscalTransacoes.XmlDescarregavel xml = descargaDocumentoFiscalService.descarregarXml(
                getTenantId(), getPrincipal(), documentoId.get());
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(xml.nomeFicheiro()).build().toString())
                .body(xml.conteudo());
    }

    @PreAuthorize("hasAuthority('financeiro:view')")
    @GetMapping("/documentos-fiscais/exportacao-mensal")
    public ResponseEntity<?> exportarMes(@RequestParam(required = false) String mes) {
        Optional<YearMonth> mesPedido = mesValido(mes);
        if (mesPedido.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", MSG_MES_INVALIDO, "code", CODIGO_MES_INVALIDO));
        }
        RelatorioMensalFiscalService.CsvMensal csv = relatorioMensalFiscalService.exportar(getTenantId(),
                getPrincipal(), mesPedido.get());
        return ResponseEntity.ok()
                .contentType(TEXT_CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(csv.nomeFicheiro()).build().toString())
                .body(csv.conteudo());
    }

    /**
     * {@code AAAA-MM} até ao mês atual no fuso de Cabo Verde (T-137-80), ou vazio quando em falta,
     * malformado ou futuro. Nada deste texto chega ao SQL: o serviço recebe um {@link YearMonth}.
     */
    private Optional<YearMonth> mesValido(String mes) {
        if (vazio(mes) || !FORMATO_MES.matcher(mes.trim()).matches()) {
            return Optional.empty();
        }
        YearMonth pedido;
        try {
            pedido = YearMonth.parse(mes.trim());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
        YearMonth atual = YearMonth.now(clock.withZone(FUSO_CABO_VERDE));
        return pedido.isAfter(atual) ? Optional.empty() : Optional.of(pedido);
    }

    /** Id do caminho como UUID, ou vazio quando não é um UUID (o chamador devolve 404 sem oráculo). */
    private static Optional<UUID> idDocumento(String id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(id.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static ResponseEntity<Map<String, String>> naoEncontrado() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", MSG_NAO_ENCONTRADO, "code", CODIGO_NAO_ENCONTRADO));
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static ResponseEntity<Map<String, String>> pedidoInvalido(String mensagem) {
        return ResponseEntity.badRequest().body(Map.of("message", mensagem));
    }
}
