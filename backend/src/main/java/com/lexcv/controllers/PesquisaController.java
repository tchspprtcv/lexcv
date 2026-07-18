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

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<?> pesquisar(@RequestParam(required = false) String q) {
        String termo = q == null ? "" : q.trim();
        if (termo.length() > TERMO_MAX_LENGTH) {
            termo = termo.substring(0, TERMO_MAX_LENGTH);
        }
        if (termo.length() < TERMO_MIN_LENGTH) {
            return ResponseEntity.ok(List.of());
        }

        UUID tenantId = getTenantId();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        List<ResultadoPesquisaDto> resultados = new ArrayList<>();
        if (hasAuthority(auth, "clientes:view")) {
            resultados.addAll(mapearClientes(clienteRepository.pesquisarGlobal(tenantId, termo, LIMITE_POR_TIPO)));
        }
        if (hasAuthority(auth, "processos:view")) {
            resultados.addAll(mapearProcessos(processoRepository.pesquisarGlobal(tenantId, termo, LIMITE_POR_TIPO)));
        }
        if (hasAuthority(auth, "documentos:view")) {
            resultados.addAll(mapearDocumentos(documentoRepository.pesquisarGlobal(tenantId, termo, LIMITE_POR_TIPO)));
        }
        if (hasAuthority(auth, "pareceres:view")) {
            resultados.addAll(mapearPareceres(parecerSolicitacaoRepository.pesquisarGlobal(tenantId, termo, LIMITE_POR_TIPO)));
        }

        return ResponseEntity.ok(resultados);
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

    private String truncarDescricao(String descricao) {
        if (descricao == null) {
            return "";
        }
        String trimmed = descricao.trim();
        return trimmed.length() > DESCRICAO_PREVIEW_LENGTH
                ? trimmed.substring(0, DESCRICAO_PREVIEW_LENGTH) + "..."
                : trimmed;
    }
}
