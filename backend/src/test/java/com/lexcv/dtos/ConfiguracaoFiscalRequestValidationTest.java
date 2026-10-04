package com.lexcv.dtos;

import com.lexcv.models.RegimeIva;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 133 (CFG-01), Plan 04: as mensagens de validação de {@link ConfiguracaoFiscalRequest}
 * são exatamente o texto do UI-SPEC (o frontend mostra-as tal como chegam).
 */
class ConfiguracaoFiscalRequestValidationTest {

    private static final String NIF_INVALIDO = "O NIF deve ter 9 dígitos e começar por um algarismo de 1 a 9.";
    private static final String OBRIGATORIO = "Preencha este campo.";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static ConfiguracaoFiscalRequest valido() {
        return new ConfiguracaoFiscalRequest("512345678", "Escritório Silva & Associados",
                "Rua 5 de Julho, 12", "Praia", "geral@silva.cv", "+238 260 00 00",
                RegimeIva.NORMAL, null);
    }

    private static ConfiguracaoFiscalRequest comNif(String nif) {
        ConfiguracaoFiscalRequest v = valido();
        return new ConfiguracaoFiscalRequest(nif, v.firma(), v.morada(), v.localidade(),
                v.emailContacto(), v.telefoneContacto(), v.regimeIva(), v.motivoIsencaoCodigo());
    }

    private static Set<String> mensagens(ConfiguracaoFiscalRequest req, String campo) {
        return validator.validate(req).stream()
                .filter(v -> v.getPropertyPath().toString().equals(campo))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void pedidoValidoNaoTemViolacoes() {
        assertEquals(Set.of(), validator.validate(valido()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"012345678", "12345678", "1234567890", "12345678a"})
    void nifInvalidoTemMensagemDoUiSpec(String nif) {
        assertEquals(Set.of(NIF_INVALIDO), mensagens(comNif(nif), "nif"));
    }

    @Test
    void nifValidoComPrimeiroDigitoCinco() {
        assertEquals(Set.of(), mensagens(comNif("512345678"), "nif"));
    }

    @Test
    void nifEmBrancoPedePreenchimento() {
        assertTrue(mensagens(comNif(""), "nif").contains(OBRIGATORIO));
        assertTrue(mensagens(comNif(null), "nif").contains(OBRIGATORIO));
    }

    @Test
    void moradaCom101CaracteresTemMensagemDoUiSpec() {
        ConfiguracaoFiscalRequest v = valido();
        ConfiguracaoFiscalRequest req = new ConfiguracaoFiscalRequest(v.nif(), v.firma(), "a".repeat(101),
                v.localidade(), v.emailContacto(), v.telefoneContacto(), v.regimeIva(), null);
        assertEquals(Set.of("A morada não pode ter mais de 100 caracteres."), mensagens(req, "morada"));
    }

    @Test
    void moradaCom100CaracteresEValida() {
        ConfiguracaoFiscalRequest v = valido();
        ConfiguracaoFiscalRequest req = new ConfiguracaoFiscalRequest(v.nif(), v.firma(), "a".repeat(100),
                v.localidade(), v.emailContacto(), v.telefoneContacto(), v.regimeIva(), null);
        assertEquals(Set.of(), mensagens(req, "morada"));
    }

    @Test
    void emailInvalidoTemMensagemDoUiSpec() {
        ConfiguracaoFiscalRequest v = valido();
        ConfiguracaoFiscalRequest req = new ConfiguracaoFiscalRequest(v.nif(), v.firma(), v.morada(),
                v.localidade(), "x@", v.telefoneContacto(), v.regimeIva(), null);
        assertEquals(Set.of("Introduza um email válido."), mensagens(req, "emailContacto"));
    }

    @Test
    void regimeNuloPedePreenchimento() {
        ConfiguracaoFiscalRequest v = valido();
        ConfiguracaoFiscalRequest req = new ConfiguracaoFiscalRequest(v.nif(), v.firma(), v.morada(),
                v.localidade(), v.emailContacto(), v.telefoneContacto(), null, null);
        assertEquals(Set.of(OBRIGATORIO), mensagens(req, "regimeIva"));
    }

    @Test
    void camposDeTextoEmBrancoPedemPreenchimento() {
        ConfiguracaoFiscalRequest req = new ConfiguracaoFiscalRequest("512345678", " ", " ", " ", " ", " ",
                RegimeIva.NORMAL, null);
        for (String campo : new String[]{"firma", "morada", "localidade", "emailContacto", "telefoneContacto"}) {
            assertTrue(mensagens(req, campo).contains(OBRIGATORIO), campo);
        }
    }
}
