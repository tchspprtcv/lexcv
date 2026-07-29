package com.lexcv.seed;

import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.SystemSetting;
import com.lexcv.models.Tenant;
import com.lexcv.models.User;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 119 (PROV-01): prova as duas politicas de gating distintas introduzidas por este plano
 * -- o papel {@code PLATAFORMA_ADMIN} e a tenant reservada {@code "LexCV"} sao seedados em TODOS
 * os arranques (mesmo com {@code app.seed.enabled=false}), enquanto o utilizador bootstrap
 * {@code plataforma@lexcv.cv} so e criado quando {@code app.seed.enabled=true}.
 *
 * <p>O Caso 1 ({@link #run_comSeedDisabled_seedaTenantReservadaMasNuncaCriaCredencialDePlataforma})
 * e o teste de seguranca central desta classe: prova que uma instalacao de producao
 * ({@code seedEnabled=false}) ganha a infraestrutura de plataforma (papel + tenant) sem NUNCA
 * ganhar a credencial de privilegio maximo com password publicamente documentada
 * ({@code Pa$$w0rd}, ver {@code CLAUDE.md}) -- e exatamente por essa password ser publica que o
 * utilizador bootstrap passa a partilhar o mesmo gate {@code seedEnabled} que ja protege o
 * utilizador demo {@code admin@lexcv.cv}.
 *
 * <p>O Caso 4 ({@link #run_comBaseDeDadosVazia_leAsTresContagensAntesDeInserirATenantReservada})
 * e o teste de nao-regressao critico: prova que as tres contagens que protegem o bloco de dados
 * demo sao lidas ANTES de {@code seedTenantPlataforma()} inserir qualquer linha. A tenant
 * reservada e incondicional, logo se as contagens fossem lidas depois dela ficariam
 * permanentemente >= 1 e o bloco de dados demo deixaria silenciosamente de correr em qualquer
 * base de dados nova com {@code SEED_ENABLED=true}.
 *
 * <p>Segue a mesma convencao de todos os testes deste codebase (ver
 * {@code AdminControllerLimiteUtilizadoresTest}): sem harness {@code @SpringBootTest}, o
 * componente e instanciado com colaboradores mockados via Mockito ({@code @InjectMocks}
 * resolve o construtor gerado por {@code @RequiredArgsConstructor}).
 */
@ExtendWith(MockitoExtension.class)
class DatabaseSeederPlataformaAdminTest {

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

    private static final String NOME_TENANT_PLATAFORMA = "LexCV";
    private static final String EMAIL_PLATAFORMA = "plataforma@lexcv.cv";

    @BeforeEach
    void configurarStubsComunsDoSeedRbac() {
        // seedRbac() corre sempre, em todos os 5 casos -- estes stubs mantem-no inofensivo sob
        // strict stubs (MockitoExtension). lenient() porque nem todos os casos exercitam todos
        // os ramos (ex.: passwordEncoder so e chamado com seedEnabled=true).
        lenient().when(permissionRepository.findByNome(anyString()))
                .thenReturn(Optional.of(Permission.builder().nome("x").build()));
        lenient().when(roleRepository.findByNome(anyString()))
                .thenAnswer(invocation -> {
                    String nomePedido = invocation.getArgument(0);
                    return Optional.of(Role.builder().nome(nomePedido).permissions(new HashSet<>()).build());
                });
        lenient().when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hash-encriptado-irrelevante");
    }

    @Test
    void run_comSeedDisabled_seedaTenantReservadaMasNuncaCriaCredencialDePlataforma() throws Exception {
        ReflectionTestUtils.setField(seeder, "seedEnabled", false);

        seeder.run();

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertEquals(NOME_TENANT_PLATAFORMA, tenantCaptor.getValue().getNome());

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_comSeedEnabled_criaUtilizadorBootstrapLigadoATenantReservadaComUmUnicoPapel() throws Exception {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        when(systemSettingRepository.findById(SystemSetting.SINGLETON_ID)).thenReturn(Optional.empty());

        seeder.run();

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        Tenant tenantPlataforma = tenantCaptor.getValue();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User utilizadorPlataforma = userCaptor.getValue();

        assertEquals(EMAIL_PLATAFORMA, utilizadorPlataforma.getEmail());
        assertEquals(tenantPlataforma.getId(), utilizadorPlataforma.getTenantId());
        assertEquals(Boolean.TRUE, utilizadorPlataforma.getAtivo());
        assertEquals(1, utilizadorPlataforma.getRoles().size());
        assertTrue(utilizadorPlataforma.getRoles().stream().anyMatch(r -> "PLATAFORMA_ADMIN".equals(r.getNome())));
        assertNotNull(utilizadorPlataforma.getPasswordHash());
        assertNotEquals("Pa$$w0rd", utilizadorPlataforma.getPasswordHash());
    }

    @Test
    void run_numSegundoArranqueComSeedEnabled_naoRecriaTenantNemUtilizador() throws Exception {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        when(systemSettingRepository.findById(SystemSetting.SINGLETON_ID)).thenReturn(Optional.empty());

        Tenant tenantExistente = Tenant.builder().id(UUID.randomUUID()).nome(NOME_TENANT_PLATAFORMA).build();
        when(tenantRepository.findByNome(NOME_TENANT_PLATAFORMA)).thenReturn(Optional.of(tenantExistente));

        User utilizadorExistente = User.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantExistente.getId())
                .nome("Administrador de Plataforma")
                .email(EMAIL_PLATAFORMA)
                .build();
        when(userRepository.findByEmail(EMAIL_PLATAFORMA)).thenReturn(Optional.of(utilizadorExistente));

        seeder.run();

        verify(tenantRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void run_comBaseDeDadosVazia_leAsTresContagensAntesDeInserirATenantReservada() throws Exception {
        ReflectionTestUtils.setField(seeder, "seedEnabled", false);

        seeder.run();

        InOrder inOrder = inOrder(tenantRepository, userRepository, clienteRepository);
        inOrder.verify(tenantRepository).count();
        inOrder.verify(userRepository).count();
        inOrder.verify(clienteRepository).count();
        inOrder.verify(tenantRepository).save(any());
    }

    @Test
    void run_comSeedDisabled_seedaOPapelPlataformaAdminMesmoAssim() throws Exception {
        ReflectionTestUtils.setField(seeder, "seedEnabled", false);

        seeder.run();

        verify(roleRepository).findByNome("PLATAFORMA_ADMIN");
    }
}
