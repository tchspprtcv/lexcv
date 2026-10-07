package com.lexcv.fiscal.pdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Phase 137 (ENTR-01; T-137-19): resolve os recursos pedidos pelo template do PDF fiscal apenas a
 * partir do classpath e apenas para os quatro ficheiros vendorizados (lista branca fixa). Qualquer
 * outro URI -- {@code http:}, {@code https:}, {@code file:}, {@code data:}, {@code jar:}, {@code ../}
 * para fora de {@code pdf/}, nomes fora da lista, query ou fragmento -- resolve para {@code null}, o
 * que faz o OpenHTMLtoPDF recusar o recurso sem o ir buscar a outro lado.
 *
 * <p>Mesma política de {@code ClasspathXsdResolver}: a normalização usa {@link URI#resolve} sobre
 * uma base sintética hierárquica, nunca a API de caminhos do sistema de ficheiros (FindSecBugs
 * PATH_TRAVERSAL_IN). As recusas são registadas em DEBUG só com o esquema: o URI completo pode
 * conter dados da fotografia do documento.
 */
public final class ClasspathPdfResolver {

    private static final Logger log = LoggerFactory.getLogger(ClasspathPdfResolver.class);

    /** Prefixo dos URIs que este resolvedor emite e aceita como base. */
    public static final String PREFIXO = "classpath:pdf/";

    /** URI canónico da folha de estilo do template. */
    public static final String CSS = PREFIXO + "documento-fiscal.css";

    static final String RAIZ_RECURSOS = "pdf/";
    private static final String ESQUEMA_SINTETICO = "classpath";
    private static final String CAMINHO_SINTETICO = "/pdf/";

    static final String FONTE_REGULAR = "fonts/DejaVuSans.ttf";
    static final String FONTE_NEGRITO = "fonts/DejaVuSans-Bold.ttf";
    static final String FONTE_MONO = "fonts/DejaVuSansMono.ttf";
    static final String FOLHA_ESTILO = "documento-fiscal.css";

    /** Os quatro recursos do PDF, relativos a {@code pdf/}. */
    static final Set<String> LISTA_BRANCA = Set.of(FONTE_REGULAR, FONTE_NEGRITO, FONTE_MONO, FOLHA_ESTILO);

    private ClasspathPdfResolver() {
    }

    /**
     * URI canónico ({@code classpath:pdf/...}) do recurso pedido, ou {@code null} se, depois de
     * normalizado contra {@code baseUri}, não for um dos quatro recursos da lista branca.
     *
     * @param uri     o URI pedido pelo documento (absoluto {@code classpath:pdf/...} ou relativo)
     * @param baseUri a base de resolução; tem de começar por {@link #PREFIXO} para aceitar relativos
     */
    public static String resolver(String uri, String baseUri) {
        String relativo = resolverRelativo(uri, baseUri);
        if (relativo == null) {
            if (log.isDebugEnabled()) {
                log.debug("Recurso do PDF recusado (esquema: {})", esquema(uri));
            }
            return null;
        }
        return PREFIXO + relativo;
    }

    /** Caminho relativo a {@code pdf/} de um URI canónico já resolvido, ou {@code null}. */
    static String relativoDeCanonico(String canonico) {
        return resolverRelativo(canonico, null);
    }

    private static String resolverRelativo(String uri, String baseUri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        boolean absoluto = uri.startsWith(PREFIXO);
        if (!absoluto && (baseUri == null || !baseUri.startsWith(PREFIXO))) {
            return null;
        }
        try {
            URI base = new URI(ESQUEMA_SINTETICO, null, CAMINHO_SINTETICO
                    + (baseUri != null && baseUri.startsWith(PREFIXO) ? baseUri.substring(PREFIXO.length()) : ""), null);
            URI pedido = absoluto
                    ? new URI(ESQUEMA_SINTETICO, null, CAMINHO_SINTETICO + uri.substring(PREFIXO.length()), null)
                    : new URI(uri);
            URI resolvido = base.resolve(pedido).normalize();
            if (!ESQUEMA_SINTETICO.equals(resolvido.getScheme()) || resolvido.getRawAuthority() != null
                    || resolvido.getRawQuery() != null || resolvido.getRawFragment() != null) {
                return null;
            }
            String caminho = resolvido.getPath();
            if (caminho == null || !caminho.startsWith(CAMINHO_SINTETICO)) {
                return null;
            }
            String relativo = caminho.substring(CAMINHO_SINTETICO.length());
            return LISTA_BRANCA.contains(relativo) ? relativo : null;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Bytes de um recurso da lista branca (relativo a {@code pdf/}), ou {@code null}. */
    static byte[] lerRecurso(String relativo) {
        if (relativo == null || !LISTA_BRANCA.contains(relativo)) {
            return null;
        }
        ClassLoader cl = ClasspathPdfResolver.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(RAIZ_RECURSOS + relativo)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static String esquema(String uri) {
        if (uri == null) {
            return "null";
        }
        int doisPontos = uri.indexOf(':');
        if (doisPontos <= 0 || doisPontos > 16) {
            return "relativo";
        }
        String esquema = uri.substring(0, doisPontos);
        return esquema.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.')
                ? esquema : "invalido";
    }
}
