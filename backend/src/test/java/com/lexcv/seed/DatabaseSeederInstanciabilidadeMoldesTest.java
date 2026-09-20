package com.lexcv.seed;

import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.repositories.ClienteRepository;
import com.lexcv.repositories.ContaCorrenteRepository;
import com.lexcv.repositories.EventoRepository;
import com.lexcv.repositories.FaseProcessualRepository;
import com.lexcv.repositories.HonorarioRepository;
import com.lexcv.repositories.PagamentoRepository;
import com.lexcv.repositories.ParteRepository;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.ProcessoFaseRepository;
import com.lexcv.repositories.ProcessoRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.SystemSettingRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 125 (MOLD-01): prova que {@code DatabaseSeeder.seedRbac()} converge a marca de
 * instanciabilidade dos 5 papeis globais em cada arranque -- os 4 papeis de escritorio
 * (ADMIN, ASSISTENTE, TECNICO, ADVOGADO) nascem/permanecem {@code instanciavel = true} e
 * {@code PLATAFORMA_ADMIN} nasce/permanece {@code instanciavel = false}, mesmo numa base de
 * dados que ja tem esse papel marcado incorrectamente (deriva) -- sem nunca apagar nenhuma
 * linha de papel.
 *
 * <p>Segue o andaime de {@link DatabaseSeederCatalogoPermissoesTest}: sem harness
 * {@code @SpringBootTest}, {@code DatabaseSeeder} e instanciado com colaboradores mockados via
 * Mockito ({@code @InjectMocks} resolve o construtor gerado por
 * {@code @RequiredArgsConstructor}). {@code seedEnabled = false} e o cenario mais barato -- e
 * tambem o cenario de producao -- porque {@code run()} executa {@code seedRbac()} e
 * {@code seedTenantPlataforma()} e regressa antes de tocar em qualquer outro repositorio.
 */
