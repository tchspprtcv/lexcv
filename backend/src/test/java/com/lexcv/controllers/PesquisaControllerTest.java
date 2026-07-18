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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit test for {@link PesquisaController} — no MockMvc/{@code @SpringBootTest}
 * harness exists anywhere in this codebase (see {@code ResourceControllerUploadDocumentoTest}),
 * so the class's {@code @PreAuthorize("isAuthenticated()")} annotation is inert here, same as
 * every other {@code @PreAuthorize}-guarded method tested in this codebase. RBAC is instead
 * proven by exercising the controller's own programmatic {@code hasAuthority(auth,
 * "<scope>:view")} branch logic directly, driven by the authorities attached to a mocked
 * {@link UsernamePasswordAuthenticationToken} placed on the {@link SecurityContextHolder}
 * before each call.
 */
@ExtendWith(MockitoExtension.class)
class PesquisaControllerTest {

    @Mock private ClienteRepository clienteRepository;
    @Mock private ProcessoRepository processoRepository;
    @Mock private DocumentoRepository documentoRepository;
    @Mock private ParecerSolicitacaoRepository parecerSolicitacaoRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Set<String> TIPOS_VALIDOS = Set.of("cliente", "processo", "documento", "parecer");

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private PesquisaController novoController() {
        return new PesquisaController(clienteRepository, processoRepository, documentoRepository, parecerSolicitacaoRepository);
    }

