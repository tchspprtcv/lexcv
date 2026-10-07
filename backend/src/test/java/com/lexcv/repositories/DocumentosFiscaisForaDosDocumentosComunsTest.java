package com.lexcv.repositories;

import com.lexcv.services.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 137 (ENTR-07, T-137-64): os documentos fiscais (PDF e XML) ficam FORA dos documentos comuns.
 *
 * <p>Os documentos comuns ({@code t_documento}, {@code Documento}, {@code DocumentoRepository}) têm
 * endpoints de eliminação em {@code ResourceController} que apagam a linha e o objeto MinIO da sua
 * {@code caminho_arquivo}. Um documento fiscal nunca pode ser apagado por esse caminho. Esta guarda
 * prova-o pela construção:
 * <ul>
 *   <li>nenhum código fiscal de produção ({@code com/lexcv/services/fiscal}, {@code com/lexcv/fiscal}
 *       e {@code DocumentoFiscalController}) usa {@code Documento}, {@code DocumentoRepository} ou a
 *       tabela {@code t_documento} -- um PDF/XML fiscal nunca ganha uma linha em
 *       {@code t_documento}, por isso não há id de documento comum que o alcance;</li>
 *   <li>as chaves MinIO fiscais vivem num espaço próprio
 *       ({@code <tenantId>/documentos-fiscais/<documentoId>/...}), distinto das chaves genéricas
 *       {@code <tenantId>/<documentoId>/...};</li>
 *   <li>nenhum controlador expõe DELETE num caminho de documentos fiscais (reafirma o Teste 10 de
 *       {@code DocumentoFiscalImutabilidadeTest}).</li>
 * </ul>
 */
class DocumentosFiscaisForaDosDocumentosComunsTest {

    private static final Path RAIZ = Path.of("src/main/java/com/lexcv");

    private static final Pattern PROIBIDOS = Pattern.compile(
            "import\\s+com\\.lexcv\\.models\\.Documento\\s*;"
                    + "|import\\s+com\\.lexcv\\.models\\.\\*\\s*;"
                    + "|import\\s+com\\.lexcv\\.repositories\\.\\*\\s*;"
                    + "|\\bDocumentoRepository\\b"
                    + "|\\bt_documento\\b");

    private static List<Path> fontesFiscais() throws IOException {
        List<Path> fontes = new ArrayList<>();
        for (Path dir : List.of(RAIZ.resolve("services/fiscal"), RAIZ.resolve("fiscal"))) {
            try (Stream<Path> caminhos = Files.walk(dir)) {
                caminhos.filter(p -> p.toString().endsWith(".java")).forEach(fontes::add);
            }
        }
        fontes.add(RAIZ.resolve("controllers/DocumentoFiscalController.java"));
        return fontes;
    }

    @Test
    void codigoFiscalNuncaUsaOsDocumentosComuns() throws IOException {
        List<Path> fontes = fontesFiscais();
        assertTrue(fontes.size() > 40, "Poucas fontes fiscais encontradas: " + fontes.size());
        List<String> violacoes = new ArrayList<>();
        for (Path fonte : fontes) {
            String conteudo = Files.readString(fonte);
            var m = PROIBIDOS.matcher(conteudo);
            while (m.find()) {
                violacoes.add(fonte + " contém '" + m.group() + "'");
            }
        }
        assertTrue(violacoes.isEmpty(), String.join("\n", violacoes));
    }

    @Test
    void padraoDetetaAsReferenciasProibidas() {
        assertTrue(PROIBIDOS.matcher("import com.lexcv.models.Documento;").find());
        assertTrue(PROIBIDOS.matcher("private final DocumentoRepository repo;").find());
        assertTrue(PROIBIDOS.matcher("insert into t_documento (id)").find());
        assertTrue(!PROIBIDOS.matcher("import com.lexcv.models.DocumentoFiscal;").find());
        assertTrue(!PROIBIDOS.matcher("DocumentoFiscalRepository t_documento_fiscal").find());
    }

    @Test
    void chavesFiscaisVivemNoEspacoDocumentosFiscais() throws Exception {
        Field campo = StorageService.class.getDeclaredField("CHAVE_FISCAL");
        campo.setAccessible(true);
        Pattern chave = (Pattern) campo.get(null);
        assertTrue(chave.pattern().contains("/documentos-fiscais/"), chave.pattern());

        String pdfService = Files.readString(RAIZ.resolve("services/fiscal/PdfDocumentoFiscalService.java"));
        assertTrue(pdfService.contains("\"/documentos-fiscais/\""),
                "PdfDocumentoFiscalService deixou de construir a chave em /documentos-fiscais/");
    }

    @Test
    void nenhumControladorApagaDocumentosFiscais() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<BeanDefinition> candidatos = scanner.findCandidateComponents("com.lexcv.controllers");
        assertTrue(candidatos.size() >= 5, "Pesquisa de controladores quase vazia: " + candidatos.size());

        for (BeanDefinition candidato : candidatos) {
            if (!(candidato instanceof AnnotatedBeanDefinition abd)) {
                continue;
            }
            Class<?> classe = Class.forName(abd.getMetadata().getClassName());
            String[] caminhosClasse = caminhos(AnnotatedElementUtils.findMergedAnnotation(classe, RequestMapping.class));
            for (Method m : classe.getDeclaredMethods()) {
                RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (rm == null || Arrays.stream(rm.method()).noneMatch(x -> x == RequestMethod.DELETE)) {
                    continue;
                }
                for (String cc : caminhosClasse.length == 0 ? new String[]{""} : caminhosClasse) {
                    String[] cms = caminhos(rm);
                    for (String cm : cms.length == 0 ? new String[]{""} : cms) {
                        String combinado = (cc + "/" + cm).toLowerCase(Locale.ROOT);
                        if (combinado.contains("documentos-fiscais") || combinado.contains("documento-fiscal")) {
                            fail("DELETE sobre documentos fiscais: " + classe.getSimpleName() + "#" + m.getName()
                                    + " -> " + combinado);
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
