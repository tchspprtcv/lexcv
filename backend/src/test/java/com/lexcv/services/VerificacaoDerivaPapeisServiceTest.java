package com.lexcv.services;

import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 126 Plan 02 (MIGR-02): prova de que a verificacao de deriva zero reproduz as TRES
 * parcelas da uniao real de permissoes por construcao (via UserPrincipal.create, sem duplicar a
 * lista do bloco ADMIN), acumula todas as divergencias, e nomeia utilizador e
 * permissoes/papeis perdidos e ganhos.
 *
 * <p>{@link ResolucaoPapeisService} e injectado REAL (construido com mocks dos seus dois
 * colaboradores), nunca mockado -- mirroring o padrao "colaborador sem colaboradores proprios
 * usa-se real" deste projecto (ex. AlertasDiariosJobTest); um resolvedor mockado tornaria este
 * teste uma tautologia, incapaz de provar que os dois lados da comparacao sao genuinamente
 * independentes.
 *
 * <p>Padrao dos dados de teste: cada {@link User} de teste carrega simultaneamente {@code roles}
 * (global) e {@code tenantRoles} (escritorio) -- exactamente a forma real pos-migracao (D-04,
 * 126-CONTEXT.md: {@code t_user_role.role_id} mantem-se povoado). {@code capturarAntes} le
 * apenas o lado global; {@code verificarSemDeriva} le apenas o lado de escritorio via
 * {@link ResolucaoPapeisService} -- os dois lados nunca partilham a mesma fonte de dados dentro
 * do mesmo objecto {@link User}, o que prova a independencia exigida.
 */
@ExtendWith(MockitoExtension.class)
class VerificacaoDerivaPapeisServiceTest {

    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private RoleRepository roleRepository;

    private ResolucaoPapeisService resolucaoPapeisServiceReal;
    private VerificacaoDerivaPapeisService service;

    @BeforeEach
    void setUp() {
        resolucaoPapeisServiceReal = new ResolucaoPapeisService(tenantRoleRepository, roleRepository);
        service = new VerificacaoDerivaPapeisService(resolucaoPapeisServiceReal);
    }

    private Permission permissao(String nome) {
        return Permission.builder().id(nome.hashCode()).nome(nome).build();
    }

    private User utilizador(UUID id, String email, Set<Role> roles, Set<TenantRole> tenantRoles, Set<String> permissoesDirectas) {
        return User.builder()
                .id(id)
                .tenantId(UUID.randomUUID())
                .nome("Utilizador Teste")
                .email(email)
                .roles(new HashSet<>(roles))
                .tenantRoles(new HashSet<>(tenantRoles))
                .permissions(new HashSet<>(permissoesDirectas))
                .build();
    }

    // Caso 1 -- sem deriva, caminho de escritorio: o papel de escritorio tem exactamente as
    // mesmas permissoes que o papel global correspondente.
    @Test
    void verificarSemDeriva_caminhoDeEscritorio_semDivergencia_naoLanca() {
        Permission permissaoComum = permissao("processos:edit");
        Role roleGlobal = Role.builder().id(2).nome("ADVOGADO")
                .permissions(new HashSet<>(Set.of(permissaoComum))).build();
        TenantRole tenantRole = TenantRole.builder().nome("ADVOGADO").moldeId(2)
                .permissions(new HashSet<>(Set.of(permissaoComum))).build();

        User user = utilizador(UUID.randomUUID(), "advogado@escritorio.cv",
                Set.of(roleGlobal), Set.of(tenantRole), Set.of());

        Map<UUID, VerificacaoDerivaPapeisService.EstadoEfectivo> antes = service.capturarAntes(List.of(user));

        assertDoesNotThrow(() -> service.verificarSemDeriva(antes, List.of(user)));
    }

    // Caso 2 -- sem deriva, caminho global (plataforma@lexcv.cv): utilizador sem papeis de
    // escritorio, antes e depois identicos por fallback. Falha se o administrador de plataforma
    // ficar trancado fora.
    @Test
    void verificarSemDeriva_caminhoGlobal_plataformaAdmin_semDivergencia_naoLanca() {
        Role plataformaAdmin = Role.builder().id(1).nome("PLATAFORMA_ADMIN")
                .permissions(new HashSet<>(Set.of(permissao("plataforma:manage")))).build();

        User user = utilizador(UUID.randomUUID(), "plataforma@lexcv.cv",
                Set.of(plataformaAdmin), Set.of(), Set.of());

        Map<UUID, VerificacaoDerivaPapeisService.EstadoEfectivo> antes = service.capturarAntes(List.of(user));

        assertDoesNotThrow(() -> service.verificarSemDeriva(antes, List.of(user)));
    }

