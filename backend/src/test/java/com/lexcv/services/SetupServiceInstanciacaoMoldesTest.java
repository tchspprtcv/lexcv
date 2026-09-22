package com.lexcv.services;

import com.lexcv.dtos.SetupInitializeRequest;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 125 Plan 02 (MOLD-01/MOLD-03): prova comportamental de que {@code
 * SetupService.provisionTenant} instancia uma cópia própria de cada molde actual dentro da sua
 * transacção existente.
 *
 * <p>O caso mais importante deste ficheiro é o {@link
 * #instanciarMoldes_permissoesSaoSnapshot_naoReferenciaViva()} (Caso 2): prova que o {@code Set}
 * de permissões de cada {@code TenantRole} instanciado é uma cópia distinta do {@code Set} do
 * molde de origem, nunca a mesma instância -- é esta independência que torna verdadeira a
 * garantia de MOLD-03 ("editar o molde depois da instanciação não muda a cópia").
 *
 * <p>Segue a mesma convenção Mockito de {@code SetupServiceProvisionTenantTest}: sem harness
 * {@code @SpringBootTest}, colaboradores mockados, serviço instanciado directamente pelo
 * construtor gerado por {@code @RequiredArgsConstructor} (6 colaboradores, {@code
 * TenantRoleRepository} por último).
 *
 * <p>Nota de âmbito: a tenant reservada {@code ALCv} não é coberta por este ficheiro porque
 * <b>não passa por {@code provisionTenant}</b> -- nasce em {@code
 * DatabaseSeeder.seedTenantPlataforma()}, que não conhece {@code TenantRoleRepository}
 * (verificado pelo gate de grep do Plan 01). É por construção, não por guarda, que ela nunca
 * recebe moldes instanciados.
 */
@ExtendWith(MockitoExtension.class)
class SetupServiceInstanciacaoMoldesTest {

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

    private Permission permissao(String nome) {
        return Permission.builder().id(nome.hashCode()).nome(nome).build();
    }

    // Caso 1 -- instancia um TenantRole por molde, com tenantId/moldeId/sistema/permissoes correctos.
    @Test
    void instanciarMoldes_umTenantRolePorMolde_comCamposCorrectos() {
        Role adminRole = Role.builder().id(1).nome("ADMIN").permissions(Set.of(permissao("clientes:manage"))).build();
        Role advogadoMolde = Role.builder().id(2).nome("ADVOGADO")
                .permissions(new HashSet<>(Set.of(permissao("processos:edit"), permissao("clientes:view"))))
                .build();
        Role tecnicoMolde = Role.builder().id(3).nome("TECNICO")
                .permissions(new HashSet<>(Set.of(permissao("agenda:view"))))
                .build();
        Role assistenteMolde = Role.builder().id(4).nome("ASSISTENTE")
                .permissions(new HashSet<>(Set.of(permissao("documentos:view"))))
                .build();

        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllByInstanciavelTrue())
                .thenReturn(List.of(adminRole, advogadoMolde, tecnicoMolde, assistenteMolde));
        when(userRepository.findByEmail("novo@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();

        SetupInitializeRequest request = requestValido("Escritorio Novo", "Novo@Escritorio.CV", "Pa$$w0rd");
        Tenant resultado = setupService.provisionTenant(request);

        ArgumentCaptor<TenantRole> captor = ArgumentCaptor.forClass(TenantRole.class);
        verify(tenantRoleRepository, times(4)).save(captor.capture());
        List<TenantRole> instanciados = captor.getAllValues();

        assertEquals(4, instanciados.size());
        assertTrue(instanciados.stream().map(TenantRole::getNome)
                .allMatch(nome -> Set.of("ADMIN", "ADVOGADO", "TECNICO", "ASSISTENTE").contains(nome)));

        for (TenantRole tenantRole : instanciados) {
            assertEquals(resultado.getId(), tenantRole.getTenantId());
            assertEquals(Boolean.TRUE, tenantRole.getSistema());
            assertNotNull(tenantRole.getMoldeId());

            Role moldeCorrespondente = List.of(adminRole, advogadoMolde, tecnicoMolde, assistenteMolde).stream()
                    .filter(m -> m.getNome().equals(tenantRole.getNome()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(moldeCorrespondente.getId(), tenantRole.getMoldeId());
            assertEquals(moldeCorrespondente.getPermissions(), tenantRole.getPermissions());
        }
    }

    // Caso 2 -- O MAIS IMPORTANTE: snapshot, nao referencia viva (MOLD-03).
    @Test
    void instanciarMoldes_permissoesSaoSnapshot_naoReferenciaViva() {
        Permission permissaoOriginal = permissao("processos:edit");
        Set<Permission> permissoesDoMolde = new HashSet<>(Set.of(permissaoOriginal));
        Role advogadoMolde = Role.builder().id(2).nome("ADVOGADO").permissions(permissoesDoMolde).build();
        Role adminRole = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();

        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of(advogadoMolde));
        when(userRepository.findByEmail("snapshot@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();

        SetupInitializeRequest request = requestValido("Escritorio Snapshot", "snapshot@escritorio.cv", "Pa$$w0rd");
        setupService.provisionTenant(request);

        ArgumentCaptor<TenantRole> captor = ArgumentCaptor.forClass(TenantRole.class);
        verify(tenantRoleRepository).save(captor.capture());
        TenantRole tenantRoleInstanciado = captor.getValue();

        // (a) instancias de Set diferentes -- um Set partilhado passaria esta asserção por
        // acidente em nenhum caso (assertNotSame falha se for a mesma instância).
        assertNotSame(advogadoMolde.getPermissions(), tenantRoleInstanciado.getPermissions());

        int tamanhoAntesDaMutacao = tenantRoleInstanciado.getPermissions().size();

        // Mutar o Set do molde DEPOIS do provisionamento -- acrescentar uma permissão nova e
        // remover a existente.
        Permission permissaoNova = permissao("processos:manage");
        advogadoMolde.getPermissions().add(permissaoNova);
        advogadoMolde.getPermissions().remove(permissaoOriginal);

        // (b) o Set do TenantRole capturado não mudou -- mesmo tamanho, mesmo conteúdo. Um Set
        // partilhado falharia sempre esta asserção.
        assertEquals(tamanhoAntesDaMutacao, tenantRoleInstanciado.getPermissions().size());
        assertTrue(tenantRoleInstanciado.getPermissions().contains(permissaoOriginal));
        assertTrue(!tenantRoleInstanciado.getPermissions().contains(permissaoNova));
    }

    // Caso 3 -- sem moldes, sem papeis instanciados; o tenant continua a nascer normalmente.
    @Test
    void instanciarMoldes_semMoldes_naoInteragComTenantRoleRepositoryEDevolveATenant() {
        Role adminRole = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of());
        when(userRepository.findByEmail("semmolde@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();

        SetupInitializeRequest request = requestValido("Escritorio Sem Molde", "semmolde@escritorio.cv", "Pa$$w0rd");
        Tenant resultado = setupService.provisionTenant(request);

        assertNotNull(resultado);
        assertNotNull(resultado.getId());
        verifyNoInteractions(tenantRoleRepository);
    }

    // Caso 4 -- PLATAFORMA_ADMIN nunca e instanciado (duas camadas de defesa).
    @Test
    void instanciarMoldes_plataformaAdminNuncaEInstanciado() {
        Role adminRole = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        // Cenario de regressao do filtro SQL: o mock simula findAllByInstanciavelTrue()
        // devolvendo indevidamente PLATAFORMA_ADMIN (nunca deveria acontecer em producao, dado
        // o default fechado de Role.instanciavel, mas a defesa em profundidade do servico tem
        // de aguentar mesmo esta regressao).
        Role plataformaAdminMolde = Role.builder().id(99).nome("PLATAFORMA_ADMIN")
                .permissions(new HashSet<>(Set.of(permissao("plataforma:manage"))))
                .build();
        Role advogadoMolde = Role.builder().id(2).nome("ADVOGADO").permissions(new HashSet<>()).build();

        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of(plataformaAdminMolde, advogadoMolde));
        when(userRepository.findByEmail("guarda@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();

        SetupInitializeRequest request = requestValido("Escritorio Guarda", "guarda@escritorio.cv", "Pa$$w0rd");
        setupService.provisionTenant(request);

        // (a) o serviço nunca chama findAll() -- a exclusão fica ao nível de SQL.
        verify(roleRepository, never()).findAll();

        // (b) mesmo com PLATAFORMA_ADMIN na lista devolvida, nenhum TenantRole capturado tem
        // esse nome -- guarda explícita de defesa em profundidade em SetupService.instanciarMoldes.
        ArgumentCaptor<TenantRole> captor = ArgumentCaptor.forClass(TenantRole.class);
        verify(tenantRoleRepository, times(1)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().noneMatch(tr -> "PLATAFORMA_ADMIN".equals(tr.getNome())));
    }

    // Caso 5 -- o administrador inicial recebe o papel GLOBAL ADMIN, nunca um TenantRole nem uma copia.
    @Test
    void provisionTenant_adminUserRecebeAMesmaInstanciaDoPapelGlobalAdmin() {
        Role adminRoleGlobal = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        Role advogadoMolde = Role.builder().id(2).nome("ADVOGADO").permissions(new HashSet<>()).build();

        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRoleGlobal));
        when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of(advogadoMolde));
        when(userRepository.findByEmail("global@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();

        SetupInitializeRequest request = requestValido("Escritorio Global", "global@escritorio.cv", "Pa$$w0rd");
        setupService.provisionTenant(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User adminUser = userCaptor.getValue();

        assertEquals(1, adminUser.getRoles().size());
        Role papelAtribuido = adminUser.getRoles().iterator().next();
        // A REDE DE SEGURANÇA da fronteira bloqueada da fase: mesma instância, não uma cópia,
        // não um TenantRole. Se um refactor futuro trocar o papel global pelo instanciado,
        // reprova aqui.
        assertSame(adminRoleGlobal, papelAtribuido);
    }

    // Caso 6 -- falha na instanciacao propaga e nao deixa o tenant meio-feito.
    @Test
    void instanciarMoldes_quandoSaveFalha_excepcaoPropagaSemSerSilenciada() {
        Role adminRole = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        Role advogadoMolde = Role.builder().id(2).nome("ADVOGADO").permissions(new HashSet<>()).build();

        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of(advogadoMolde));
        when(userRepository.findByEmail("falha@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();
        when(tenantRoleRepository.save(any(TenantRole.class)))
                .thenThrow(new RuntimeException("falha simulada de persistencia"));

        SetupInitializeRequest request = requestValido("Escritorio Falha", "falha@escritorio.cv", "Pa$$w0rd");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> setupService.provisionTenant(request));
        assertEquals("falha simulada de persistencia", ex.getMessage());
    }
}
