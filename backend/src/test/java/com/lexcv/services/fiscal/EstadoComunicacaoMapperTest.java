package com.lexcv.services.fiscal;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexcv.fiscal.efatura.ResultadoComunicacao;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.EstadoComunicacaoFiscal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Phase 136 (DFE-04, DFE-06): a única função resultado -> estado, e o portão de código que
 * impede qualquer caminho para AUTORIZADO/PRODUCAO no código de produção.
 */
class EstadoComunicacaoMapperTest {

    private static final ResultadoComunicacao ACEITE = new ResultadoComunicacao.AceiteSimulado("SIMULADO-x");
    private static final ResultadoComunicacao REJEITADO = new ResultadoComunicacao.Rejeitado("XSD_INVALIDO", "m");
    private static final ResultadoComunicacao TRANSITORIO = new ResultadoComunicacao.ErroTransitorio("FALHA_SIMULADA", "m");

    @Test
    void maximoDeTentativasEOito() {
        assertThat(EstadoComunicacaoMapper.MAX_TENTATIVAS).isEqualTo(8);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void aceiteSimuladoSoPodeSerAceiteSimulado(int tentativas) {
        assertThat(EstadoComunicacaoMapper.estadoPara(ACEITE, AmbienteFiscal.SIMULADO, tentativas))
                .isEqualTo(EstadoComunicacaoFiscal.ACEITE_SIMULADO);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 7, 8, 9})
    void rejeitadoETerminalDeImediato(int tentativas) {
        assertThat(EstadoComunicacaoMapper.estadoPara(REJEITADO, AmbienteFiscal.SIMULADO, tentativas))
                .isEqualTo(EstadoComunicacaoFiscal.REJEITADO);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7})
    void erroTransitorioAntesDoMaximoFicaPendente(int tentativas) {
        assertThat(EstadoComunicacaoMapper.estadoPara(TRANSITORIO, AmbienteFiscal.SIMULADO, tentativas))
                .isEqualTo(EstadoComunicacaoFiscal.PENDENTE);
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 9})
    void erroTransitorioNoMaximoFicaErro(int tentativas) {
        assertThat(EstadoComunicacaoMapper.estadoPara(TRANSITORIO, AmbienteFiscal.SIMULADO, tentativas))
                .isEqualTo(EstadoComunicacaoFiscal.ERRO);
    }

    private static final Pattern[] PROIBIDOS = {
            Pattern.compile("\\bAUTORIZADO\\s*\\("),
            Pattern.compile("\\.AUTORIZADO\\b"),
            Pattern.compile("\\bPRODUCAO\\s*\\("),
            Pattern.compile("\\.PRODUCAO\\b"),
    };

    private static List<String> linhasDeCodigo(Path ficheiro) {
        try {
            List<String> codigo = new ArrayList<>();
            for (String linha : Files.readAllLines(ficheiro, StandardCharsets.UTF_8)) {
                String t = linha.trim();
                if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) {
                    continue;
                }
                codigo.add(linha);
            }
            return codigo;
        } catch (IOException e) {
            throw new IllegalStateException(ficheiro.toString(), e);
        }
    }

    /** Portão de código: nenhum identificador AUTORIZADO/PRODUCAO; o literal só no @Check. */
    @Test
    void codigoDeProducaoNaoTemCaminhoParaAutorizado() throws IOException {
        Path raiz = Paths.get("src/main/java");
        assertThat(raiz).isDirectory();
        List<Path> ficheiros;
        try (Stream<Path> s = Files.walk(raiz)) {
            ficheiros = s.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertThat(ficheiros).hasSizeGreaterThan(100);

        List<String> violacoes = new ArrayList<>();
        List<String> comLiteral = new ArrayList<>();
        for (Path f : ficheiros) {
            List<String> codigo = linhasDeCodigo(f);
            for (String linha : codigo) {
                for (Pattern p : PROIBIDOS) {
                    if (p.matcher(linha).find()) {
                        violacoes.add(f + ": " + linha.trim());
                    }
                }
            }
            if (codigo.stream().anyMatch(l -> l.contains("AUTORIZADO"))) {
                comLiteral.add(f.getFileName().toString());
            }
        }
        assertThat(violacoes).isEmpty();
        assertThat(comLiteral).containsExactly("ComunicacaoFiscal.java");
        String check = String.join("\n", linhasDeCodigo(
                raiz.resolve("com/lexcv/models/ComunicacaoFiscal.java")));
        assertThat(check).contains("estado <> 'AUTORIZADO' OR ambiente = 'PRODUCAO'");
    }
}
