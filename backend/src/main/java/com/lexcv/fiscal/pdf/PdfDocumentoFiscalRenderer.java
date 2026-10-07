package com.lexcv.fiscal.pdf;

import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.fontbox.FontBoxFont;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.font.CIDFontMapping;
import org.apache.pdfbox.pdmodel.font.FontMapper;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.PDCIDSystemInfo;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Phase 137 (ENTR-01, DFE-06): gera o PDF de uma Fatura-Recibo ou Nota de Crédito a partir da
 * fotografia guardada ({@link DadosPdfDocumentoFiscal}), com OpenHTMLtoPDF 1.1.87 / PDFBox 3.0.7
 * (137-SPIKE.md A/B).
 *
 * <p>Arranque fail-fast (padrão {@code DfeValidador}): a folha de estilo e as três fontes DejaVu são
 * lidas uma vez, através da lista branca de {@link ClasspathPdfResolver}; se faltar alguma, o
 * arranque falha com {@link #MENSAGEM_ARRANQUE}.
 *
 * <p>Endurecimento (T-137-19): o resolvedor de URIs do builder delega em
 * {@link ClasspathPdfResolver#resolver}; a implementação HTTP e as dos protocolos {@code file},
 * {@code jar}, {@code http}, {@code https}, {@code ftp} e {@code data} são substituídas por fábricas
 * que recusam tudo (defesa em profundidade); o protocolo sintético {@code classpath} serve apenas a
 * folha de estilo já carregada em memória. As fontes são registadas a partir de bytes (nunca de
 * ficheiros) e o mapeador de fontes do PDFBox é substituído por um que não varre as fontes do
 * sistema (137-SPIKE.md B, achado 3). Sem transação e sem I/O além de memória.
 *
 * <p>Os valores do documento só aparecem em DEBUG; {@link FalhaGeracaoPdf} tem mensagem fixa (T-137-22).
 */
@Component
public final class PdfDocumentoFiscalRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentoFiscalRenderer.class);

    static final String MENSAGEM_ARRANQUE = "Recursos do PDF fiscal indisponíveis no classpath";

    private static final String FAMILIA = "DejaVu Sans";
    private static final String FAMILIA_MONO = "DejaVu Sans Mono";
    private static final String PRODUTOR = "LexCV";
    private static final String[] PROTOCOLOS_RECUSADOS = {"file", "jar", "http", "https", "ftp", "data"};

    private final byte[] css;
    private final byte[] fonteRegular;
    private final byte[] fonteNegrito;
    private final byte[] fonteMono;
    private final Consumer<String> espiaResolver;

    public PdfDocumentoFiscalRenderer() {
        this(ClasspathPdfResolver.FOLHA_ESTILO, null);
    }

    /**
     * Construtor de teste: folha de estilo alternativa (tem de estar na lista branca) e um espião
     * opcional que recebe cada URI pedido ao resolvedor.
     */
    PdfDocumentoFiscalRenderer(String folhaEstilo, Consumer<String> espiaResolver) {
        this.css = obrigatorio(folhaEstilo);
        this.fonteRegular = obrigatorio(ClasspathPdfResolver.FONTE_REGULAR);
        this.fonteNegrito = obrigatorio(ClasspathPdfResolver.FONTE_NEGRITO);
        this.fonteMono = obrigatorio(ClasspathPdfResolver.FONTE_MONO);
        this.espiaResolver = espiaResolver;
        MapeadorFontesClasspath.instalar(fonteRegular);
    }

    private static byte[] obrigatorio(String relativo) {
        byte[] bytes = ClasspathPdfResolver.lerRecurso(relativo);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException(MENSAGEM_ARRANQUE);
        }
        return bytes;
    }

    /**
     * @return os bytes do PDF
     * @throws FalhaGeracaoPdf se o renderer falhar (mensagem fixa; causa mantida para o log)
     */
    public byte[] renderizar(DadosPdfDocumentoFiscal dados) {
        String xhtml = ModeloPdfDocumentoFiscal.xhtml(dados);
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        try {
            PdfRendererBuilder b = new PdfRendererBuilder().useFastMode();
            b.useFont(() -> new ByteArrayInputStream(fonteRegular), FAMILIA, 400, FontStyle.NORMAL, true);
            b.useFont(() -> new ByteArrayInputStream(fonteNegrito), FAMILIA, 700, FontStyle.NORMAL, true);
            b.useFont(() -> new ByteArrayInputStream(fonteMono), FAMILIA_MONO, 400, FontStyle.NORMAL, true);
            b.useUriResolver((String baseUri, String uri) -> {
                if (espiaResolver != null) {
                    espiaResolver.accept(uri);
                }
                return ClasspathPdfResolver.resolver(uri, baseUri);
            });
            FSStreamFactory recusa = url -> null;
            b.useHttpStreamImplementation(recusa);
            b.useProtocolsStreamImplementation(recusa, PROTOCOLOS_RECUSADOS);
            b.useProtocolsStreamImplementation(this::abrirClasspath, "classpath");
            b.withProducer(PRODUTOR);
            b.withHtmlContent(xhtml, ClasspathPdfResolver.PREFIXO);
            b.toStream(out);
            b.run();
        } catch (IOException | RuntimeException e) {
            log.debug("Falha a gerar o PDF do documento {}", dados.numeroFormatado(), e);
            throw new FalhaGeracaoPdf(e);
        }
        return out.toByteArray();
    }

    /** Protocolo {@code classpath}: só a folha de estilo, a partir dos bytes já carregados. */
    private FSStream abrirClasspath(String url) {
        if (!ClasspathPdfResolver.FOLHA_ESTILO.equals(ClasspathPdfResolver.relativoDeCanonico(url))) {
            return null;
        }
        byte[] bytes = css;
        return new FSStream() {
            @Override
            public InputStream getStream() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public Reader getReader() {
                return new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
            }
        };
    }

    /**
     * Mapeador de fontes do PDFBox que nunca varre as fontes do sistema: qualquer fonte pedida por
     * nome (as Standard-14 que o OpenHTMLtoPDF instancia de recurso) é servida pela DejaVu Sans do
     * classpath. Nenhuma destas fontes de recurso chega ao PDF -- o texto usa só as três DejaVu
     * registadas no builder -- mas sem isto o PDFBox lê o sistema de ficheiros e escreve
     * {@code ~/.pdfbox.cache}.
     *
     * <p>Os mapeamentos são declarados como não-recurso ({@code isFallback = false}) de propósito: a
     * substituição é deliberada e, de outro modo, o PDFBox registaria 14 avisos "Using fallback
     * font" em cada PDF gerado, por fontes que nunca chegam ao documento.
     */
    static final class MapeadorFontesClasspath implements FontMapper {

        private final TrueTypeFont fonte;

        private MapeadorFontesClasspath(TrueTypeFont fonte) {
            this.fonte = fonte;
        }

        static synchronized void instalar(byte[] bytesFonte) {
            if (FontMappers.instance() instanceof MapeadorFontesClasspath) {
                return;
            }
            try {
                TrueTypeFont ttf = new TTFParser().parse(new RandomAccessReadBuffer(bytesFonte));
                FontMappers.set(new MapeadorFontesClasspath(ttf));
            } catch (IOException e) {
                throw new IllegalStateException(MENSAGEM_ARRANQUE, e);
            }
        }

        @Override
        public FontMapping<TrueTypeFont> getTrueTypeFont(String baseFont, PDFontDescriptor fontDescriptor) {
            return new FontMapping<>(fonte, false);
        }

        @Override
        public FontMapping<FontBoxFont> getFontBoxFont(String baseFont, PDFontDescriptor fontDescriptor) {
            return new FontMapping<>(fonte, false);
        }

        @Override
        public CIDFontMapping getCIDFont(String baseFont, PDFontDescriptor fontDescriptor,
                                         PDCIDSystemInfo cidSystemInfo) {
            return new CIDFontMapping(null, fonte, false);
        }
    }
}
