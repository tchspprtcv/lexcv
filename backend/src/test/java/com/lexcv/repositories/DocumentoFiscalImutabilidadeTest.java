package com.lexcv.repositories;

import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 134 (EMIS-08, D-07, D-15): gate estrutural, por reflexão e leitura das fontes, de que um
 * documento fiscal só pode ser inserido e lido por tenant. Adaptado de
 * {@link AuditLogImutabilidadeTest}.
 *
 * <p>Quando falhar porque um método de leitura legítimo foi acrescentado a um repositório, a
 * correção é acrescentar esse nome ao conjunto esperado, deliberadamente -- nunca relaxar a
 * verificação.
 */
class DocumentoFiscalImutabilidadeTest {

    private static final List<Class<?>> REPOSITORIOS_FISCAIS = List.of(
            DocumentoFiscalRepository.class,
            DocumentoFiscalLinhaRepository.class,
            DocumentoFiscalLigacaoClienteRepository.class,
            ComunicacaoFiscalRepository.class);

    private static final String SQL_REPONTAR =
            "UPDATE t_documento_fiscal SET cliente_id = :novo WHERE tenant_id = :tenantId AND cliente_id = :antigo";

    private static Set<String> nomes(Class<?> tipo) {
        return Arrays.stream(tipo.getMethods()).map(Method::getName).collect(Collectors.toSet());
    }

    // ---- Teste 1: só o marcador Repository, nunca as bases mais permissivas ----
    @Test
    void repositoriosFiscaisSaoEstreitos() {
        for (Class<?> repo : REPOSITORIOS_FISCAIS) {
            String n = repo.getSimpleName();
            assertTrue(Repository.class.isAssignableFrom(repo), n + " deve ser um Repository");
            assertFalse(CrudRepository.class.isAssignableFrom(repo), n + " NAO pode ser CrudRepository");
            assertFalse(ListCrudRepository.class.isAssignableFrom(repo), n + " NAO pode ser ListCrudRepository");
            assertFalse(PagingAndSortingRepository.class.isAssignableFrom(repo),
                    n + " NAO pode ser PagingAndSortingRepository");
            assertFalse(JpaRepository.class.isAssignableFrom(repo), n + " NAO pode ser JpaRepository");
            assertFalse(JpaSpecificationExecutor.class.isAssignableFrom(repo),
                    n + " NAO pode ser JpaSpecificationExecutor (declara delete(Specification))");
        }
    }

    // ---- Teste 2: conjunto de métodos de DocumentoFiscalRepository fixado, sem @Modifying ----
    @Test
    void documentoFiscalRepositoryTemExatamenteOsMetodosFixados() {
        assertEquals(Set.of("save", "findByIdAndTenantId", "findByTenantIdAndChaveIdempotencia",
                        "findByTenantIdAndPagamentoIdIn", "existsByTenantIdAndPagamentoId",
                        "existsByTenantIdAndClienteId", "existsByTenantIdAndProcessoId",
                        "existsByTenantIdAndHonorarioId", "buscar",
                        // Phase 135 (NCRD-01..03): finders das Notas de Crédito -- atualização deliberada.
                        "findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc",
                        "findByTenantIdAndIdIn", "existsByTenantIdAndPagamentoIdAndTipo"),
                nomes(DocumentoFiscalRepository.class));
        for (Method m : DocumentoFiscalRepository.class.getMethods()) {
            assertFalse(m.isAnnotationPresent(Modifying.class), "@Modifying proibido: " + m.getName());
        }
    }

    // ---- Teste 3: linhas -- save + um finder, sem @Modifying ----
    @Test
    void documentoFiscalLinhaRepositoryTemExatamenteOsMetodosFixados() {
        assertEquals(Set.of("save", "findByTenantIdAndDocumentoFiscalIdOrderByNumeroLinhaAsc"),
                nomes(DocumentoFiscalLinhaRepository.class));
        for (Method m : DocumentoFiscalLinhaRepository.class.getMethods()) {
            assertFalse(m.isAnnotationPresent(Modifying.class), "@Modifying proibido: " + m.getName());
        }
    }

    // ---- Teste 4: ligação ao cliente -- um único método com a SQL fixada ----
    @Test
    void ligacaoClienteTemSoORepontarComASqlFixada() throws NoSuchMethodException {
        assertEquals(Set.of("repontarCliente"), nomes(DocumentoFiscalLigacaoClienteRepository.class));
        Method m = DocumentoFiscalLigacaoClienteRepository.class
                .getMethod("repontarCliente", UUID.class, UUID.class, UUID.class);
        Query q = m.getAnnotation(Query.class);
        assertNotNull(q, "repontarCliente deve ter @Query");
        assertTrue(q.nativeQuery(), "repontarCliente deve ser nativo");
        assertEquals(SQL_REPONTAR, q.value());
        assertTrue(m.isAnnotationPresent(Modifying.class));
    }

    // ---- Teste 5: comunicação -- conjunto fixado ----
    @Test
    void comunicacaoFiscalRepositoryTemExatamenteOsMetodosFixados() {
        assertEquals(Set.of("save", "findByTenantIdAndDocumentoFiscalId", "findByTenantIdAndDocumentoFiscalIdIn"),
                nomes(ComunicacaoFiscalRepository.class));
    }

    // ---- Teste 6: nenhum nome delete/remove/update ----
    @Test
    void nenhumMetodoTemNomeDeApagarOuAtualizar() {
        String[] proibidos = {"delete", "remove", "update", "saveall", "saveandflush"};
        for (Class<?> repo : REPOSITORIOS_FISCAIS) {
            for (Method m : repo.getMethods()) {
                String nome = m.getName().toLowerCase(Locale.ROOT);
                for (String p : proibidos) {
                    assertFalse(nome.startsWith(p), repo.getSimpleName() + "#" + m.getName()
                            + " começa por prefixo proibido '" + p + "'");
                }
            }
        }
    }

