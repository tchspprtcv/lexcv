package com.lexcv.services;

import com.lexcv.dtos.SetupInitializeRequest;
import com.lexcv.models.Role;
import com.lexcv.models.SystemSetting;
import com.lexcv.models.Tenant;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.SystemSettingRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.TenantRoleRepository;
import com.lexcv.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * WR-01/IN-01 (126-REVIEW.md): prova comportamental de que o administrador fundador de um
 * escritorio -- criado tanto por {@link SetupService#provisionTenant} como pelo wizard publico
 * {@link SetupService#initializeSystem} -- recebe de IMEDIATO o {@link TenantRole} ADMIN
 * instanciado, em vez de ficar com {@code tenantRoles} vazio ate ao proximo arranque que corresse
 * {@code MigracaoPapeisEscritorioService.migrar()}.
 *
 * <p>O Caso 1 e o que PROVA o achado: contra o codigo NAO corrigido, `adminUser.getTenantRoles()`
 * ficava sempre vazio -- nem {@code provisionTenant} nem {@code initializeSystem} tocavam em
 * {@code tenantRoleRepository} para o utilizador fundador. Este teste falha contra esse codigo
 * (ver SUMMARY para o output real capturado).
 *
 * <p>O Caso 3 fecha o IN-01: antes desta correcao, so {@code provisionTenant} chamava
 * {@code instanciarMoldes} -- {@code initializeSystem} deixava o tenant sem NENHUM TenantRole ate
 * ao proximo arranque. Este teste prova que os dois caminhos convergem.
 */
@ExtendWith(MockitoExtension.class)
class SetupServiceAtribuicaoAdminFundadorTest {

    @Mock private SystemSettingRepository systemSettingRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private AuditoriaRbacService auditoriaRbacService;

    private SetupService setupService;

    @BeforeEach
    void setUp() {
        setupService = new SetupService(
                systemSettingRepository, tenantRepository, userRepository, roleRepository, passwordEncoder,
                tenantRoleRepository, auditoriaRbacService);
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

    // Caso 1 (O TESTE QUE PROVA O ACHADO) -- provisionTenant com ADMIN instanciavel: o utilizador
    // fundador tem de acabar com o TenantRole ADMIN em tenantRoles, e o papel GLOBAL ADMIN
    // continua presente em roles (aditivo, nunca uma troca -- Decisao 4, reversibilidade).
    @Test
    void provisionTenant_comAdminInstanciavel_atribuiTenantRoleAdminAoFundadorDeImediato() {
        Role adminRoleGlobal = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRoleGlobal));
        when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of(adminRoleGlobal));
        when(userRepository.findByEmail("fundador@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        SetupInitializeRequest request = requestValido("Escritorio Fundador", "fundador@escritorio.cv", "Pa$$w0rd");
        setupService.provisionTenant(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(userCaptor.capture());
        User ultimoEstadoGravado = userCaptor.getAllValues().get(userCaptor.getAllValues().size() - 1);

        assertEquals(1, ultimoEstadoGravado.getTenantRoles().size());
        TenantRole tenantRoleAdmin = ultimoEstadoGravado.getTenantRoles().iterator().next();
        assertEquals("ADMIN", tenantRoleAdmin.getNome());
        assertEquals(Boolean.TRUE, tenantRoleAdmin.getSistema());

        // Reversibilidade (Decisao 4): o papel GLOBAL continua presente, nunca trocado.
        assertTrue(ultimoEstadoGravado.getRoles().contains(adminRoleGlobal));
    }

    // Caso 2 -- sem ADMIN instanciavel (catalogo vazio ou ADMIN deixou de ser molde): o fundador
    // fica sem TenantRole, exactamente como hoje -- sem excepcao, falha para o lado seguro.
    @Test
    void provisionTenant_semAdminInstanciavel_fundadorFicaSemTenantRoleSemExcecao() {
        Role adminRoleGlobal = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRoleGlobal));
        when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of());
        when(userRepository.findByEmail("semmolde@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        SetupInitializeRequest request = requestValido("Escritorio Sem Molde Admin", "semmolde@escritorio.cv", "Pa$$w0rd");
        setupService.provisionTenant(request);

        // Sem moldes a instanciar, o fundador so e gravado UMA vez -- nunca uma segunda escrita
        // vazia -- e nenhuma leitura extra a tenantRoleRepository (ver instanciarMoldes Caso 3 em
        // SetupServiceInstanciacaoMoldesTest, precondicao que este metodo preserva).
        verify(userRepository, times(1)).save(any(User.class));
    }

    // Caso 3 (fecha IN-01) -- initializeSystem (o wizard publico) tambem instancia moldes e
    // atribui o TenantRole ADMIN ao fundador de imediato -- a mesma prova do Caso 1, desta vez
    // para o caminho que ANTES da correcao nao chamava instanciarMoldes de todo.
    @Test
    void initializeSystem_comAdminInstanciavel_atribuiTenantRoleAdminAoFundadorDeImediato() {
        SystemSetting settings = SystemSetting.builder()
                .id(SystemSetting.SINGLETON_ID)
                .initialized(false)
                .build();
        when(systemSettingRepository.findByIdForUpdate(SystemSetting.SINGLETON_ID))
                .thenReturn(Optional.of(settings));

        Role adminRoleGlobal = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRoleGlobal));
        when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of(adminRoleGlobal));
        when(userRepository.findByEmail("wizard@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        SetupInitializeRequest request = requestValido("Escritorio Wizard", "wizard@escritorio.cv", "Pa$$w0rd");
        setupService.initializeSystem(request);

        // IN-01: initializeSystem agora tambem instancia o catalogo de moldes -- prova por
        // interacao com tenantRoleRepository.save (o metodo que instanciarMoldes chama).
        verify(tenantRoleRepository, times(1)).save(any(TenantRole.class));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(userCaptor.capture());
        User ultimoEstadoGravado = userCaptor.getAllValues().get(userCaptor.getAllValues().size() - 1);
        assertEquals(1, ultimoEstadoGravado.getTenantRoles().size());
        assertEquals("ADMIN", ultimoEstadoGravado.getTenantRoles().iterator().next().getNome());

        // Phase 128 Plan 06 (Decisao 4): initializeSystem e o wizard publico de primeiro
        // arranque, sem principal autenticado -- nunca toca em auditoriaRbacService, ao
        // contrario de provisionTenant.
        verifyNoInteractions(auditoriaRbacService);
    }

    // Caso 4 -- o administrador de plataforma nunca passa por provisionTenant/initializeSystem
    // (nasce em DatabaseSeeder.seedUtilizadorPlataforma), mas o discriminador que
    // ResolucaoPapeisService agora usa para o reconhecer (papel global PLATAFORMA_ADMIN) tem de
    // continuar a resolve-lo correctamente mesmo com tenantRoles vazio -- prova complementar de
    // que esta correcao nao o tranca fora. Ver ResolucaoPapeisServiceTest Caso 2 para a prova
    // directa sobre ResolucaoPapeisService; este caso fixa a expectativa do lado de SetupService:
    // nenhum caminho de provisionamento de escritorio jamais atribui PLATAFORMA_ADMIN.
    @Test
    void provisionTenant_nuncaAtribuiPapelDePlataformaAoFundador() {
        Role adminRoleGlobal = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRoleGlobal));
        when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of(adminRoleGlobal));
        when(userRepository.findByEmail("naoplataforma@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        SetupInitializeRequest request = requestValido("Escritorio Nao Plataforma", "naoplataforma@escritorio.cv", "Pa$$w0rd");
        setupService.provisionTenant(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(userCaptor.capture());
        User ultimoEstadoGravado = userCaptor.getAllValues().get(userCaptor.getAllValues().size() - 1);
        assertTrue(ultimoEstadoGravado.getRoles().stream().noneMatch(r -> "PLATAFORMA_ADMIN".equals(r.getNome())));
        assertTrue(ultimoEstadoGravado.getTenantRoles().stream().noneMatch(tr -> "PLATAFORMA_ADMIN".equals(tr.getNome())));
    }
}
