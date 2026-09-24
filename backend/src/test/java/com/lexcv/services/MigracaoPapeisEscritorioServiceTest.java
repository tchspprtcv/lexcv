package com.lexcv.services;

import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.TenantRoleRepository;
import com.lexcv.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 126 Plan 03 (MIGR-01/MIGR-02): prova comportamental de {@link
 * MigracaoPapeisEscritorioService#migrar()} -- conversão feliz, reversibilidade de {@code
 * t_user_role}, instanciação convergente de moldes, idempotência total, salto da tenant
 * reservada, utilizador sem equivalente de escritório, aborto por deriva, e a ordem entre a
 * captura do estado anterior e a primeira escrita.
 *
 * <p>{@link ResolucaoPapeisService} e {@link VerificacaoDerivaPapeisService} são injectados REAIS
 * (construídos com mocks dos seus próprios colaboradores), nunca mockados -- mockar o
 * verificador tornaria o caso de deriva (8) uma tautologia, e é precisamente esse o caso que mais
 * importa provar. {@link SetupService} é mockado: o seu comportamento já está provado por {@code
 * SetupServiceInstanciacaoMoldesTest}.
 */
@ExtendWith(MockitoExtension.class)
class MigracaoPapeisEscritorioServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private SetupService setupService;

    private ResolucaoPapeisService resolucaoPapeisService;
    private VerificacaoDerivaPapeisService verificacaoDerivaPapeisService;
    private MigracaoPapeisEscritorioService migracaoPapeisEscritorioService;

    @BeforeEach
    void setUp() {
        resolucaoPapeisService = new ResolucaoPapeisService(tenantRoleRepository, roleRepository);
        verificacaoDerivaPapeisService = new VerificacaoDerivaPapeisService(resolucaoPapeisService);
        migracaoPapeisEscritorioService = new MigracaoPapeisEscritorioService(
                tenantRepository, userRepository, tenantRoleRepository, resolucaoPapeisService,
                verificacaoDerivaPapeisService, setupService);
    }

    private Permission permissao(String nome) {
        return Permission.builder().id(nome.hashCode()).nome(nome).build();
    }

    private Role roleGlobal(int id, String nome, Set<Permission> permissions) {
        return Role.builder().id(id).nome(nome).permissions(new HashSet<>(permissions)).build();
    }

    private TenantRole tenantRole(UUID tenantId, String nome, Integer moldeId, Set<Permission> permissions) {
        return TenantRole.builder().id(UUID.randomUUID()).tenantId(tenantId).nome(nome).moldeId(moldeId)
                .sistema(true).permissions(new HashSet<>(permissions)).build();
    }

    private User utilizador(UUID tenantId, String email, Set<Role> roles) {
        return User.builder().id(UUID.randomUUID()).tenantId(tenantId).nome(email).email(email)
                .roles(new HashSet<>(roles)).build();
    }

    // Caso 1 -- conversao feliz: cada utilizador fica associado ao TenantRole homonimo do seu papel global.
    @Test
    void migrar_conversaoFeliz_associaCadaUtilizadorAoTenantRoleHomonimo() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).nome("Escritorio A").build();

        Role adminGlobal = roleGlobal(1, "ADMIN", Set.of());
        Role advogadoGlobal = roleGlobal(2, "ADVOGADO", Set.of(permissao("processos:view")));
        TenantRole tenantRoleAdmin = tenantRole(tenantId, "ADMIN", 1, Set.of());
        TenantRole tenantRoleAdvogado = tenantRole(tenantId, "ADVOGADO", 2, Set.of(permissao("processos:view")));

        User userAdmin = utilizador(tenantId, "admin@escritorio.cv", Set.of(adminGlobal));
        User userAdvogado = utilizador(tenantId, "advogado@escritorio.cv", Set.of(advogadoGlobal));

        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of(userAdmin, userAdvogado));
        when(tenantRoleRepository.findByTenantId(tenantId)).thenReturn(List.of(tenantRoleAdmin, tenantRoleAdvogado));
        when(tenantRoleRepository.findByTenantIdAndNome(tenantId, "ADMIN")).thenReturn(Optional.of(tenantRoleAdmin));
        when(tenantRoleRepository.findByTenantIdAndNome(tenantId, "ADVOGADO"))
                .thenReturn(Optional.of(tenantRoleAdvogado));

        migracaoPapeisEscritorioService.migrar();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(captor.capture());

        User capturadoAdmin = captor.getAllValues().stream()
                .filter(u -> "admin@escritorio.cv".equals(u.getEmail())).findFirst().orElseThrow();
        User capturadoAdvogado = captor.getAllValues().stream()
                .filter(u -> "advogado@escritorio.cv".equals(u.getEmail())).findFirst().orElseThrow();

        assertEquals(Set.of(tenantRoleAdmin), capturadoAdmin.getTenantRoles());
        assertEquals(Set.of(tenantRoleAdvogado), capturadoAdvogado.getTenantRoles());
    }

    // Caso 2 -- t_user_role preservado (Decisao 4, reversibilidade): getRoles() intacto apos a conversao.
    @Test
    void migrar_userRolePreservado_papeisGlobaisIntactosAposConversao() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).nome("Escritorio B").build();

        Role advogadoGlobal = roleGlobal(2, "ADVOGADO", Set.of(permissao("processos:view")));
        TenantRole tenantRoleAdvogado = tenantRole(tenantId, "ADVOGADO", 2, Set.of(permissao("processos:view")));
        User userAdvogado = utilizador(tenantId, "advogado2@escritorio.cv", Set.of(advogadoGlobal));

        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of(userAdvogado));
        when(tenantRoleRepository.findByTenantId(tenantId)).thenReturn(List.of(tenantRoleAdvogado));
        when(tenantRoleRepository.findByTenantIdAndNome(tenantId, "ADVOGADO"))
                .thenReturn(Optional.of(tenantRoleAdvogado));

        migracaoPapeisEscritorioService.migrar();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User capturado = captor.getValue();

        // Reversibilidade: os papeis globais sobrevivem exactamente, mesma instancia de Role.
        assertEquals(Set.of(advogadoGlobal), capturado.getRoles());
        assertTrue(capturado.getRoles().contains(advogadoGlobal));
    }

    // Caso 3 -- instanciacao em falta: t_tenant_role vazio dispara instanciarMoldes exactamente uma vez.
    @Test
    void migrar_tenantSemMoldesInstanciados_chamaInstanciarMoldesUmaVez() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).nome("Escritorio Vazio").build();

        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of());
        when(tenantRoleRepository.findByTenantId(tenantId)).thenReturn(List.of());

        migracaoPapeisEscritorioService.migrar();

        verify(setupService, times(1)).instanciarMoldes(tenantId);
    }

    // Caso 4 -- instanciacao NAO repetida: t_tenant_role ja povoado nunca chama instanciarMoldes.
    @Test
    void migrar_tenantComMoldesJaInstanciados_nuncaChamaInstanciarMoldes() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).nome("Escritorio Povoado").build();
        TenantRole tenantRoleAdmin = tenantRole(tenantId, "ADMIN", 1, Set.of());

        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of());
        when(tenantRoleRepository.findByTenantId(tenantId)).thenReturn(List.of(tenantRoleAdmin));

        migracaoPapeisEscritorioService.migrar();

        verify(setupService, never()).instanciarMoldes(any());
    }

    // Caso 5 -- idempotencia: utilizador ja convertido para o alvo exacto nao produz nenhuma escrita.
    @Test
    void migrar_segundaPassagem_naoEscreveNadaParaUtilizadorJaConvertido() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).nome("Escritorio Idempotente").build();

        Role advogadoGlobal = roleGlobal(2, "ADVOGADO", Set.of(permissao("processos:view")));
        TenantRole tenantRoleAdvogado = tenantRole(tenantId, "ADVOGADO", 2, Set.of(permissao("processos:view")));
        User userJaConvertido = utilizador(tenantId, "ja@escritorio.cv", Set.of(advogadoGlobal));
        userJaConvertido.setTenantRoles(new HashSet<>(Set.of(tenantRoleAdvogado)));

        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of(userJaConvertido));
        when(tenantRoleRepository.findByTenantId(tenantId)).thenReturn(List.of(tenantRoleAdvogado));
        when(tenantRoleRepository.findByTenantIdAndNome(tenantId, "ADVOGADO"))
                .thenReturn(Optional.of(tenantRoleAdvogado));

        migracaoPapeisEscritorioService.migrar();

        verify(userRepository, never()).save(any());
        verify(setupService, never()).instanciarMoldes(any());
    }

    // Caso 6 -- tenant reservada LexCV saltada. E este o teste que falha se o administrador de
    // plataforma for trancado para fora: plataforma@lexcv.cv tem PLATAFORMA_ADMIN global, que nao
    // e instanciavel; se a migracao lhe atribuisse papeis de escritorio, o resolvedor passaria a
    // ler o lado de escritorio e ele perderia toda a autoridade de plataforma no instante do
    // arranque seguinte.
    @Test
    void migrar_tenantReservadaLexCV_ehSaltadaSemLerUtilizadoresNemInstanciarMoldes() {
        UUID tenantReservadoId = UUID.randomUUID();
        Tenant tenantReservado = Tenant.builder().id(tenantReservadoId).nome("LexCV").build();

        when(tenantRepository.findAll()).thenReturn(List.of(tenantReservado));

        migracaoPapeisEscritorioService.migrar();

        verify(userRepository, never()).findByTenantId(tenantReservadoId);
        verify(setupService, never()).instanciarMoldes(tenantReservadoId);
    }

    // Caso 7 -- utilizador sem equivalente de papel de escritorio: nenhuma escrita, nenhuma
    // excepcao, continua a resolver por papeis globais (falha para o lado seguro).
    @Test
    void migrar_utilizadorSemEquivalenteDeEscritorio_naoEscreveNemLancaExcecao() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).nome("Escritorio Parcial").build();

        Role tecnicoGlobal = roleGlobal(3, "TECNICO", Set.of());
        User userTecnico = utilizador(tenantId, "tecnico@escritorio.cv", Set.of(tecnicoGlobal));
        TenantRole tenantRoleAdmin = tenantRole(tenantId, "ADMIN", 1, Set.of());

        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of(userTecnico));
        when(tenantRoleRepository.findByTenantId(tenantId)).thenReturn(List.of(tenantRoleAdmin));
        when(tenantRoleRepository.findByTenantIdAndNome(tenantId, "TECNICO")).thenReturn(Optional.empty());

        migracaoPapeisEscritorioService.migrar();

        verify(userRepository, never()).save(any());
        assertTrue(userTecnico.getTenantRoles().isEmpty());
    }

    // Caso 8 -- deriva aborta: TenantRole homonimo com uma permissao a menos que o papel global
    // lanca IllegalStateException nomeando o utilizador afectado.
    @Test
    void migrar_derivaDetectada_lancaIllegalStateExceptionComEmailDoUtilizador() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).nome("Escritorio Deriva").build();

        Permission processosView = permissao("processos:view");
        Permission processosEdit = permissao("processos:edit");
        Role advogadoGlobal = roleGlobal(2, "ADVOGADO", Set.of(processosView, processosEdit));
        // TenantRole homonimo com uma permissao a MENOS -- a deriva a detectar.
        TenantRole tenantRoleAdvogadoComDeriva = tenantRole(tenantId, "ADVOGADO", 2, Set.of(processosView));
        User userDeriva = utilizador(tenantId, "deriva@escritorio.cv", Set.of(advogadoGlobal));

        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of(userDeriva));
        when(tenantRoleRepository.findByTenantId(tenantId)).thenReturn(List.of(tenantRoleAdvogadoComDeriva));
        when(tenantRoleRepository.findByTenantIdAndNome(tenantId, "ADVOGADO"))
                .thenReturn(Optional.of(tenantRoleAdvogadoComDeriva));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> migracaoPapeisEscritorioService.migrar());

        assertTrue(ex.getMessage().contains("deriva@escritorio.cv"));
    }

    // Caso 9 -- espaco negativo sobre a ordem: capturarAntes tem de ser invocado ANTES de
    // qualquer userRepository.save. Capturar depois de mutar mediria o depois duas vezes e
    // benzeria qualquer migracao.
    @Test
    void migrar_capturarAntesInvocadoAntesDeQualquerSave() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).nome("Escritorio Ordem").build();

        Role advogadoGlobal = roleGlobal(2, "ADVOGADO", Set.of(permissao("processos:view")));
        TenantRole tenantRoleAdvogado = tenantRole(tenantId, "ADVOGADO", 2, Set.of(permissao("processos:view")));
        User userOrdem = utilizador(tenantId, "ordem@escritorio.cv", Set.of(advogadoGlobal));

        VerificacaoDerivaPapeisService verificacaoSpy = spy(verificacaoDerivaPapeisService);
        MigracaoPapeisEscritorioService servicoComSpy = new MigracaoPapeisEscritorioService(
                tenantRepository, userRepository, tenantRoleRepository, resolucaoPapeisService,
                verificacaoSpy, setupService);

        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of(userOrdem));
        when(tenantRoleRepository.findByTenantId(tenantId)).thenReturn(List.of(tenantRoleAdvogado));
        when(tenantRoleRepository.findByTenantIdAndNome(tenantId, "ADVOGADO"))
                .thenReturn(Optional.of(tenantRoleAdvogado));

        servicoComSpy.migrar();

        InOrder ordem = inOrder(verificacaoSpy, userRepository);
        ordem.verify(verificacaoSpy).capturarAntes(anyCollection());
        ordem.verify(userRepository).save(any(User.class));
    }
}
