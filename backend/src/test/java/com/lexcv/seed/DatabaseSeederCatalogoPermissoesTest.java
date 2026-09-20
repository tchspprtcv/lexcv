package com.lexcv.seed;

import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.SystemSetting;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 124 (CATL-01/CATL-02/CATL-03): prova que {@code DatabaseSeeder.seedRbac()} e um upsert
 * nao-destrutivo sobre {@code t_permission} -- cria as 20 permissoes do catalogo com rotulo/
 * descricao/modulo/ordem preenchidos num primeiro arranque, actualiza esses mesmos campos numa
 * linha ja existente sem nunca apagar nem recriar a linha (a invariante CATL-02: {@code id} e
 * {@code nome} tem de sobreviver intactos, porque {@code t_role_permission}/
 * {@code t_user_permission} referenciam {@code t_permission.id}), e nunca invoca nenhuma
 * operacao de remocao sobre {@code permissionRepository}.
 *
 * <p>Segue o andaime de {@link DatabaseSeederPlataformaAdminTest}: sem harness
 * {@code @SpringBootTest}, {@code DatabaseSeeder} e instanciado com colaboradores mockados via
 * Mockito ({@code @InjectMocks} resolve o construtor gerado por
 * {@code @RequiredArgsConstructor}). {@code seedEnabled = false} e o cenario mais barato -- e
 * tambem o cenario de producao -- porque {@code run()} executa {@code seedRbac()} e
 * {@code seedTenantPlataforma()} e regressa antes de tocar em qualquer outro repositorio.
 */
@ExtendWith(MockitoExtension.class)
class DatabaseSeederCatalogoPermissoesTest {

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

    @BeforeEach
    void configurarStubsComuns() {
        lenient().when(roleRepository.findByNome(anyString()))
                .thenAnswer(invocation -> {
                    String nomePedido = invocation.getArgument(0);
                    return Optional.of(Role.builder().nome(nomePedido).permissions(new HashSet<>()).build());
                });
        lenient().when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(java.util.UUID.randomUUID());
            return tenant;
        });
        lenient().when(permissionRepository.save(any(Permission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void executarComCatalogoVazio() throws Exception {
        ReflectionTestUtils.setField(seeder, "seedEnabled", false);
        when(permissionRepository.findByNome(anyString())).thenReturn(Optional.empty());
        seeder.run();
    }

    @Test
    void seedRbac_noPrimeiroArranque_criaAs20PermissoesComCamposDescritivosPreenchidos() throws Exception {
        executarComCatalogoVazio();

        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository, org.mockito.Mockito.atLeast(20)).save(captor.capture());

        List<Permission> capturadas = captor.getAllValues();
        Permission clientesView = capturadas.stream()
                .filter(p -> "clientes:view".equals(p.getNome()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("clientes:view nao foi gravada"));

        assertEquals("Visualizar Clientes", clientesView.getRotulo());
        assertEquals("Ver lista e detalhes de clientes", clientesView.getDescricao());
        assertEquals("Clientes", clientesView.getModulo());
        assertNotNull(clientesView.getOrdem());
        assertEquals(Boolean.FALSE, clientesView.getReservadaPlataforma());
    }

    @Test
    void seedRbac_asTresChavesAntesInvisiveis_tornamSeOfereciveisComRotuloProprio() throws Exception {
        executarComCatalogoVazio();

        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository, org.mockito.Mockito.atLeast(20)).save(captor.capture());

        List<Permission> capturadas = captor.getAllValues();
        Set<String> chavesEsperadas = Set.of("processos:create", "processos:manage", "financeiro:manage");

        for (String chave : chavesEsperadas) {
            Permission p = capturadas.stream()
                    .filter(x -> chave.equals(x.getNome()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(chave + " nao foi gravada"));
            assertNotNull(p.getRotulo());
            assertFalse(p.getRotulo().isBlank());
            assertNotNull(p.getDescricao());
            assertFalse(p.getDescricao().isBlank());
            assertNotNull(p.getModulo());
            assertFalse(p.getModulo().isBlank());
            assertEquals(Boolean.FALSE, p.getReservadaPlataforma());
        }
    }

    @Test
    void seedRbac_numSegundoArranque_actualizaAMesmaInstanciaSemTocarEmIdNemNome() throws Exception {
        ReflectionTestUtils.setField(seeder, "seedEnabled", false);

        Permission clientesViewExistente = Permission.builder()
                .id(7)
                .nome("clientes:view")
                .rotulo("Rótulo Velho")
                .build();

        when(permissionRepository.findByNome(anyString())).thenAnswer(invocation -> {
            String chave = invocation.getArgument(0);
            if ("clientes:view".equals(chave)) {
                return Optional.of(clientesViewExistente);
            }
            return Optional.empty();
        });

        seeder.run();

        assertEquals(Integer.valueOf(7), clientesViewExistente.getId());
        assertEquals("clientes:view", clientesViewExistente.getNome());
        assertEquals("Visualizar Clientes", clientesViewExistente.getRotulo());
        // IN-01 (124-REVIEW.md): existente.setReservadaPlataforma(false) (linha 395 de
        // DatabaseSeeder.java) so estava coberto no caminho de criacao -- esta era a asserção
        // em falta no caminho de actualizacao, onde o comportamento (um-reservar uma permissao
        // manualmente reservada no proximo arranque) mais importa.
        assertEquals(Boolean.FALSE, clientesViewExistente.getReservadaPlataforma());
    }

    @Test
    void seedRbac_nuncaInvocaOperacaoDeRemocaoSobrePermissionRepository() throws Exception {
        executarComCatalogoVazio();

        verify(permissionRepository, never()).delete(any());
        verify(permissionRepository, never()).deleteAll();
        verify(permissionRepository, never()).deleteById(any());
        verify(permissionRepository, never()).deleteAllInBatch();
    }

    @Test
    void seedRbac_ordemDasEntradas_eDistintaEEstavelParaProcessos() throws Exception {
        executarComCatalogoVazio();

        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionRepository, org.mockito.Mockito.atLeast(20)).save(captor.capture());

        List<Permission> capturadas = captor.getAllValues();
        long ordensDistintas = capturadas.stream().map(Permission::getOrdem).distinct().count();
        assertEquals(capturadas.size(), ordensDistintas);

        Integer ordemEdit = capturadas.stream().filter(p -> "processos:edit".equals(p.getNome())).findFirst()
                .orElseThrow().getOrdem();
        Integer ordemCreate = capturadas.stream().filter(p -> "processos:create".equals(p.getNome())).findFirst()
                .orElseThrow().getOrdem();
        Integer ordemManage = capturadas.stream().filter(p -> "processos:manage".equals(p.getNome())).findFirst()
                .orElseThrow().getOrdem();

        assertTrue(ordemCreate > ordemEdit);
        assertTrue(ordemCreate < ordemManage);
    }
}
