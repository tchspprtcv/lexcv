package com.lexcv.services;

import com.lexcv.dtos.SetupInitializeRequest;
import com.lexcv.models.Role;
import com.lexcv.models.SystemSetting;
import com.lexcv.models.Tenant;
import com.lexcv.models.TenantPlano;
import com.lexcv.models.User;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.SystemSettingRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 119 (PROV-06): prova {@code SetupService.provisionTenant} -- o caminho de
 * provisionamento que o futuro {@code PlatformAdminController} (Plan 04) invoca, distinto do
 * wizard público {@code /setup/initialize} e do seu gate singleton {@code SystemSetting}.
 *
 * <p>O Caso 2 ({@link #provisionTenant_nuncaInteragComSystemSettingRepository}) é o teste mais
 * importante deste ficheiro: prova que o novo caminho de provisionamento não lê nem escreve o
 * gate singleton, ao contrário de {@code initializeSystem}. Se um refactor futuro reintroduzir
 * qualquer leitura/escrita de {@code SystemSetting} dentro de {@code provisionTenant}, este
 * teste reprova.
 *
 * <p>Segue a mesma convenção de todos os testes de serviço deste codebase (ver
 * {@code NotificacaoServiceTest}): sem harness {@code @SpringBootTest}, colaboradores mockados
 * via Mockito, o serviço instanciado diretamente pelo construtor gerado por
 * {@code @RequiredArgsConstructor} (ordem exata dos 5 colaboradores documentada em
 * {@code SetupService}).
 */
@ExtendWith(MockitoExtension.class)
class SetupServiceProvisionTenantTest {

    @Mock private SystemSettingRepository systemSettingRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private SetupService setupService;

    @BeforeEach
    void setUp() {
        setupService = new SetupService(
                systemSettingRepository, tenantRepository, userRepository, roleRepository, passwordEncoder);
    }

    private SetupInitializeRequest requestValido(String clientName, String adminEmail, String adminPassword) {
        SetupInitializeRequest request = new SetupInitializeRequest();
        request.setClientName(clientName);
        request.setAdminEmail(adminEmail);
        request.setAdminPassword(adminPassword);
        request.setLogo(null);
        return request;
    }

    private void stubTenantSaveComIdGerado() {
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
    }

    // Caso 1 -- cria tenant + utilizador ADMIN e devolve a tenant guardada.
    @Test
    void provisionTenant_comRequestValido_criaTenantEUtilizadorAdminEDevolveATenantGuardada() {
        SetupInitializeRequest request = requestValido("Escritorio Novo", "Novo@Escritorio.CV", "Pa$$w0rd");

        Role adminRole = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.findByEmail("novo@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();

        Tenant resultado = setupService.provisionTenant(request);

        assertNotNull(resultado);
        assertNotNull(resultado.getId());

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        Tenant tenantPersistida = tenantCaptor.getValue();
        assertEquals("Escritorio Novo", tenantPersistida.getNome());
        assertEquals("novo@escritorio.cv", tenantPersistida.getEmail());

        assertEquals(tenantPersistida.getId(), resultado.getId());
        assertEquals(tenantPersistida.getNome(), resultado.getNome());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User userPersistido = userCaptor.getValue();
        assertEquals(tenantPersistida.getId(), userPersistido.getTenantId());
        assertEquals("novo@escritorio.cv", userPersistido.getEmail());
        assertEquals(Boolean.TRUE, userPersistido.getAtivo());
        assertEquals(1, userPersistido.getRoles().size());
        assertTrue(userPersistido.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getNome())));
    }

    // Caso 2 -- zero interação com o SystemSettingRepository (coração do Success Criterion 2/3).
    @Test
    void provisionTenant_nuncaInteragComSystemSettingRepository() {
        SetupInitializeRequest request = requestValido("Escritorio Sem Gate", "semgate@escritorio.cv", "Pa$$w0rd");

        Role adminRole = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.findByEmail("semgate@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();

        setupService.provisionTenant(request);

        verifyNoInteractions(systemSettingRepository);
    }

    // Caso 3a -- nome em branco, mensagem exata de validateRequest reutilizada.
    @Test
    void provisionTenant_comNomeEmBranco_lancaIllegalArgumentExceptionSemPersistirTenant() {
        SetupInitializeRequest request = requestValido("   ", "valido@escritorio.cv", "Pa$$w0rd");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> setupService.provisionTenant(request));

        assertEquals("O nome da empresa/cliente é obrigatório.", ex.getMessage());
        verify(tenantRepository, never()).save(any());
    }

    // Caso 3b -- email malformado, mensagem exata de validateRequest reutilizada.
    @Test
    void provisionTenant_comEmailInvalido_lancaIllegalArgumentExceptionSemPersistirTenant() {
        SetupInitializeRequest request = requestValido("Escritorio Valido", "nao-e-email", "Pa$$w0rd");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> setupService.provisionTenant(request));

        assertEquals("O email do administrador é inválido.", ex.getMessage());
        verify(tenantRepository, never()).save(any());
    }

    // Caso 3c -- password fraca, mensagem exata de validateRequest reutilizada.
    @Test
    void provisionTenant_comPasswordFraca_lancaIllegalArgumentExceptionSemPersistirTenant() {
        SetupInitializeRequest request = requestValido("Escritorio Valido", "valido@escritorio.cv", "fraca");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> setupService.provisionTenant(request));

        assertEquals(
                "A password deve ter no mínimo 8 caracteres, com maiúscula, minúscula, número e caractere especial.",
                ex.getMessage());
        verify(tenantRepository, never()).save(any());
    }

    // Caso 4 -- email já existente em qualquer tenant recusado antes de criar a tenant.
    @Test
    void provisionTenant_comEmailJaExistente_lancaIllegalArgumentExceptionAntesDeCriarATenant() {
        SetupInitializeRequest request = requestValido("Escritorio Duplicado", "existente@escritorio.cv", "Pa$$w0rd");
        User utilizadorExistente = User.builder().id(UUID.randomUUID()).email("existente@escritorio.cv").build();
        when(userRepository.findByEmail("existente@escritorio.cv")).thenReturn(Optional.of(utilizadorExistente));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> setupService.provisionTenant(request));

        assertEquals("Já existe um utilizador com este email.", ex.getMessage());
        verify(tenantRepository, never()).save(any());
    }

    // Caso 5 -- papel ADMIN ausente é erro de estado, não de input; mapeado a 403 pelo Plan 04.
    @Test
    void provisionTenant_comPapelAdminAusente_lancaIllegalStateExceptionSemPersistirTenant() {
        SetupInitializeRequest request = requestValido("Escritorio Sem Papel", "semrole@escritorio.cv", "Pa$$w0rd");
        when(userRepository.findByEmail("semrole@escritorio.cv")).thenReturn(Optional.empty());
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> setupService.provisionTenant(request));

        assertEquals("O papel ADMIN não está configurado.", ex.getMessage());
        verify(tenantRepository, never()).save(any());
    }

    // Caso 6 -- repetibilidade: duas chamadas seguidas criam duas tenants (diferença essencial
    // face a initializeSystem, que só funciona uma vez).
    @Test
    void provisionTenant_chamadoDuasVezes_criaDuasTenantsDistintas() {
        Role adminRole = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRole));
        stubTenantSaveComIdGerado();

        SetupInitializeRequest request1 = requestValido("Escritorio Um", "um@escritorio.cv", "Pa$$w0rd");
        when(userRepository.findByEmail("um@escritorio.cv")).thenReturn(Optional.empty());
        setupService.provisionTenant(request1);

        SetupInitializeRequest request2 = requestValido("Escritorio Dois", "dois@escritorio.cv", "Pa$$w0rd");
        when(userRepository.findByEmail("dois@escritorio.cv")).thenReturn(Optional.empty());
        setupService.provisionTenant(request2);

        verify(tenantRepository, times(2)).save(any());
        verify(userRepository, times(2)).save(any());
        verifyNoInteractions(systemSettingRepository);
    }

    // Caso 7 -- não-regressão: initializeSystem mantém o seu gate singleton intacto.
    @Test
    void initializeSystem_quandoJaInicializado_lancaIllegalStateExceptionMantendoOGateSingleton() {
        SystemSetting settings = SystemSetting.builder()
                .id(SystemSetting.SINGLETON_ID)
                .initialized(true)
                .build();
        when(systemSettingRepository.findByIdForUpdate(SystemSetting.SINGLETON_ID)).thenReturn(Optional.of(settings));

        SetupInitializeRequest request = requestValido("Qualquer Nome", "qualquer@escritorio.cv", "Pa$$w0rd");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> setupService.initializeSystem(request));

        assertEquals("O sistema já foi inicializado.", ex.getMessage());
    }

    // Caso 8 (CR-01, 120-REVIEW.md) -- provisionTenant nunca chama .plano(...) explicitamente;
    // prova que o @Builder.Default de Tenant.plano garante STARTER, nunca null, sem depender de
    // nenhuma logica extra dentro do proprio SetupService.
    @Test
    void provisionTenant_naoDefinePlanoExplicitamente_tenantPersistidaTemPlanoStarterNuncaNull() {
        SetupInitializeRequest request =
                requestValido("Escritorio Sem Plano Explicito", "semplano@escritorio.cv", "Pa$$w0rd");
        Role adminRole = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.findByEmail("semplano@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();

        Tenant resultado = setupService.provisionTenant(request);

        assertEquals(TenantPlano.STARTER, resultado.getPlano());
        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertEquals(TenantPlano.STARTER, tenantCaptor.getValue().getPlano());
    }

    // Caso 9 (CR-01, 120-REVIEW.md) -- initializeSystem (o wizard publico de setup, distinto de
    // provisionTenant) tambem nunca chama .plano(...) explicitamente; mesma prova que o Caso 8,
    // desta vez para o outro dos dois caminhos de criacao de tenant apontados pelo finding.
    @Test
    void initializeSystem_naoDefinePlanoExplicitamente_tenantPersistidaTemPlanoStarterNuncaNull() {
        SystemSetting settings = SystemSetting.builder()
                .id(SystemSetting.SINGLETON_ID)
                .initialized(false)
                .build();
        when(systemSettingRepository.findByIdForUpdate(SystemSetting.SINGLETON_ID))
                .thenReturn(Optional.of(settings));
        SetupInitializeRequest request =
                requestValido("Escritorio Setup Inicial", "setupinicial@escritorio.cv", "Pa$$w0rd");
        Role adminRole = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.findByEmail("setupinicial@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();

        setupService.initializeSystem(request);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertEquals(TenantPlano.STARTER, tenantCaptor.getValue().getPlano());
    }
}
