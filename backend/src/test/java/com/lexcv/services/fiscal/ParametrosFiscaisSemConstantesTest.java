package com.lexcv.services.fiscal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 133 (CFG-04), Plan 02: gate de código-fonte. As taxas fiscais são dados com vigência em
 * {@code t_parametro_fiscal}, nunca constantes no código de produção.
 *
 * <p>Falha se algum ficheiro em {@code src/main/java} contiver um literal decimal "zero ponto
 * quinze" ou "zero ponto vinte" (proibido em todo o lado, incluindo comentários, para manter o
 * gate simples), ou se algum ficheiro além de {@code seed/DatabaseSeeder.java} construir um
 * {@code BigDecimal} com o valor quinze ou vinte (por string ou {@code valueOf}).
 */
class ParametrosFiscaisSemConstantesTest {

    private static final Path RAIZ_MAIN = Path.of("src/main/java");

    private static final Pattern FRACAO_TAXA = Pattern.compile("\\b0\\.(15|20)\\b");
    private static final Pattern TAXA_BIGDECIMAL = Pattern.compile(
            "BigDecimal\\(\"(15|20)(\\.0+)?\"\\)|BigDecimal\\.valueOf\\((15|20)(\\.0+)?\\)");

    private static final String UNICO_FICHEIRO_PERMITIDO = "com/lexcv/seed/DatabaseSeeder.java";

    private static List<Path> ficheirosJava() throws IOException {
        try (Stream<Path> caminhos = Files.walk(RAIZ_MAIN)) {
            return caminhos.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String relativo(Path p) {
        return RAIZ_MAIN.relativize(p).toString().replace('\\', '/');
    }

    @Test
    void nenhumFicheiroDeProducaoContemFracaoDeTaxa() throws IOException {
        List<String> ofensores = new ArrayList<>();
        for (Path p : ficheirosJava()) {
            if (FRACAO_TAXA.matcher(Files.readString(p, StandardCharsets.UTF_8)).find()) {
                ofensores.add(relativo(p));
            }
        }
        assertTrue(ofensores.isEmpty(), "Literal de taxa fiscal em código de produção: " + ofensores);
    }

    @Test
    void apenasOSeederConstroiTaxasComoBigDecimal() throws IOException {
        List<Path> ficheiros = ficheirosJava();
        assertTrue(ficheiros.size() > 10, "Gate sem ficheiros a analisar: caminho errado?");

        List<String> ofensores = new ArrayList<>();
        for (Path p : ficheiros) {
            if (TAXA_BIGDECIMAL.matcher(Files.readString(p, StandardCharsets.UTF_8)).find()
                    && !relativo(p).equals(UNICO_FICHEIRO_PERMITIDO)) {
                ofensores.add(relativo(p));
            }
        }
        assertEquals(List.of(), ofensores, "Taxa fiscal construída fora do seeder: " + ofensores);
    }
}
