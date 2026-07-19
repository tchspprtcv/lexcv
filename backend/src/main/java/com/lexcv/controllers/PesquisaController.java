package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ResultadoPesquisaDto;
import com.lexcv.models.Cliente;
import com.lexcv.models.Documento;
import com.lexcv.models.ParecerSolicitacao;
import com.lexcv.models.Processo;
import com.lexcv.repositories.ClienteRepository;
import com.lexcv.repositories.DocumentoRepository;
import com.lexcv.repositories.ParecerSolicitacaoRepository;
import com.lexcv.repositories.ProcessoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Dedicated controller for the top-level GET /api/v1/pesquisa cross-entity global search
 * endpoint. Bare class-level @RequestMapping plus a single argument-less @GetMapping,
 * mirroring ParecerPesquisaController — Spring concatenates class-level and method-level
 * mappings regardless of a leading "/", so this shape (no sub-path anywhere on either
 * annotation) sidesteps that bug class by construction (see ParecerPesquisaController's
 * own header comment for the historical routing incident this avoids).
 *
 * RBAC here is intentionally NOT a single scope-requiring @PreAuthorize: each of the 4
 * entity branches below is gated independently, on its own "&lt;scope&gt;:view" authority,
 * inside the method body (see hasAuthority). A caller who is authenticated but holds none
 * of the 4 scopes still gets 200 OK plus an empty list, never 403 — SecurityConfig's
 * .anyRequest().authenticated() already forces authentication on this path at the filter
 * chain, so @PreAuthorize("isAuthenticated()") here is a coarse, non-discriminating gate,
 * not the source of authorization. There is deliberately no fifth query branch beyond
 * cliente/processo/documento/parecer — this endpoint never touches billing/fee records,
 * by construction (SRCH-06).
 */
@RestController
@RequestMapping("/api/v1/pesquisa")
@RequiredArgsConstructor
public class PesquisaController {

    private static final Logger logger = LoggerFactory.getLogger(PesquisaController.class);

    private static final int LIMITE_POR_TIPO = 5;
    private static final int TERMO_MAX_LENGTH = 200;
    private static final int TERMO_MIN_LENGTH = 2;
    private static final int DESCRICAO_PREVIEW_LENGTH = 80;

    private final ClienteRepository clienteRepository;
    private final ProcessoRepository processoRepository;
    private final DocumentoRepository documentoRepository;
    private final ParecerSolicitacaoRepository parecerSolicitacaoRepository;

