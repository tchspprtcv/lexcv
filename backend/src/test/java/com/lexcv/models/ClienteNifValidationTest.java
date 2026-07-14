package com.lexcv.models;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bean-Validation regression coverage for {@link Cliente#getNif()}'s
 * {@code @NotBlank}/{@code @Pattern} constraints (introduced in Phase 73.1, previously
 * uncovered by automated tests — closed here per v2.11 AUD-03).
 *
 * Plain JUnit 5, standalone {@code jakarta.validation.Validator} (no
 * {@code @SpringBootTest}/MockMvc), matching the codebase convention established by
 * {@code RiscoPrazoServiceTest}/{@code NotificacaoServiceTest} — hibernate-validator is on the
 * test classpath transitively via {@code spring-boot-starter-validation}.
 *
 * Each scenario builds a {@link Cliente} populated only enough to isolate the {@code nif}
 * property, then asserts on the violation subset scoped to {@code nif} specifically (other
 * fields are irrelevant to this rule and are left unset).
 */
class ClienteNifValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        validatorFactory.close();
    }

    private Set<ConstraintViolation<Cliente>> nifViolations(Cliente cliente) {
        return validator.validate(cliente).stream()
                .filter(v -> "nif".equals(v.getPropertyPath().toString()))
                .collect(Collectors.toSet());
    }

    @Test
    void nif_comNoveDigitos_naoProduzViolacoes() {
        Cliente cliente = Cliente.builder().nome("Cliente Teste").nif("123456789").build();

        assertTrue(nifViolations(cliente).isEmpty(),
                "NIF com exatamente 9 dígitos numéricos não deve produzir violações");
    }

    @Test
    void nif_nuloOuEmBranco_produzViolacaoNotBlank() {
        for (String invalido : new String[] { null, "", "   " }) {
            Cliente cliente = Cliente.builder().nome("Cliente Teste").nif(invalido).build();

            Set<ConstraintViolation<Cliente>> violations = nifViolations(cliente);
            assertTrue(
                    violations.stream().anyMatch(v -> "NIF é obrigatório".equals(v.getMessage())),
                    "nif=" + (invalido == null ? "null" : "'" + invalido + "'")
                            + " deve produzir a violação @NotBlank \"NIF é obrigatório\"");
        }
    }

    @Test
    void nif_comTamanhoInvalido_produzViolacaoPattern() {
        for (String invalido : new String[] { "12345", "1234567890" }) {
            Cliente cliente = Cliente.builder().nome("Cliente Teste").nif(invalido).build();

            Set<ConstraintViolation<Cliente>> violations = nifViolations(cliente);
            assertTrue(
                    violations.stream()
                            .anyMatch(v -> "NIF deve conter exatamente 9 dígitos numéricos".equals(v.getMessage())),
                    "nif='" + invalido + "' deve produzir a violação @Pattern \"NIF deve conter exatamente 9 dígitos numéricos\"");
        }
    }

    @Test
    void nif_naoNumerico_produzViolacaoPattern() {
        Cliente cliente = Cliente.builder().nome("Cliente Teste").nif("12345678A").build();

        Set<ConstraintViolation<Cliente>> violations = nifViolations(cliente);
        assertTrue(
                violations.stream()
                        .anyMatch(v -> "NIF deve conter exatamente 9 dígitos numéricos".equals(v.getMessage())),
                "nif='12345678A' deve produzir a violação @Pattern \"NIF deve conter exatamente 9 dígitos numéricos\"");
    }
}