    private void autenticarComo(List<String> scopes) {
        UserPrincipal principal = UserPrincipal.builder().userId(USER_ID).tenantId(TENANT_ID).build();
        List<SimpleGrantedAuthority> authorities = scopes.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    @SuppressWarnings("unchecked")
    private List<ResultadoPesquisaDto> corpo(ResponseEntity<?> response) {
        return (List<ResultadoPesquisaDto>) response.getBody();
    }

    @Test
    void pesquisar_comTodosOsQuatroScopes_consultaTodosOsQuatroRamos() {
        autenticarComo(List.of("clientes:view", "processos:view", "documentos:view", "pareceres:view"));

        when(clienteRepository.pesquisarGlobal(any(), anyString(), anyInt()))
                .thenReturn(List.of(Cliente.builder().id(UUID.randomUUID()).nome("Cliente Teste").build()));
        when(processoRepository.pesquisarGlobal(any(), anyString(), anyInt()))
                .thenReturn(List.of(Processo.builder().id(UUID.randomUUID()).numeroProcesso("PROC-001").build()));
        when(documentoRepository.pesquisarGlobal(any(), anyString(), anyInt()))
                .thenReturn(List.of(Documento.builder().id(UUID.randomUUID()).nome("Doc Teste").build()));
        when(parecerSolicitacaoRepository.pesquisarGlobal(any(), anyString(), anyInt()))
                .thenReturn(List.of(ParecerSolicitacao.builder().id(UUID.randomUUID()).descricao("Parecer Teste").build()));

        ResponseEntity<?> response = novoController().pesquisar("termo");
        List<ResultadoPesquisaDto> body = corpo(response);

        assertEquals(4, body.size());
        Set<String> tipos = body.stream().map(ResultadoPesquisaDto::tipo).collect(Collectors.toSet());
        assertEquals(TIPOS_VALIDOS, tipos);

        verify(clienteRepository, times(1)).pesquisarGlobal(any(), anyString(), anyInt());
        verify(processoRepository, times(1)).pesquisarGlobal(any(), anyString(), anyInt());
        verify(documentoRepository, times(1)).pesquisarGlobal(any(), anyString(), anyInt());
        verify(parecerSolicitacaoRepository, times(1)).pesquisarGlobal(any(), anyString(), anyInt());
    }

    @Test
    void pesquisar_comScopeParcial_omiteRamosSemScopeEmVezDeEsconderResultados() {
        autenticarComo(List.of("clientes:view", "processos:view"));

        when(clienteRepository.pesquisarGlobal(any(), anyString(), anyInt()))
                .thenReturn(List.of(Cliente.builder().id(UUID.randomUUID()).nome("Cliente A").build()));
        when(processoRepository.pesquisarGlobal(any(), anyString(), anyInt()))
                .thenReturn(List.of(Processo.builder().id(UUID.randomUUID()).numeroProcesso("PROC-002").build()));

        ResponseEntity<?> response = novoController().pesquisar("termo");
        List<ResultadoPesquisaDto> body = corpo(response);

        assertTrue(body.stream().map(ResultadoPesquisaDto::tipo)
                .allMatch(tipo -> tipo.equals("cliente") || tipo.equals("processo")));

        verify(clienteRepository, times(1)).pesquisarGlobal(any(), anyString(), anyInt());
        verify(processoRepository, times(1)).pesquisarGlobal(any(), anyString(), anyInt());
        verify(documentoRepository, never()).pesquisarGlobal(any(), any(), anyInt());
        verify(parecerSolicitacaoRepository, never()).pesquisarGlobal(any(), any(), anyInt());
    }

    @Test
    void pesquisar_semNenhumScope_devolveListaVaziaSemExcecaoENuncaConsultaRepositorios() {
        autenticarComo(List.of());

        ResponseEntity<?> response = novoController().pesquisar("termo");
        List<ResultadoPesquisaDto> body = corpo(response);

        assertTrue(body.isEmpty());

        verify(clienteRepository, never()).pesquisarGlobal(any(), any(), anyInt());
        verify(processoRepository, never()).pesquisarGlobal(any(), any(), anyInt());
        verify(documentoRepository, never()).pesquisarGlobal(any(), any(), anyInt());
        verify(parecerSolicitacaoRepository, never()).pesquisarGlobal(any(), any(), anyInt());
    }

    /**
     * SRCH-06 role matrix, scope sets copied verbatim from {@code DatabaseSeeder.seedRbac()}
     * (lines 293-353): all 4 seeded roles hold all 4 {@code :view} scopes this endpoint gates
     * on; only {@code financeiro:view} differs (ASSISTENTE lacks it, the other 3 hold it) —
     * and this endpoint has no branch that ever reads that scope, so toggling it must have
     * zero observable effect on the response (proves the structural, not just RBAC, exclusion
     * of any billing/fee-sourced data).
     */
    @Test
    void pesquisar_matrizDePerfis_nuncaExpoeTipoForaDosQuatroPermitidos() {
        Map<String, List<String>> scopesPorPerfil = Map.of(
                "ADMIN", List.of("clientes:view", "processos:view", "documentos:view", "pareceres:view", "financeiro:view"),
                "ADVOGADO", List.of("clientes:view", "processos:view", "documentos:view", "pareceres:view", "financeiro:view"),
                "TECNICO", List.of("clientes:view", "processos:view", "documentos:view", "pareceres:view", "financeiro:view"),
                "ASSISTENTE", List.of("clientes:view", "processos:view", "documentos:view", "pareceres:view")
        );

        for (Map.Entry<String, List<String>> perfil : scopesPorPerfil.entrySet()) {
            autenticarComo(perfil.getValue());

            when(clienteRepository.pesquisarGlobal(any(), anyString(), anyInt()))
                    .thenReturn(List.of(Cliente.builder().id(UUID.randomUUID()).nome("Cliente " + perfil.getKey()).build()));
            when(processoRepository.pesquisarGlobal(any(), anyString(), anyInt()))
                    .thenReturn(List.of(Processo.builder().id(UUID.randomUUID()).numeroProcesso("PROC-" + perfil.getKey()).build()));
            when(documentoRepository.pesquisarGlobal(any(), anyString(), anyInt()))
                    .thenReturn(List.of(Documento.builder().id(UUID.randomUUID()).nome("Doc " + perfil.getKey()).build()));
            when(parecerSolicitacaoRepository.pesquisarGlobal(any(), anyString(), anyInt()))
                    .thenReturn(List.of(ParecerSolicitacao.builder().id(UUID.randomUUID()).descricao("Parecer " + perfil.getKey()).build()));

            ResponseEntity<?> response = novoController().pesquisar("termo");
            List<ResultadoPesquisaDto> body = corpo(response);

            assertTrue(body.stream().map(ResultadoPesquisaDto::tipo).allMatch(TIPOS_VALIDOS::contains),
                    "Perfil " + perfil.getKey() + " expos tipo fora do conjunto permitido");
            assertEquals(4, body.size(),
                    "Perfil " + perfil.getKey() + " detem os 4 scopes :view — financeiro:view nao deveria alterar a contagem");
        }
    }

    @Test
    void pesquisar_comQNulo_devolveListaVaziaSemConsultarRepositorios() {
        autenticarComo(List.of("clientes:view", "processos:view", "documentos:view", "pareceres:view"));

        ResponseEntity<?> response = novoController().pesquisar(null);
        assertTrue(corpo(response).isEmpty());

        verify(clienteRepository, never()).pesquisarGlobal(any(), any(), anyInt());
        verify(processoRepository, never()).pesquisarGlobal(any(), any(), anyInt());
        verify(documentoRepository, never()).pesquisarGlobal(any(), any(), anyInt());
        verify(parecerSolicitacaoRepository, never()).pesquisarGlobal(any(), any(), anyInt());
    }

    @Test
    void pesquisar_comQDeUmCaractere_devolveListaVaziaSemConsultarRepositorios() {
        autenticarComo(List.of("clientes:view", "processos:view", "documentos:view", "pareceres:view"));

        ResponseEntity<?> response = novoController().pesquisar("a");
        assertTrue(corpo(response).isEmpty());

        verify(clienteRepository, never()).pesquisarGlobal(any(), any(), anyInt());
        verify(processoRepository, never()).pesquisarGlobal(any(), any(), anyInt());
        verify(documentoRepository, never()).pesquisarGlobal(any(), any(), anyInt());
        verify(parecerSolicitacaoRepository, never()).pesquisarGlobal(any(), any(), anyInt());
    }

    @Test
    void pesquisar_comQAcimaDoLimite_truncaTermoA200CaracteresAntesDeConsultar() {
        autenticarComo(List.of("clientes:view"));

        when(clienteRepository.pesquisarGlobal(any(), anyString(), anyInt())).thenReturn(List.of());

        String termoLongo = "a".repeat(201);
        novoController().pesquisar(termoLongo);

        ArgumentCaptor<String> termoCaptor = ArgumentCaptor.forClass(String.class);
        verify(clienteRepository).pesquisarGlobal(any(), termoCaptor.capture(), anyInt());
        assertEquals(200, termoCaptor.getValue().length());
    }
}
