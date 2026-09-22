package com.lexcv.repositories;

import com.lexcv.models.AuditLog;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Fase 128 (AUDT-04), Decisao 6: o gate estrutural que prova, por reflexao e por leitura de
 * ficheiro fonte -- nunca por chamar o registo em runtime -- que {@code t_audit_log} nao tem
 * superficie de escrita alem de uma insercao inicial. Nao ha precedente para este teste neste
 * codebase (128-PATTERNS.md, seccao "No Analog Found": "nenhum equivalente no lado Java do
 * verify-bloqueio-rbac.mjs/verify-consola-moldes.mjs do frontend").
 *
 * <p>Este teste falha quando alguem alarga o repositorio (volta a extender
 * {@code JpaRepository}/{@code CrudRepository}/etc.), reintroduz um caminho de apagar/alterar,
 * ou acrescenta um endpoint de escrita sobre o registo de auditoria. Quando falhar porque um
 * metodo de leitura legitimo foi acrescentado ao repositorio, a correcao e acrescentar esse
 * nome ao conjunto esperado no Teste 2, deliberadamente -- nunca relaxar a verificacao.
 */
class AuditLogImutabilidadeTest {

    // ---------------------------------------------------------------------
    // Teste 1: AuditLogRepository e Repository, e NAO e nenhuma das bases
    // mais permissivas.
    // ---------------------------------------------------------------------
    @Test
    void repositorioEAssignavelASoRepositoryENaoAsBasesMaisPermissivas() {
        assertTrue(Repository.class.isAssignableFrom(AuditLogRepository.class),
                "AuditLogRepository deve ser assignavel a Repository");
        assertFalse(CrudRepository.class.isAssignableFrom(AuditLogRepository.class),
                "AuditLogRepository NAO deve ser assignavel a CrudRepository (traz delete/deleteAll)");
        assertFalse(ListCrudRepository.class.isAssignableFrom(AuditLogRepository.class),
                "AuditLogRepository NAO deve ser assignavel a ListCrudRepository");
        assertFalse(PagingAndSortingRepository.class.isAssignableFrom(AuditLogRepository.class),
                "AuditLogRepository NAO deve ser assignavel a PagingAndSortingRepository");
        assertFalse(JpaRepository.class.isAssignableFrom(AuditLogRepository.class),
                "AuditLogRepository NAO deve ser assignavel a JpaRepository (traz saveAll/deleteAll/saveAndFlush)");
    }

    // ---------------------------------------------------------------------
    // Teste 2: o conjunto de nomes de metodo e exactamente o pinado; nenhum
    // metodo carrega @Modifying.
    // ---------------------------------------------------------------------
    @Test
    void conjuntoDeMetodosEExactamenteOPinadoSemModifying() {
        Set<String> nomesEsperados = Set.of("save", "findByTenantIdAndProcessoIdOrderByTimestampDesc",
                "buscarEventosRbac");

        Set<String> nomesEncontrados = Arrays.stream(AuditLogRepository.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(nomesEsperados, nomesEncontrados,
                "AuditLogRepository deve declarar exactamente {save, "
                        + "findByTenantIdAndProcessoIdOrderByTimestampDesc, buscarEventosRbac} -- "
                        + "se um metodo de leitura legitimo foi acrescentado, acrescentar o seu nome "
                        + "aqui deliberadamente, nunca relaxar a verificacao");

        for (Method metodo : AuditLogRepository.class.getMethods()) {
            assertFalse(metodo.isAnnotationPresent(Modifying.class),
                    "Nenhum metodo de AuditLogRepository deve carregar @Modifying: " + metodo.getName());
        }
    }

    // ---------------------------------------------------------------------
    // Teste 3: nenhum nome de metodo comeca por delete/remove/update/saveAll
    // /saveAndFlush.
    // ---------------------------------------------------------------------
    @Test
    void nenhumMetodoTemNomeDeApagarOuActualizar() {
        String[] prefixosProibidos = {"delete", "remove", "update", "saveAll", "saveAndFlush"};

        for (Method metodo : AuditLogRepository.class.getMethods()) {
            String nome = metodo.getName().toLowerCase(Locale.ROOT);
            for (String prefixo : prefixosProibidos) {
                assertFalse(nome.startsWith(prefixo.toLowerCase(Locale.ROOT)),
                        "Metodo '" + metodo.getName() + "' comeca por prefixo proibido '" + prefixo + "'");
            }
        }
    }

    // ---------------------------------------------------------------------
    // Teste 4: AuditLog carrega @Immutable.
    // ---------------------------------------------------------------------
    @Test
    void entidadeCarregaImmutable() {
        assertTrue(AuditLog.class.isAnnotationPresent(Immutable.class),
                "AuditLog deve carregar org.hibernate.annotations.Immutable");
    }

    // ---------------------------------------------------------------------
    // Teste 5: nenhum @RestController sob com.lexcv.controllers tem um
    // handler mutante (POST/PUT/PATCH/DELETE) cujo caminho combinado
    // classe+metodo contenha "audit" (case-insensitive -- cobre "auditoria").
    // ---------------------------------------------------------------------
    @Test
    void nenhumControladorTemHandlerMutanteSobreAudit() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<org.springframework.beans.factory.config.BeanDefinition> candidatos =
                scanner.findCandidateComponents("com.lexcv.controllers");

        assertTrue(candidatos.size() >= 5,
                "A pesquisa por @RestController em com.lexcv.controllers encontrou apenas "
                        + candidatos.size() + " classes -- uma pesquisa vazia ou quase vazia nao pode "
                        + "passar vacuamente este teste");

        for (org.springframework.beans.factory.config.BeanDefinition candidato : candidatos) {
            if (!(candidato instanceof AnnotatedBeanDefinition annotatedBeanDefinition)) {
                continue;
            }
            String nomeClasse = annotatedBeanDefinition.getMetadata().getClassName();
            Class<?> classe;
            try {
                classe = Class.forName(nomeClasse);
            } catch (ClassNotFoundException e) {
                fail("Nao foi possivel carregar a classe encontrada pela pesquisa: " + nomeClasse, e);
                return;
            }

            String[] caminhosClasse = caminhosDe(classe.getAnnotation(RequestMapping.class));

            for (Method metodo : classe.getDeclaredMethods()) {
                verificarHandlerMutante(classe, metodo, caminhosClasse, PostMapping.class,
                        metodo.isAnnotationPresent(PostMapping.class)
                                ? caminhosDe(metodo.getAnnotation(PostMapping.class)) : null);
                verificarHandlerMutante(classe, metodo, caminhosClasse, PutMapping.class,
                        metodo.isAnnotationPresent(PutMapping.class)
                                ? caminhosDe(metodo.getAnnotation(PutMapping.class)) : null);
                verificarHandlerMutante(classe, metodo, caminhosClasse, PatchMapping.class,
                        metodo.isAnnotationPresent(PatchMapping.class)
                                ? caminhosDe(metodo.getAnnotation(PatchMapping.class)) : null);
                verificarHandlerMutante(classe, metodo, caminhosClasse, DeleteMapping.class,
                        metodo.isAnnotationPresent(DeleteMapping.class)
                                ? caminhosDe(metodo.getAnnotation(DeleteMapping.class)) : null);

                RequestMapping requestMapping = AnnotatedElementUtils.findMergedAnnotation(metodo, RequestMapping.class);
                if (requestMapping != null) {
                    boolean mutante = Arrays.stream(requestMapping.method())
                            .anyMatch(m -> m == RequestMethod.POST || m == RequestMethod.PUT
                                    || m == RequestMethod.PATCH || m == RequestMethod.DELETE);
                    if (mutante) {
                        verificarCaminhoNaoContemAudit(classe, metodo, caminhosClasse, caminhosDe(requestMapping));
                    }
                }
            }
        }
    }

    private void verificarHandlerMutante(Class<?> classe, Method metodo, String[] caminhosClasse,
                                          Class<? extends java.lang.annotation.Annotation> anotacao,
                                          String[] caminhosMetodo) {
        if (caminhosMetodo == null) {
            return;
        }
        verificarCaminhoNaoContemAudit(classe, metodo, caminhosClasse, caminhosMetodo);
    }

    private void verificarCaminhoNaoContemAudit(Class<?> classe, Method metodo, String[] caminhosClasse,
                                                 String[] caminhosMetodo) {
        for (String caminhoClasse : caminhosClasse.length == 0 ? new String[]{""} : caminhosClasse) {
            for (String caminhoMetodo : caminhosMetodo.length == 0 ? new String[]{""} : caminhosMetodo) {
                String combinado = (caminhoClasse + "/" + caminhoMetodo).toLowerCase(Locale.ROOT);
                if (combinado.contains("audit")) {
                    fail("Handler mutante com caminho contendo 'audit' encontrado: "
                            + classe.getSimpleName() + "#" + metodo.getName() + " -> " + combinado);
                }
            }
        }
    }

    private String[] caminhosDe(RequestMapping requestMapping) {
        if (requestMapping == null) {
            return new String[0];
        }
        String[] valor = requestMapping.value();
        return valor.length > 0 ? valor : requestMapping.path();
    }

    private String[] caminhosDe(PostMapping mapping) {
        String[] valor = mapping.value();
        return valor.length > 0 ? valor : mapping.path();
    }

    private String[] caminhosDe(PutMapping mapping) {
        String[] valor = mapping.value();
        return valor.length > 0 ? valor : mapping.path();
    }

    private String[] caminhosDe(PatchMapping mapping) {
        String[] valor = mapping.value();
        return valor.length > 0 ? valor : mapping.path();
    }

    private String[] caminhosDe(DeleteMapping mapping) {
        String[] valor = mapping.value();
        return valor.length > 0 ? valor : mapping.path();
    }

    // ---------------------------------------------------------------------
    // Teste 6: nenhum ficheiro .java sob src/main/java contem uma instrucao
    // DELETE/UPDATE contra t_audit_log/auditlog.
    // ---------------------------------------------------------------------
    @Test
    void nenhumFicheiroFonteContemInstrucaoDeApagarOuActualizarSobreAuditLog() throws IOException {
        String[] fragmentosProibidos = {
                "delete from t_audit_log",
                "update t_audit_log",
                "delete from auditlog",
                "update auditlog "
        };

        Path raiz = Path.of("src/main/java");
        long[] contador = {0};

        try (Stream<Path> caminhos = Files.walk(raiz)) {
            caminhos.filter(p -> p.toString().endsWith(".java"))
                    .forEach(ficheiro -> {
                        contador[0]++;
                        String conteudo;
                        try {
                            conteudo = Files.readString(ficheiro).toLowerCase(Locale.ROOT);
                        } catch (IOException e) {
                            throw new RuntimeException("Falha a ler " + ficheiro, e);
                        }
                        for (String fragmento : fragmentosProibidos) {
                            if (conteudo.contains(fragmento)) {
                                fail("Ficheiro " + ficheiro + " contem o fragmento proibido '" + fragmento + "'");
                            }
                        }
                    });
        }

        assertTrue(contador[0] > 50,
                "A pesquisa em " + raiz.toAbsolutePath() + " encontrou apenas " + contador[0]
                        + " ficheiros .java -- um valor tao baixo sugere working directory errado, "
                        + "o que nao pode passar vacuamente este teste");
    }
}