    private UUID getTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return principal.getTenantId();
    }

    private boolean hasAuthority(Authentication auth, String authority) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    /**
     * WR-01: codepoint-safe truncation. Plain {@code String.substring(0, maxLength)} cuts at a
     * UTF-16 code-unit boundary, which can split a supplementary-plane character's surrogate
     * pair in half and leave an unpaired surrogate in the result — silently corrupting the JDBC
     * bind parameter on the input side (line ~76), and causing Jackson to reject the JSON output
     * on the {@code truncarDescricao()} side (line ~180). {@code offsetByCodePoints} walks whole
     * codepoints, so the returned index can never land inside a surrogate pair.
     */
    private static String truncateSafely(String s, int maxLength) {
        if (s.codePointCount(0, s.length()) <= maxLength) {
            return s;
        }
        int endIndex = s.offsetByCodePoints(0, maxLength);
        return s.substring(0, endIndex);
    }

    /**
     * WR-02: escapes the two ILIKE wildcard metacharacters ({@code %}, {@code _}) plus the
     * escape character itself ({@code \}) in a user-supplied search term, so a literal
     * {@code %}/{@code _} typed by the user is matched literally instead of being interpreted
     * as a wildcard (e.g. a termo of exactly {@code %%} would otherwise match every row). Must
     * be paired with an explicit {@code ESCAPE '\'} clause on every ILIKE comparison that wraps
     * the escaped value in wildcards. Only applies to the wildcard-wrapped ILIKE comparisons —
     * the raw, unescaped termo is still used for the exact-match ({@code =}) ranking tiers in
     * ClienteRepository/ProcessoRepository, since escaping would corrupt a literal equality
     * comparison.
     */
    private static String escapeLike(String termo) {
        return termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<?> pesquisar(@RequestParam(required = false) String q) {
        String termo = q == null ? "" : q.trim();
        termo = truncateSafely(termo, TERMO_MAX_LENGTH);
        if (termo.length() < TERMO_MIN_LENGTH) {
            return ResponseEntity.ok(List.of());
        }
        // termo is reassigned above (truncation), so it is not effectively final and cannot be
        // captured by the lambdas below. termoFinal is assigned exactly once and stands in for
        // termo from this point on.
        final String termoFinal = termo;

        UUID tenantId = getTenantId();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String termoEscapado = escapeLike(termoFinal);

        List<ResultadoPesquisaDto> resultados = new ArrayList<>();
        if (hasAuthority(auth, "clientes:view")) {
            resultados.addAll(mapearClientes(pesquisarComIsolamentoDeFalhas("cliente",
                    () -> clienteRepository.pesquisarGlobal(tenantId, termoFinal, termoEscapado, LIMITE_POR_TIPO))));
        }
        if (hasAuthority(auth, "processos:view")) {
            resultados.addAll(mapearProcessos(pesquisarComIsolamentoDeFalhas("processo",
                    () -> processoRepository.pesquisarGlobal(tenantId, termoFinal, termoEscapado, LIMITE_POR_TIPO))));
        }
        if (hasAuthority(auth, "documentos:view")) {
            resultados.addAll(mapearDocumentos(pesquisarComIsolamentoDeFalhas("documento",
                    () -> documentoRepository.pesquisarGlobal(tenantId, termoEscapado, LIMITE_POR_TIPO))));
        }
        if (hasAuthority(auth, "pareceres:view")) {
            resultados.addAll(mapearPareceres(pesquisarComIsolamentoDeFalhas("parecer",
                    () -> parecerSolicitacaoRepository.pesquisarGlobal(tenantId, termoEscapado, LIMITE_POR_TIPO))));
        }

        return ResponseEntity.ok(resultados);
    }

    /**
     * WR-03: isolates each of the 4 entity branches above so a single failing query — most
     * plausibly the required-but-manual {@code unaccent} PostgreSQL extension
     * (backend/migrations/111-enable-search-extensions.sql) not having been applied to this
     * environment — does not take down the other three branches. Logs a specific, actionable
     * diagnostic (naming the likely cause and the migration file) instead of letting only the
     * generic 500 from {@code GlobalExceptionHandler}'s catch-all reach the caller. A failing
     * branch degrades to an empty result for that entity type rather than failing the whole
     * request — consistent with how a caller lacking the branch's RBAC scope already gets an
     * empty result for that branch rather than an error.
     */
    private <T> List<T> pesquisarComIsolamentoDeFalhas(String tipo, Supplier<List<T>> consulta) {
        try {
            return consulta.get();
        } catch (DataAccessException ex) {
            logger.error("Search failed for entity type '{}' in GET /api/v1/pesquisa (branch isolated " +
                    "from the other three). Most likely cause: the PostgreSQL 'unaccent' extension is not " +
                    "installed (see backend/migrations/111-enable-search-extensions.sql). Original exception: {}",
                    tipo, ex.toString(), ex);
            return List.of();
        }
    }

    private List<ResultadoPesquisaDto> mapearClientes(List<Cliente> clientes) {
        List<ResultadoPesquisaDto> resultados = new ArrayList<>();
        for (Cliente cliente : clientes) {
            resultados.add(new ResultadoPesquisaDto(
                    "cliente",
                    cliente.getId().toString(),
                    cliente.getNome(),
                    montarSubtituloCliente(cliente),
                    "/clientes/" + cliente.getId()));
        }
        return resultados;
    }

    private String montarSubtituloCliente(Cliente cliente) {
        String numeroCliente = cliente.getNumeroCliente();
        String nif = cliente.getNif();
        boolean temNif = nif != null && !nif.isBlank();
        boolean temNumero = numeroCliente != null && !numeroCliente.isBlank();
        if (temNif && temNumero) {
            return numeroCliente + " · NIF " + nif;
        }
        if (temNif) {
            return "NIF " + nif;
        }
        return numeroCliente;
    }

    private List<ResultadoPesquisaDto> mapearProcessos(List<Processo> processos) {
        List<ResultadoPesquisaDto> resultados = new ArrayList<>();
        for (Processo processo : processos) {
            String titulo = (processo.getNumeroProcesso() != null && !processo.getNumeroProcesso().isBlank())
                    ? processo.getNumeroProcesso()
                    : "Processo";
            String subtitulo = (processo.getEstado() != null && !processo.getEstado().isBlank())
                    ? processo.getEstado()
                    : processo.getTipoProcesso();
            resultados.add(new ResultadoPesquisaDto(
                    "processo",
                    processo.getId().toString(),
                    titulo,
                    subtitulo,
                    "/processos/" + processo.getId()));
        }
        return resultados;
    }

    private List<ResultadoPesquisaDto> mapearDocumentos(List<Documento> documentos) {
        List<ResultadoPesquisaDto> resultados = new ArrayList<>();
        for (Documento documento : documentos) {
            resultados.add(new ResultadoPesquisaDto(
                    "documento",
                    documento.getId().toString(),
                    documento.getNome(),
                    documento.getTipo(),
                    "/documentos/" + documento.getId()));
        }
        return resultados;
    }

    private List<ResultadoPesquisaDto> mapearPareceres(List<ParecerSolicitacao> pareceres) {
        List<ResultadoPesquisaDto> resultados = new ArrayList<>();
        for (ParecerSolicitacao parecer : pareceres) {
            resultados.add(new ResultadoPesquisaDto(
                    "parecer",
                    parecer.getId().toString(),
                    truncarDescricao(parecer.getDescricao()),
                    parecer.getStatus(),
                    "/pareceres/" + parecer.getId()));
        }
        return resultados;
    }

    /**
     * WR-01 (re-review): the truncation decision must be derived from the same
     * codepoint-aware call that performs the truncation, not from a separately-computed
     * {@code String.length()} (UTF-16 code units) pre-check. The two units disagree whenever
     * the text contains a supplementary-plane character (e.g. an emoji) among the first
     * {@code DESCRICAO_PREVIEW_LENGTH} codepoints — a UTF-16-length guard can fire (text
     * "looks" too long) even though {@code truncateSafely} legitimately decides no codepoints
     * need removing, which used to append a spurious "..." to unmodified text. Comparing
     * {@code truncado} against {@code trimmed} directly is unit-consistent by construction.
     */
    private String truncarDescricao(String descricao) {
        if (descricao == null) {
            return "";
        }
        String trimmed = descricao.trim();
        String truncado = truncateSafely(trimmed, DESCRICAO_PREVIEW_LENGTH);
        return truncado.length() < trimmed.length() ? truncado + "..." : truncado;
    }
}