    // Caso 3 -- deriva na parcela 1: permissao perdida. O papel de escritorio tem uma permissao a
    // menos que o global.
    @Test
    void verificarSemDeriva_permissaoPerdida_lancaComEmailEPermissaoNomeados() {
        Permission permissaoMantida = permissao("processos:edit");
        Permission permissaoPerdida = permissao("processos:view");
        Role roleGlobal = Role.builder().id(2).nome("ADVOGADO")
                .permissions(new HashSet<>(Set.of(permissaoMantida, permissaoPerdida))).build();
        TenantRole tenantRole = TenantRole.builder().nome("ADVOGADO").moldeId(2)
                .permissions(new HashSet<>(Set.of(permissaoMantida))).build();

        User user = utilizador(UUID.randomUUID(), "perdeu@escritorio.cv",
                Set.of(roleGlobal), Set.of(tenantRole), Set.of());

        Map<UUID, VerificacaoDerivaPapeisService.EstadoEfectivo> antes = service.capturarAntes(List.of(user));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.verificarSemDeriva(antes, List.of(user)));

        assertTrue(ex.getMessage().contains("perdeu@escritorio.cv"));
        assertTrue(ex.getMessage().contains("processos:view"));
    }

    // Caso 4 -- deriva na parcela 1: permissao ganha. Uma verificacao que so detectasse perdas
    // benzeria uma escalada de privilegio.
    @Test
    void verificarSemDeriva_permissaoGanha_lancaComPermissaoGanhaNomeada() {
        Permission permissaoComum = permissao("processos:edit");
        Permission permissaoGanha = permissao("financeiro:manage");
        Role roleGlobal = Role.builder().id(2).nome("ADVOGADO")
                .permissions(new HashSet<>(Set.of(permissaoComum))).build();
        TenantRole tenantRole = TenantRole.builder().nome("ADVOGADO").moldeId(2)
                .permissions(new HashSet<>(Set.of(permissaoComum, permissaoGanha))).build();

        User user = utilizador(UUID.randomUUID(), "ganhou@escritorio.cv",
                Set.of(roleGlobal), Set.of(tenantRole), Set.of());

        Map<UUID, VerificacaoDerivaPapeisService.EstadoEfectivo> antes = service.capturarAntes(List.of(user));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.verificarSemDeriva(antes, List.of(user)));

        assertTrue(ex.getMessage().contains("ganhou@escritorio.cv"));
        assertTrue(ex.getMessage().contains("financeiro:manage"));
    }

    // Caso 5 -- parcela 2 preservada: uma permissao directa continua presente nos dois lados e
    // nao e reportada como divergencia.
    @Test
    void verificarSemDeriva_permissaoDirecta_naoEReportadaComoDivergencia() {
        Permission permissaoComum = permissao("processos:edit");
        Role roleGlobal = Role.builder().id(2).nome("ADVOGADO")
                .permissions(new HashSet<>(Set.of(permissaoComum))).build();
        TenantRole tenantRole = TenantRole.builder().nome("ADVOGADO").moldeId(2)
                .permissions(new HashSet<>(Set.of(permissaoComum))).build();

        User user = utilizador(UUID.randomUUID(), "directa@escritorio.cv",
                Set.of(roleGlobal), Set.of(tenantRole), Set.of("financeiro:view"));

        Map<UUID, VerificacaoDerivaPapeisService.EstadoEfectivo> antes = service.capturarAntes(List.of(user));

        assertTrue(antes.get(user.getId()).getPermissoes().contains("financeiro:view"));
        assertDoesNotThrow(() -> service.verificarSemDeriva(antes, List.of(user)));
    }

    // Caso 6 -- parcela 3 coberta: utilizador ADMIN em que os dois lados contem "ADMIN" -- as 20
    // permissoes do bloco entram nos dois lados, sem divergencia. Variante: o papel ADMIN
    // desaparece do lado novo -- lanca, e a mensagem nomeia as permissoes do bloco como perdidas.
    @Test
    void verificarSemDeriva_blocoAdmin_cobertoPorConstrucao_semDivergenciaSePresenteEDivergeSeDesaparece() {
        UUID id = UUID.randomUUID();
        Role adminGlobal = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        TenantRole tenantRoleAdmin = TenantRole.builder().nome("ADMIN").moldeId(1).permissions(new HashSet<>()).build();
        User userAntes = utilizador(id, "admin@escritorio.cv", Set.of(adminGlobal), Set.of(tenantRoleAdmin), Set.of());

        Map<UUID, VerificacaoDerivaPapeisService.EstadoEfectivo> antes = service.capturarAntes(List.of(userAntes));

        // Confirma que a parcela 3 (bloco ADMIN) foi de facto capturada do lado "antes".
        assertTrue(antes.get(id).getPermissoes().contains("rbac:manage"));

        // Sub-caso (a): ADMIN presente nos dois lados -- sem divergencia.
        assertDoesNotThrow(() -> service.verificarSemDeriva(antes, List.of(userAntes)));

        // Sub-caso (b): o papel de escritorio foi renomeado para algo que nao e "ADMIN" -- o
        // utilizador continua a ter o papel global ADMIN, mas o caminho de escritorio tem
        // precedencia (ha TenantRoles), pelo que a resolucao nova ja nao ve "ADMIN" e perde as
        // 20 permissoes do bloco. Prova que a parcela 3 esta de facto coberta, nao apenas assumida.
        TenantRole tenantRoleRenomeado = TenantRole.builder().nome("GESTOR").moldeId(1).permissions(new HashSet<>()).build();
        User userDepois = utilizador(id, "admin@escritorio.cv", Set.of(adminGlobal), Set.of(tenantRoleRenomeado), Set.of());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.verificarSemDeriva(antes, List.of(userDepois)));

        assertTrue(ex.getMessage().contains("admin@escritorio.cv"));
        assertTrue(ex.getMessage().contains("rbac:manage"));
    }

    // Caso 7 -- multiplas divergencias: dois utilizadores divergentes, a excepcao nomeia os dois.
    @Test
    void verificarSemDeriva_multiplasDivergencias_nomeiaTodosOsUtilizadores() {
        Permission permissaoMantida = permissao("processos:edit");
        Permission permissaoPerdida1 = permissao("processos:view");
        Role roleGlobal1 = Role.builder().id(2).nome("ADVOGADO")
                .permissions(new HashSet<>(Set.of(permissaoMantida, permissaoPerdida1))).build();
        TenantRole tenantRole1 = TenantRole.builder().nome("ADVOGADO").moldeId(2)
                .permissions(new HashSet<>(Set.of(permissaoMantida))).build();
        User user1 = utilizador(UUID.randomUUID(), "primeiro@escritorio.cv",
                Set.of(roleGlobal1), Set.of(tenantRole1), Set.of());

        Permission permissaoPerdida2 = permissao("agenda:edit");
        Role roleGlobal2 = Role.builder().id(3).nome("TECNICO")
                .permissions(new HashSet<>(Set.of(permissaoPerdida2))).build();
        TenantRole tenantRole2 = TenantRole.builder().nome("TECNICO").moldeId(3)
                .permissions(new HashSet<>()).build();
        User user2 = utilizador(UUID.randomUUID(), "segundo@escritorio.cv",
                Set.of(roleGlobal2), Set.of(tenantRole2), Set.of());

        Map<UUID, VerificacaoDerivaPapeisService.EstadoEfectivo> antes =
                service.capturarAntes(List.of(user1, user2));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.verificarSemDeriva(antes, List.of(user1, user2)));

        assertTrue(ex.getMessage().contains("primeiro@escritorio.cv"));
        assertTrue(ex.getMessage().contains("segundo@escritorio.cv"));
    }

    // Caso 8 -- utilizador presente em depois e ausente de antes: tratado como divergencia, nao
    // ignorado.
    @Test
    void verificarSemDeriva_utilizadorAusenteDeAntes_eTratadoComoDivergencia() {
        Role roleGlobal = Role.builder().id(2).nome("ADVOGADO").permissions(new HashSet<>()).build();
        User userConhecido = utilizador(UUID.randomUUID(), "conhecido@escritorio.cv",
                Set.of(roleGlobal), Set.of(), Set.of());
        Map<UUID, VerificacaoDerivaPapeisService.EstadoEfectivo> antes =
                service.capturarAntes(List.of(userConhecido));

        User userDesconhecido = utilizador(UUID.randomUUID(), "novo-durante-conversao@escritorio.cv",
                Set.of(roleGlobal), Set.of(), Set.of());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.verificarSemDeriva(antes, List.of(userConhecido, userDesconhecido)));

        assertTrue(ex.getMessage().contains("novo-durante-conversao@escritorio.cv"));
    }
}
