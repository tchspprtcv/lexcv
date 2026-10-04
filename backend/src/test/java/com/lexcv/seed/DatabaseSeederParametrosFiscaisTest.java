package com.lexcv.seed;

import com.lexcv.models.CodigoParametroFiscal;
import com.lexcv.models.ParametroFiscal;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.repositories.ClienteRepository;
import com.lexcv.repositories.ContaCorrenteRepository;
import com.lexcv.repositories.EventoRepository;
import com.lexcv.repositories.FaseProcessualRepository;
import com.lexcv.repositories.HonorarioRepository;
import com.lexcv.repositories.PagamentoRepository;
import com.lexcv.repositories.ParametroFiscalRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 133 (CFG-04), Plan 02: o seeder cria os parâmetros fiscais em falta em TODOS os
 * arranques (mesmo com {@code seedEnabled = false}: são dados legais de referência, como o RBAC
 * e a tenant reservada) e nunca altera nem apaga uma linha existente.
 */
@ExtendWith(MockitoExtension.class)
class DatabaseSeederParametrosFiscaisTest {

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
    @Mock private ParametroFiscalRepository parametroFiscalRepository;

    @InjectMocks
    private DatabaseSeeder seeder;

    private static final LocalDate INICIO_TECNICO = LocalDate.of(2000, 1, 1);

    @BeforeEach
    void configurarStubsComunsDoSeedRbac() {
        lenient().when(permissionRepository.findByNome(anyString()))
                .thenReturn(Optional.of(Permission.builder().nome("x").build()));
        lenient().when(roleRepository.findByNome(anyString()))
                .thenAnswer(invocation -> Optional.of(Role.builder()
                        .nome(invocation.getArgument(0)).permissions(new HashSet<>()).build()));
        lenient().when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
        // seedEnabled = false: prova também que a semeadura é incondicional.
        ReflectionTestUtils.setField(seeder, "seedEnabled", false);
    }

    @Test
    void run_semParametros_criaIvaERetencaoComInicioTecnico() throws Exception {
        when(parametroFiscalRepository.findByCodigoAndVigenteDesde(anyString(), eq(INICIO_TECNICO)))
                .thenReturn(Optional.empty());

        seeder.run();

        ArgumentCaptor<ParametroFiscal> captor = ArgumentCaptor.forClass(ParametroFiscal.class);
        verify(parametroFiscalRepository, times(2)).save(captor.capture());
        List<ParametroFiscal> gravados = captor.getAllValues();

        ParametroFiscal iva = gravados.stream()
                .filter(p -> p.getCodigo().equals(CodigoParametroFiscal.IVA_TAXA_NORMAL.name()))
                .findFirst().orElseThrow();
        ParametroFiscal retencao = gravados.stream()
                .filter(p -> p.getCodigo().equals(CodigoParametroFiscal.RETENCAO_SUGERIDA.name()))
                .findFirst().orElseThrow();

        assertEquals(0, iva.getValor().compareTo(new BigDecimal("15")));
        assertEquals(INICIO_TECNICO, iva.getVigenteDesde());
        assertNotNull(iva.getCreatedAt());
        assertEquals(0, retencao.getValor().compareTo(new BigDecimal("20")));
        assertEquals(INICIO_TECNICO, retencao.getVigenteDesde());
        assertNotNull(retencao.getCreatedAt());
    }

    @Test
    void run_comParametrosExistentes_naoGravaNada_mesmoComValorDiferente() throws Exception {
        when(parametroFiscalRepository.findByCodigoAndVigenteDesde(anyString(), eq(INICIO_TECNICO)))
                .thenAnswer(invocation -> Optional.of(ParametroFiscal.builder()
                        .codigo(invocation.getArgument(0))
                        .valor(new BigDecimal("99"))
                        .vigenteDesde(INICIO_TECNICO)
                        .build()));

        seeder.run();

        verify(parametroFiscalRepository, times(2)).findByCodigoAndVigenteDesde(anyString(), eq(INICIO_TECNICO));
        verify(parametroFiscalRepository, never()).save(any());
        verify(parametroFiscalRepository, never()).delete(any());
        verify(parametroFiscalRepository, never()).deleteAll();
    }
}
