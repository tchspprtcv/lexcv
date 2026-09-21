package com.lexcv.config;

import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.TenantRoleRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.ResolucaoPapeisService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 126 (Plan 04): prova comportamental de que {@link JwtAuthenticationFilter} resolve a
 * autoridade de um pedido autenticado por {@link ResolucaoPapeisService} -- papeis de escritorio
 * ({@link TenantRole}) quando o utilizador os tem, papeis globais ({@link Role}) quando nao tem.
 * O resolvedor e construido REAL aqui (nunca mockado) para que as asserções exercitem a
 * resolucao verdadeira, nao um stub -- so os dois repositorios de que depende
 * ({@link TenantRoleRepository}, {@link RoleRepository}) sao mocks, e nenhum dos dois metodos
 * exercitados por este ficheiro ({@code resolverNomesPapeis}/{@code resolverPermissoesEfectivas})
 * chega sequer a invocá-los -- operam inteiramente sobre as colecoes ja carregadas do
 * {@link User}.
 *
 * <p>Segue a convencao de {@link JwtAuthenticationFilterTenantSuspensoTest}: sem MockMvc/
 * {@code @SpringBootTest} -- instanciacao directa do filtro com colaboradores Mockito.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterPapeisEscritorioTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantRoleRepository tenantRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private Claims claims;

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private JwtAuthenticationFilter novoFiltro() {
        ResolucaoPapeisService resolucaoPapeisService =
                new ResolucaoPapeisService(tenantRoleRepository, roleRepository);
        return new JwtAuthenticationFilter(tokenProvider, userRepository, tenantRepository, resolucaoPapeisService);
    }

    private void stubTokenValidoPara(UUID userId) {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-de-teste");
        when(tokenProvider.validateToken("token-de-teste")).thenReturn(true);
        when(tokenProvider.getClaimsFromToken("token-de-teste")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(userId.toString());
    }

    private Tenant tenantAtivo(UUID id) {
        return Tenant.builder().id(id).nome("Tenant de Teste").ativo(true).build();
    }

    private Permission permissao(String nome) {
        return Permission.builder().id(nome.hashCode()).nome(nome).build();
    }

    private Role roleGlobal(String nome, String... permissoes) {
        Set<Permission> perms = new HashSet<>();
        for (String p : permissoes) {
            perms.add(permissao(p));
        }
        return Role.builder().id(nome.hashCode()).nome(nome).permissions(perms).build();
    }

    private TenantRole tenantRole(String nome, String... permissoes) {
        Set<Permission> perms = new HashSet<>();
        for (String p : permissoes) {
            perms.add(permissao(p));
        }
        return TenantRole.builder().id(UUID.randomUUID()).nome(nome).permissions(perms).build();
    }

    private void correrFiltro() throws Exception {
        novoFiltro().doFilterInternal(request, response, filterChain);
    }

    private Set<String> authoritiesComo(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    // Caso 1: utilizador com papeis de escritorio -> as autoridades derivam do lado de
    // escritorio, e NENHUMA permissao exclusiva do lado global (deliberadamente diferente)
    // aparece.
    @Test
    void utilizadorComPapeisDeEscritorio_resolvePeloLadoDeEscritorioSemMisturarComOGlobal() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        stubTokenValidoPara(userId);

        Role globalDiferente = roleGlobal("ADVOGADO", "financeiro:manage");
        TenantRole papelEscritorio = tenantRole("ADVOGADO", "processos:view");
        User utilizador = User.builder()
                .id(userId).tenantId(tenantId).nome("Utilizador").email("u@lexcv.cv").ativo(true)
                .roles(Set.of(globalDiferente))
                .tenantRoles(Set.of(papelEscritorio))
                .permissions(Set.of())
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(utilizador));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantAtivo(tenantId)));

        correrFiltro();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Set<String> authorities = authoritiesComo(auth);
        assertTrue(authorities.contains("processos:view"));
        assertFalse(authorities.contains("financeiro:manage"));
    }

    // Caso 2 (o que fecha o T-126-21): plataforma@lexcv.cv -- zero papeis de escritorio,
    // papel global PLATAFORMA_ADMIN -> a autoridade ROLE_PLATAFORMA_ADMIN tem de continuar a
    // ser produzida. Falha se o cutover trancar o administrador de plataforma fora da sua
    // propria consola.
    @Test
    void utilizadorDePlataformaSemPapeisDeEscritorio_continuaAReceberRolePlataformaAdmin() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        stubTokenValidoPara(userId);

        Role plataformaAdmin = roleGlobal("PLATAFORMA_ADMIN");
        User plataforma = User.builder()
                .id(userId).tenantId(tenantId).nome("Admin Plataforma").email("plataforma@lexcv.cv").ativo(true)
                .roles(Set.of(plataformaAdmin))
                .tenantRoles(Set.of())
                .permissions(Set.of())
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(plataforma));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantAtivo(tenantId)));

        correrFiltro();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Set<String> authorities = authoritiesComo(auth);
        assertTrue(authorities.contains("ROLE_PLATAFORMA_ADMIN"));
    }

    // Caso 3: utilizador cujo papel de ESCRITORIO se chama "ADMIN" -> as 20 permissoes da
    // parcela 3 (bloco ADMIN de UserPrincipal.create) continuam presentes, provando que
    // UserPrincipal.create e cego a proveniencia do papel (escritorio ou global).
    @Test
    void utilizadorComPapelDeEscritorioChamadoAdmin_recebeParcela3DoBlocoAdmin() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        stubTokenValidoPara(userId);

        TenantRole papelAdminDeEscritorio = tenantRole("ADMIN");
        User utilizador = User.builder()
                .id(userId).tenantId(tenantId).nome("Admin Escritorio").email("admin@escritorio.cv").ativo(true)
                .roles(Set.of())
                .tenantRoles(Set.of(papelAdminDeEscritorio))
                .permissions(Set.of())
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(utilizador));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantAtivo(tenantId)));

        correrFiltro();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Set<String> authorities = authoritiesComo(auth);
        // Subconjunto representativo das 20 entradas de UserPrincipal.create (mantido em sincronia
        // com DatabaseSeeder.CATALOGO_PERMISSOES, Phase 124) -- nao a lista inteira, para o teste
        // nao ficar acoplado a exact-match se essa lista crescer.
        assertTrue(authorities.contains("rbac:manage"));
        assertTrue(authorities.contains("users:manage"));
        assertTrue(authorities.contains("financeiro:manage"));
        assertTrue(authorities.contains("pareceres:manage"));
    }

    // Caso 4a: permissao directa (t_user_permission) presente nas autoridades quando o
    // utilizador resolve pelo lado de ESCRITORIO.
    @Test
    void permissaoDirecta_apareceNasAutoridadesNoCaminhoDeEscritorio() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        stubTokenValidoPara(userId);

        TenantRole papelEscritorio = tenantRole("ASSISTENTE", "agenda:view");
        User utilizador = User.builder()
                .id(userId).tenantId(tenantId).nome("Utilizador").email("u2@lexcv.cv").ativo(true)
                .roles(Set.of())
                .tenantRoles(Set.of(papelEscritorio))
                .permissions(Set.of("documentos:view"))
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(utilizador));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantAtivo(tenantId)));

        correrFiltro();

        Set<String> authorities = authoritiesComo(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(authorities.contains("documentos:view"));
        assertTrue(authorities.contains("agenda:view"));
    }

    // Caso 4b: idem, mas com o utilizador a resolver pelo lado GLOBAL (sem papeis de
    // escritorio) -- prova que a parcela 2 (permissoes diretas) e somada em ambos os caminhos,
    // nao apenas no de escritorio.
    @Test
    void permissaoDirecta_apareceNasAutoridadesNoCaminhoGlobal() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        stubTokenValidoPara(userId);

        Role global = roleGlobal("TECNICO", "agenda:edit");
        User utilizador = User.builder()
                .id(userId).tenantId(tenantId).nome("Utilizador").email("u3@lexcv.cv").ativo(true)
                .roles(Set.of(global))
                .tenantRoles(Set.of())
                .permissions(Set.of("clientes:view"))
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(utilizador));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantAtivo(tenantId)));

        correrFiltro();

        Set<String> authorities = authoritiesComo(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(authorities.contains("clientes:view"));
        assertTrue(authorities.contains("agenda:edit"));
    }

    // Caso 5 (espaco negativo -- prova, nao apenas afirma, o comentario de
    // JwtAuthenticationFilter:50-58): exactamente UMA query de utilizador por pedido, mesmo
    // depois do cutover para o resolvedor.
    @Test
    void resolucaoDeAutoridade_naoIntroduzQueryAdicionalDeUtilizador() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        stubTokenValidoPara(userId);

        TenantRole papelEscritorio = tenantRole("ADVOGADO", "processos:view");
        User utilizador = User.builder()
                .id(userId).tenantId(tenantId).nome("Utilizador").email("u4@lexcv.cv").ativo(true)
                .roles(Set.of())
                .tenantRoles(Set.of(papelEscritorio))
                .permissions(Set.of())
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(utilizador));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantAtivo(tenantId)));

        correrFiltro();

        verify(userRepository, times(1)).findById(any());
    }

    // Caso 6a (guarda existente, regressao): utilizador inactivo nao autentica, mesmo tendo
    // papeis de escritorio -- prova que o cutover nao abriu um caminho que salta a guarda.
    @Test
    void utilizadorInativoComPapeisDeEscritorio_naoAutentica() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        stubTokenValidoPara(userId);

        TenantRole papelEscritorio = tenantRole("ADVOGADO", "processos:view");
        User utilizador = User.builder()
                .id(userId).tenantId(tenantId).nome("Utilizador").email("u5@lexcv.cv").ativo(false)
                .roles(Set.of())
                .tenantRoles(Set.of(papelEscritorio))
                .permissions(Set.of())
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(utilizador));

        correrFiltro();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // Caso 6b (guarda existente, regressao): tenant suspenso nao autentica, mesmo tendo
    // papeis de escritorio.
    @Test
    void utilizadorComPapeisDeEscritorioETenantSuspenso_naoAutentica() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        stubTokenValidoPara(userId);

        TenantRole papelEscritorio = tenantRole("ADVOGADO", "processos:view");
        User utilizador = User.builder()
                .id(userId).tenantId(tenantId).nome("Utilizador").email("u6@lexcv.cv").ativo(true)
                .roles(Set.of())
                .tenantRoles(Set.of(papelEscritorio))
                .permissions(Set.of())
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(utilizador));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(Tenant.builder().id(tenantId).ativo(false).build()));

        correrFiltro();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
