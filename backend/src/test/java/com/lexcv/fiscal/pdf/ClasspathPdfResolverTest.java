package com.lexcv.fiscal.pdf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 137 (ENTR-01; T-137-19): só os quatro recursos vendorizados do PDF podem ser resolvidos. */
class ClasspathPdfResolverTest {

    private static final String CSS = "classpath:pdf/documento-fiscal.css";

    @Test
    void listaBrancaEExatamenteOsQuatroRecursos() {
        assertThat(ClasspathPdfResolver.LISTA_BRANCA).isEqualTo(Set.of(
                "fonts/DejaVuSans.ttf", "fonts/DejaVuSans-Bold.ttf", "fonts/DejaVuSansMono.ttf",
                "documento-fiscal.css"));
        assertThat(ClasspathPdfResolver.PREFIXO).isEqualTo("classpath:pdf/");
    }

    @Test
    void resolveOsRecursosDaListaBrancaSemBase() {
        assertThat(ClasspathPdfResolver.resolver("classpath:pdf/fonts/DejaVuSans.ttf", null))
                .isEqualTo("classpath:pdf/fonts/DejaVuSans.ttf");
        assertThat(ClasspathPdfResolver.resolver(CSS, null)).isEqualTo(CSS);
    }

    @Test
    void resolveRelativoAUmaBaseDaListaBranca() {
        assertThat(ClasspathPdfResolver.resolver("fonts/DejaVuSansMono.ttf", CSS))
                .isEqualTo("classpath:pdf/fonts/DejaVuSansMono.ttf");
        assertThat(ClasspathPdfResolver.resolver("./documento-fiscal.css", CSS)).isEqualTo(CSS);
    }

    @Test
    void lerRecursoDevolveOsBytesDoClasspath() throws IOException {
        for (String relativo : ClasspathPdfResolver.LISTA_BRANCA) {
            if (relativo.endsWith(".css")) {
                continue; // o CSS é criado na tarefa 2; verificado no teste do renderer
            }
            try (InputStream esperado = getClass().getClassLoader().getResourceAsStream("pdf/" + relativo)) {
                assertThat(esperado).as(relativo).isNotNull();
                assertThat(ClasspathPdfResolver.lerRecurso(relativo)).isEqualTo(esperado.readAllBytes());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:9/x.png",
            "https://example.cv/x.css",
            "file:///etc/hostname",
            "data:image/png;base64,iVBORw0KGgo=",
            "jar:file:/tmp/x.jar!/pdf/documento-fiscal.css",
            "ftp://example.cv/x",
            "classpath:pdf/../application.yml",
            "classpath:pdf/fonts/x.ttf",
            "classpath:pdf/README.md",
            "classpath:pdf/fonts/LICENSE-DejaVu.txt",
            "classpath:pdf/documento-fiscal.css?x=1",
            "classpath:pdf/documento-fiscal.css#a",
            "classpath://evil/pdf/documento-fiscal.css",
            "classpath:application.yml",
            "documento-fiscal.css",
            "",
    })
    void recusaTudoOQueNaoEstaNaListaBrancaSemBase(String uri) {
        assertThat(ClasspathPdfResolver.resolver(uri, null)).as(uri).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../application.yml",
            "../application.yml",
            "fonts/../../application.yml",
            "README.md",
            "fonts/x.ttf",
            "//evil.example/pdf/documento-fiscal.css",
            "http://127.0.0.1:9/x.png",
            "file:///etc/hostname",
            "data:text/css,body{}",
            "documento-fiscal.css?x",
    })
    void recusaRelativosQueEscapamOuNaoEstaoNaListaBranca(String uri) {
        assertThat(ClasspathPdfResolver.resolver(uri, CSS)).as(uri).isNull();
    }

    @Test
    void recusaBaseForaDaListaBrancaENulos() {
        assertThat(ClasspathPdfResolver.resolver(null, null)).isNull();
        assertThat(ClasspathPdfResolver.resolver(null, CSS)).isNull();
        assertThat(ClasspathPdfResolver.resolver("fonts/DejaVuSans.ttf", "file:///tmp/")).isNull();
        assertThat(ClasspathPdfResolver.resolver("fonts/DejaVuSans.ttf", "http://example.cv/pdf/")).isNull();
    }

    @Test
    void lerRecursoRecusaNomesForaDaListaBranca() {
        assertThat(ClasspathPdfResolver.lerRecurso("README.md")).isNull();
        assertThat(ClasspathPdfResolver.lerRecurso("fonts/LICENSE-DejaVu.txt")).isNull();
        assertThat(ClasspathPdfResolver.lerRecurso("../application.yml")).isNull();
        assertThat(ClasspathPdfResolver.lerRecurso(null)).isNull();
    }
}