@ExtendWith(MockitoExtension.class)
class DatabaseSeederInstanciabilidadeMoldesTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private ContaCorrenteRepository contaCorrenteRepository;
    @Mock private ProcessoRepository processoRepository;
    @Mock private ParteRepository parteRepository;
    @Mock private FaseProcessualRepository faseProcessualRepository;
    @Mock private ProcessoFaseRepository processoFaseRepository;
    @Mock private EventoRepository eventoRepository;
    @Mock private HonorarioRepository honorarioRepository;
    @Mock private PagamentoRepository pagamentoRepository;
    @Mock private SystemSettingRepository systemSettingRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DatabaseSeeder seeder;

    private static final List<String> PAPEIS_MOLDE = List.of("ADMIN", "ASSISTENTE", "TECNICO", "ADVOGADO");

    @BeforeEach
    void configurarStubsComuns() {
        lenient().when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(java.util.UUID.randomUUID());
            return tenant;
        });
        lenient().when(permissionRepository.findByNome(anyString()))
                .thenReturn(Optional.of(Permission.builder().nome("x").build()));
        lenient().when(permissionRepository.save(any(Permission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(roleRepository.save(any(Role.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void executarComSeedDisabled() throws Exception {
        ReflectionTestUtils.setField(seeder, "seedEnabled", false);
        seeder.run();
    }

    @Test
    void seedRbac_noPrimeiroArranque_gravaOsCincoPapeisComInstanciabilidadeCorrecta() throws Exception {
        when(roleRepository.findByNome(anyString())).thenReturn(Optional.empty());

        executarComSeedDisabled();

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, org.mockito.Mockito.atLeast(5)).save(captor.capture());

        List<Role> capturados = captor.getAllValues();
        for (String nomePapel : PAPEIS_MOLDE) {
            Role role = capturados.stream().filter(r -> nomePapel.equals(r.getNome())).findFirst()
                    .orElseThrow(() -> new AssertionError(nomePapel + " nao foi gravado"));
            assertEquals(Boolean.TRUE, role.getInstanciavel(), nomePapel + " deveria ser instanciavel");
        }

        Role plataformaAdmin = capturados.stream().filter(r -> "PLATAFORMA_ADMIN".equals(r.getNome())).findFirst()
                .orElseThrow(() -> new AssertionError("PLATAFORMA_ADMIN nao foi gravado"));
        assertEquals(Boolean.FALSE, plataformaAdmin.getInstanciavel());
    }

    @Test
    void seedRbac_comPlataformaAdminEmDeriva_convergeParaNaoInstanciavel() throws Exception {
        Role plataformaAdminDerivado = Role.builder()
                .nome("PLATAFORMA_ADMIN")
                .instanciavel(true)
                .permissions(new HashSet<>())
                .build();

        when(roleRepository.findByNome(anyString())).thenAnswer(invocation -> {
            String nomePedido = invocation.getArgument(0);
            if ("PLATAFORMA_ADMIN".equals(nomePedido)) {
                return Optional.of(plataformaAdminDerivado);
            }
            return Optional.of(Role.builder().nome(nomePedido).instanciavel(true).permissions(new HashSet<>()).build());
        });

        executarComSeedDisabled();

        assertEquals(Boolean.FALSE, plataformaAdminDerivado.getInstanciavel());
        verify(roleRepository).save(plataformaAdminDerivado);
    }

    @Test
    void seedRbac_comColunaRecemCriada_papeisDeEscritorioComInstanciavelNull_convergemParaTrue() throws Exception {
        when(roleRepository.findByNome(anyString())).thenAnswer(invocation -> {
            String nomePedido = invocation.getArgument(0);
            // instanciavel deliberadamente nao fixado (fica null), simulando uma coluna que
            // acabou de ser criada numa base de dados existente sem passar pelo Lombok
            // @Builder.Default (linha ja persistida antes desta fase).
            Role role = new Role();
            role.setNome(nomePedido);
            role.setPermissions(new HashSet<>());
            return Optional.of(role);
        });

        executarComSeedDisabled();

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, org.mockito.Mockito.atLeast(4)).save(captor.capture());
        List<Role> capturados = captor.getAllValues();

        for (String nomePapel : PAPEIS_MOLDE) {
            Role role = capturados.stream().filter(r -> nomePapel.equals(r.getNome())).findFirst()
                    .orElseThrow(() -> new AssertionError(nomePapel + " nao foi gravado"));
            assertEquals(Boolean.TRUE, role.getInstanciavel(), nomePapel + " nao deveria ficar null nem false");
        }
    }

    @Test
    void seedRbac_nuncaInvocaOperacaoDeRemocaoSobreRoleRepository_ePreservaIdENomeExistentes() throws Exception {
        Role adminExistente = Role.builder()
                .id(3)
                .nome("ADMIN")
                .instanciavel(false)
                .permissions(new HashSet<>())
                .build();

        when(roleRepository.findByNome(anyString())).thenAnswer(invocation -> {
            String nomePedido = invocation.getArgument(0);
            if ("ADMIN".equals(nomePedido)) {
                return Optional.of(adminExistente);
            }
            return Optional.of(Role.builder().nome(nomePedido).instanciavel(true).permissions(new HashSet<>()).build());
        });

        executarComSeedDisabled();

        verify(roleRepository, never()).delete(any());
        verify(roleRepository, never()).deleteAll();
        verify(roleRepository, never()).deleteById(any());
        verify(roleRepository, never()).deleteAllInBatch();

        assertEquals(Integer.valueOf(3), adminExistente.getId());
        assertEquals("ADMIN", adminExistente.getNome());
        assertEquals(Boolean.TRUE, adminExistente.getInstanciavel());
    }

    @Test
    void seedRbac_naoDeclaraNenhumColaboradorDePapeisDeEscritorio() {
        // Prova estrutural (nao apenas comportamental): DatabaseSeeder nao declara nenhum campo
        // cujo tipo mencione TenantRole -- se declarasse, seria necessario um @Mock adicional
        // nesta classe (e em todas as outras que instanciam DatabaseSeeder) para @InjectMocks
        // resolver o construtor gerado por @RequiredArgsConstructor. Reforcado por grep no
        // ficheiro fonte (ver acceptance criteria da Task 2 do plano 125-01).
        boolean algumCampoMencionaTenantRole = java.util.Arrays.stream(DatabaseSeeder.class.getDeclaredFields())
                .anyMatch(f -> f.getType().getName().contains("TenantRole"));
        assertTrue(!algumCampoMencionaTenantRole, "DatabaseSeeder nao deve declarar nenhum colaborador TenantRole*");
    }
}
