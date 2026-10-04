package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.DocumentoFiscalResumoResponse;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.services.fiscal.DocumentoFiscalService;
import com.lexcv.services.fiscal.PreVisualizacaoFaturaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

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
 * </ul>
 *
 * <p><b>Porque um controlador novo:</b> {@link FaturacaoController} tem um gate de CLASSE
 * {@code financeiro:manage} e os seus testes fixam-no em 7 handlers; estes endpoints servem quem
 * regista pagamentos e consulta documentos, não só quem administra a faturação.
 *
 * <p><b>RBAC nas duas camadas:</b> estado, lista e detalhe exigem {@code financeiro:view}; a
 * pré-visualização exige {@code financeiro:edit}, o mesmo gate de registar um pagamento. O
 * frontend usa {@code hasScopedPermission(perms, "financeiro", "view"|"edit")}. O backend verifica
 * a autoridade exata (sem cadeia de equivalências), e os papéis semeados detêm sempre
 * {@code view} quando detêm {@code edit}, por isso as duas camadas concordam na prática.
 *
 * <p><b>Tenant só do principal autenticado</b>, nunca do caminho, da query ou do corpo
 * ({@link PagamentoRequest} não tem tenant). Os parâmetros da listagem chegam como texto e são
 * analisados aqui: um valor malformado devolve 400 com mensagem fixa (o handler global não mapeia
 * erros de conversão, que cairiam em 500); um id que não é UUID devolve 404, como um documento
 * inexistente ou de outro escritório (sem oráculo).
 *
 * <p><b>Imutabilidade:</b> não há rotas para editar, anular ou apagar um documento fiscal, nem
 * para emitir um documento para um pagamento já registado (EMIS-12). Sem {@code @Transactional}
 * aqui: os serviços são donos das suas transações (só de leitura).
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

    private final PreVisualizacaoFaturaService preVisualizacaoFaturaService;
    private final DocumentoFiscalService documentoFiscalService;

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UserPrincipal) auth.getPrincipal();
    }

    private UUID getTenantId() {
        return getPrincipal().getTenantId();
    }

    @PreAuthorize("hasAuthority('financeiro:view')")
    @GetMapping("/faturacao/estado-emissao")
    public ResponseEntity<?> estadoEmissao() {
        return ResponseEntity.ok(preVisualizacaoFaturaService.estadoEmissao(getTenantId()));
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
        UUID documentoId;
        try {
            documentoId = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", MSG_NAO_ENCONTRADO, "code", CODIGO_NAO_ENCONTRADO));
        }
        return ResponseEntity.ok(documentoFiscalService.detalhe(getTenantId(), documentoId));
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static ResponseEntity<Map<String, String>> pedidoInvalido(String mensagem) {
        return ResponseEntity.badRequest().body(Map.of("message", mensagem));
    }
}