    // ---- Teste 7: entidades @Immutable sem setters públicos ----
    @Test
    void entidadesSaoImutaveisESemSetters() {
        for (Class<?> entidade : List.of(DocumentoFiscal.class, DocumentoFiscalLinha.class)) {
            assertTrue(entidade.isAnnotationPresent(Immutable.class), entidade.getSimpleName() + " sem @Immutable");
            List<String> setters = Arrays.stream(entidade.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()) && m.getName().startsWith("set"))
                    .map(Method::getName)
                    .toList();
            assertTrue(setters.isEmpty(), entidade.getSimpleName() + " declara setters públicos: " + setters);
        }
    }

    // ---- Teste 8: todo o finder recebe um UUID tenantId ----
    @Test
    void todoFinderRecebeTenantId() {
        for (Class<?> repo : List.of(DocumentoFiscalRepository.class, DocumentoFiscalLinhaRepository.class,
                ComunicacaoFiscalRepository.class)) {
            for (Method m : repo.getDeclaredMethods()) {
                if (m.getName().equals("save") || m.isSynthetic()) {
                    continue;
                }
                boolean temTenant = false;
                for (Parameter p : m.getParameters()) {
                    if (!p.getType().equals(UUID.class)) {
                        continue;
                    }
                    Param param = p.getAnnotation(Param.class);
                    boolean ligado = param != null && "tenantId".equals(param.value());
                    boolean nomeado = p.isNamePresent() && "tenantId".equals(p.getName());
                    boolean derivado = param == null && m.getName().contains("TenantId");
                    if (ligado || nomeado || derivado) {
                        temTenant = true;
                    }
                }
                assertTrue(temTenant, repo.getSimpleName() + "#" + m.getName() + " não recebe tenantId");
            }
        }
    }

    // ---- Teste 9: nenhuma fonte de produção apaga/altera documentos fora da ligação ----
    @Test
    void nenhumaFonteApagaOuAlteraDocumentosFiscais() throws IOException {
        Path raiz = Path.of("src/main/java");
        List<String> violacoes = new ArrayList<>();
        long[] contador = {0};
        try (Stream<Path> caminhos = Files.walk(raiz)) {
            caminhos.filter(p -> p.toString().endsWith(".java")).forEach(ficheiro -> {
                contador[0]++;
                String c;
                try {
                    c = Files.readString(ficheiro).toLowerCase(Locale.ROOT);
                } catch (IOException e) {
                    throw new IllegalStateException("Falha a ler " + ficheiro, e);
                }
                for (String f : new String[]{"delete from t_documento_fiscal", "update t_documento_fiscal_linha"}) {
                    if (c.contains(f)) {
                        violacoes.add(ficheiro + " contém '" + f + "'");
                    }
                }
                boolean ehLigacao = ficheiro.getFileName().toString()
                        .equals("DocumentoFiscalLigacaoClienteRepository.java");
                if (!ehLigacao && c.contains("update t_documento_fiscal")) {
                    violacoes.add(ficheiro + " contém 'update t_documento_fiscal' fora da ligação ao cliente");
                }
            });
        }
        assertTrue(contador[0] > 50, "Poucos ficheiros encontrados em " + raiz.toAbsolutePath());
        assertTrue(violacoes.isEmpty(), String.join("\n", violacoes));
    }

    // ---- Teste 10: nenhum controlador tem PUT/PATCH/DELETE sobre documentos fiscais ----
    @Test
    void nenhumControladorAlteraOuApagaDocumentosFiscais() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<BeanDefinition> candidatos = scanner.findCandidateComponents("com.lexcv.controllers");
        assertTrue(candidatos.size() >= 5, "Pesquisa de controladores quase vazia: " + candidatos.size());

        for (BeanDefinition candidato : candidatos) {
            if (!(candidato instanceof AnnotatedBeanDefinition abd)) {
                continue;
            }
            Class<?> classe;
            try {
                classe = Class.forName(abd.getMetadata().getClassName());
            } catch (ClassNotFoundException e) {
                fail("Classe não carregável: " + abd.getMetadata().getClassName(), e);
                return;
            }
            String[] caminhosClasse = caminhos(AnnotatedElementUtils.findMergedAnnotation(classe, RequestMapping.class));
            for (Method m : classe.getDeclaredMethods()) {
                RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (rm == null) {
                    continue;
                }
                boolean mutante = Arrays.stream(rm.method()).anyMatch(x ->
                        x == RequestMethod.PUT || x == RequestMethod.PATCH || x == RequestMethod.DELETE);
                if (!mutante) {
                    continue;
                }
                for (String cc : caminhosClasse.length == 0 ? new String[]{""} : caminhosClasse) {
                    String[] cms = caminhos(rm);
                    for (String cm : cms.length == 0 ? new String[]{""} : cms) {
                        String combinado = (cc + "/" + cm).toLowerCase(Locale.ROOT);
                        if (combinado.contains("documentos-fiscais") || combinado.contains("documento-fiscal")) {
                            fail("Handler PUT/PATCH/DELETE sobre documentos fiscais: "
                                    + classe.getSimpleName() + "#" + m.getName() + " -> " + combinado);
                        }
                    }
                }
            }
        }
    }

    private static String[] caminhos(RequestMapping rm) {
        if (rm == null) {
            return new String[0];
        }
        return rm.value().length > 0 ? rm.value() : rm.path();
    }
}
