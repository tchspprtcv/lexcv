package com.lexcv.fiscal.efatura;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Valida um DFE contra o esquema oficial eFatura 2024-05-27 vendorizado (DFE-01).
 *
 * <p>O {@link Schema} é construído uma vez, no arranque, a partir do classpath e através da lista
 * branca de {@link ClasspathXsdResolver}; se o pacote estiver partido o arranque falha
 * (fail-fast, T-136-20). O {@code Schema} é thread-safe; cada chamada cria o seu {@link Validator}.
 *
 * <p>Endurecimento (T-136-16): {@code FEATURE_SECURE_PROCESSING}, {@code ACCESS_EXTERNAL_DTD} e
 * {@code ACCESS_EXTERNAL_SCHEMA} vazios na fábrica e no validador, e um parser SAX que recusa
 * qualquer DOCTYPE. O resultado só traz código + linha/coluna (T-136-19).
 */
@Component
public final class DfeValidador {

    private static final Logger log = LoggerFactory.getLogger(DfeValidador.class);

    static final String ESQUEMA_ENTRADA = "EnvelopedSignature.xsd";
    private static final String RAIZ_RECURSOS = "xsd/efatura/";
    private static final String FEATURE_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String FEATURE_ENTIDADES_GERAIS = "http://xml.org/sax/features/external-general-entities";
    private static final String FEATURE_ENTIDADES_PARAMETRO = "http://xml.org/sax/features/external-parameter-entities";
    private static final String MENSAGEM_ARRANQUE = "Esquema eFatura indisponível ou inválido no classpath";

    private final Schema schema;
    private final SAXParserFactory parserFactory;

    public DfeValidador() {
        this(RAIZ_RECURSOS + ESQUEMA_ENTRADA);
    }

    /** Construtor de teste: recurso de entrada alternativo (tem de ser um XSD da lista branca). */
    DfeValidador(String recursoEntrada) {
        this.schema = construirSchema(recursoEntrada);
        this.parserFactory = construirParserFactory();
    }

    private static Schema construirSchema(String recursoEntrada) {
        if (recursoEntrada == null || !recursoEntrada.startsWith(RAIZ_RECURSOS)) {
            throw new IllegalStateException(MENSAGEM_ARRANQUE);
        }
        String relativo = recursoEntrada.substring(RAIZ_RECURSOS.length());
        byte[] bytes = ClasspathXsdResolver.lerRecurso(relativo);
        if (bytes == null) {
            throw new IllegalStateException(MENSAGEM_ARRANQUE);
        }
        try {
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            sf.setResourceResolver(new ClasspathXsdResolver());
            return sf.newSchema(new StreamSource(new ByteArrayInputStream(bytes),
                    ClasspathXsdResolver.PREFIXO + relativo));
        } catch (SAXException e) {
            throw new IllegalStateException(MENSAGEM_ARRANQUE, e);
        }
    }

    private static SAXParserFactory construirParserFactory() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            spf.setValidating(false);
            spf.setXIncludeAware(false);
            spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            spf.setFeature(FEATURE_DOCTYPE, true);
            spf.setFeature(FEATURE_ENTIDADES_GERAIS, false);
            spf.setFeature(FEATURE_ENTIDADES_PARAMETRO, false);
            return spf;
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException(MENSAGEM_ARRANQUE, e);
        }
    }

    /**
     * Valida os bytes de um DFE. Nunca lança por causa do conteúdo: qualquer problema vira um
     * {@link ResultadoValidacao} inválido com código fixo.
     */
    public ResultadoValidacao validar(byte[] xml) {
        if (xml == null || xml.length == 0) {
            return ResultadoValidacao.invalido(ResultadoValidacao.XML_ILEGIVEL, 0, 0);
        }
        try {
            XMLReader reader = parserFactory.newSAXParser().getXMLReader();
            Validator validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new SAXSource(reader, new InputSource(new ByteArrayInputStream(xml))));
            return ResultadoValidacao.sucesso();
        } catch (SAXParseException e) {
            log.debug("DFE inválido na linha {} coluna {}: {}", e.getLineNumber(), e.getColumnNumber(), e.getMessage());
            String codigo = mencionaDoctype(e)
                    ? ResultadoValidacao.XML_PROIBIDO
                    : ResultadoValidacao.XSD_INVALIDO;
            return ResultadoValidacao.invalido(codigo, Math.max(e.getLineNumber(), 0), Math.max(e.getColumnNumber(), 0));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            log.debug("DFE ilegível: {}", e.getClass().getSimpleName());
            return ResultadoValidacao.invalido(ResultadoValidacao.XML_ILEGIVEL, 0, 0);
        }
    }

    /**
     * O parser que recusa DOCTYPE (feature acima) rejeita o DOCTYPE com um erro fatal; o texto desse
     * erro (chave Xerces {@code DoctypeNotAllowed}) cita a palavra-chave DOCTYPE. O parser recusa
     * antes de reportar o DOCTYPE a qualquer handler, por isso não há sinal estruturado para a
     * distinguir de um erro de esquema; só o código muda -- a recusa acontece de qualquer forma.
     */
    private static boolean mencionaDoctype(SAXParseException e) {
        String mensagem = e.getMessage();
        return mensagem != null && mensagem.contains("DOCTYPE");
    }
}
