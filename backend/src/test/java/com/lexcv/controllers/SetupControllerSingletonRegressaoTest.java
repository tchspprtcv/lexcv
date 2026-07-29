package com.lexcv.controllers;

import com.lexcv.dtos.SetupInitializeRequest;
import com.lexcv.services.SetupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste de regressão do Success Criterion 3 da Phase 119: o endpoint público
 * {@code POST /api/v1/setup/initialize} continua singleton depois de a Phase 119 ter
 * introduzido um segundo caminho de criação de tenants ({@code PlatformAdminController},
 * Plan 04). {@link SetupController} não foi modificado por esta fase -- este ficheiro prova
 * comportamentalmente que o seu gate singleton e o caminho novo de provisionamento de
 * plataforma são irmãos completamente independentes, nunca encadeados: o wizard público nunca
 * chama {@code provisionTenant}, e o caminho de plataforma nunca chama {@code initializeSystem}
 * nem consulta {@code isInitialized()} (ver {@code PlatformAdminControllerTest}, Caso 4).
 *
 * <p>Segue a mesma convenção de todos os testes de controller deste codebase: não existe
 * harness MockMvc/{@code @SpringBootTest} neste projeto -- {@link SetupController} é
 * instanciado diretamente com {@link SetupService} mockado via Mockito, e {@code initialize} é
 * invocado como uma chamada Java simples.
 */
@ExtendWith(MockitoExtension.class)
class SetupControllerSingletonRegressaoTest {

    @Mock
    private SetupService setupService;

    private SetupController novoController() {
        return new SetupController(setupService);
    }

    private SetupInitializeRequest pedidoValido() {
        SetupInitializeRequest request = new SetupInitializeRequest();
        request.setClientName("Escritorio Novo");
        request.setAdminEmail("admin@escritorionovo.cv");
        request.setAdminPassword("Pa$$w0rd1");
        return request;
    }

    @Test
    void initialize_sistemaJaInicializado_devolve403ENuncaChamaInitializeSystem() {
        when(setupService.isInitialized()).thenReturn(true);

        ResponseEntity<?> response = novoController().initialize(pedidoValido());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Map.of("message", "O sistema já foi inicializado."), response.getBody());
        verify(setupService, never()).initializeSystem(any());
    }

    @Test
    void initialize_primeiraInicializacao_devolve201EChamaInitializeSystemUmaVez() {
        when(setupService.isInitialized()).thenReturn(false);

        ResponseEntity<?> response = novoController().initialize(pedidoValido());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(setupService, times(1)).initializeSystem(any());
    }

    /**
     * Prova que nenhum dos dois ramos de {@code initialize} alguma vez encadeia para o
     * provisionamento de plataforma -- os dois caminhos são irmãos independentes. As duas
     * chamadas consecutivas reutilizam o mesmo mock com retorno consecutivo
     * ({@code thenReturn(true, false)}, o mesmo idioma já usado em
     * {@code AdminControllerLimiteUtilizadoresTest}), cobrindo ambos os cenários (sistema já
     * inicializado e primeira inicialização) numa única verificação final.
     */
    @Test
    void initialize_emAmbosOsCenarios_nuncaChamaProvisionTenant() {
        when(setupService.isInitialized()).thenReturn(true, false);
        SetupController controller = novoController();

        controller.initialize(pedidoValido());
        controller.initialize(pedidoValido());

        verify(setupService, never()).provisionTenant(any());
    }
}
