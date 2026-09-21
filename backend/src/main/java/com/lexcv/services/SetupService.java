package com.lexcv.services;

import com.lexcv.dtos.SetupInitializeRequest;
import com.lexcv.models.Role;
import com.lexcv.models.SystemSetting;
import com.lexcv.models.Tenant;
import com.lexcv.models.User;
import com.lexcv.models.TenantRole;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.SystemSettingRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.TenantRoleRepository;
import com.lexcv.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SetupService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern STRONG_PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"
    );
    private static final Pattern DATA_URL_IMAGE_PATTERN = Pattern.compile("^data:image/[a-zA-Z0-9.+-]+;base64,.+$");
    private static final int MAX_LOGO_LENGTH = 5_000_000;

    // Phase 125 Plan 02 (MOLD-01): defesa em profundidade sobre o filtro SQL de
    // findAllByInstanciavelTrue(). A exclusão de PLATAFORMA_ADMIN já é garantida ao nível de
    // SQL (Role.instanciavel = false, Phase 125 Plan 01), mas uma regressão nesse filtro (ex.:
    // uma migração futura que reponha a coluna a true por engano) não deve conseguir instanciar
    // o papel de plataforma como se fosse um molde de escritório. Guarda por nome literal, o
    // mesmo idioma de TENANT_RESERVADO em PlatformAdminController.
    private static final String NOME_PAPEL_PLATAFORMA = "PLATAFORMA_ADMIN";

    private final SystemSettingRepository systemSettingRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantRoleRepository tenantRoleRepository;

    public boolean isInitialized() {
        return systemSettingRepository.findById(SystemSetting.SINGLETON_ID)
                .map(SystemSetting::getInitialized)
                .orElse(false);
    }

    @Transactional
    public void initializeSystem(SetupInitializeRequest request) {
        validateRequest(request);

        SystemSetting settings = systemSettingRepository.findByIdForUpdate(SystemSetting.SINGLETON_ID)
                .orElseGet(() -> systemSettingRepository.saveAndFlush(
                        SystemSetting.builder()
                                .id(SystemSetting.SINGLETON_ID)
                                .initialized(false)
                                .build()
                ));

        if (Boolean.TRUE.equals(settings.getInitialized())) {
            throw new IllegalStateException("O sistema já foi inicializado.");
        }

        if (userRepository.findByEmail(request.getAdminEmail().trim().toLowerCase()).isPresent()) {
            throw new IllegalArgumentException("Já existe um utilizador com este email.");
        }

        Role adminRole = roleRepository.findByNome("ADMIN")
                .orElseThrow(() -> new IllegalStateException("O papel ADMIN não está configurado."));

        Tenant tenant = Tenant.builder()
                .nome(request.getClientName().trim())
                .email(request.getAdminEmail().trim().toLowerCase())
                .logoDataUrl(normalizeLogo(request.getLogo()))
                .build();
        tenant = tenantRepository.save(tenant);

        User adminUser = User.builder()
                .tenantId(tenant.getId())
                .nome("Administrador")
                .email(request.getAdminEmail().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getAdminPassword()))
                .ativo(true)
                .roles(Set.of(adminRole))
                .build();
        userRepository.save(adminUser);

        // WR-01/IN-01 (126-REVIEW.md): este era o unico dos dois caminhos de criacao de tenant que
        // NAO instanciava moldes nem atribuia o TenantRole ADMIN ao fundador -- ver o comentario
        // em instanciarMoldesEAtribuirAdminFundador. A divergencia face a provisionTenant nao
        // tinha nenhum comentario a justifica-la como deliberada (ao contrario de outras
        // assimetrias documentadas neste codebase), e nada no âmbito do wizard publico exige que
        // ele fique atras -- por isso e tratada aqui como um descuido a corrigir, nao como uma
        // divergencia a documentar e manter.
        instanciarMoldesEAtribuirAdminFundador(tenant.getId(), adminUser, adminRole);

        settings.setInitialized(true);
        settings.setInitializedAt(LocalDateTime.now());
        systemSettingRepository.save(settings);
    }

    /**
     * Caminho de provisionamento gated a {@code PLATAFORMA_ADMIN} (invocado pelo
     * {@code PlatformAdminController} do Plan 04) -- distinto do wizard público
     * {@code /setup/initialize}. Nunca lê nem escreve {@link SystemSettingRepository}: não há
     * gate singleton por desenho, pelo que este método é repetível (pode ser chamado N vezes,
     * criando N tenants), ao contrário de {@link #initializeSystem}. Reutiliza
     * {@link #validateRequest} deliberadamente, para as regras de email/password nunca
     * divergirem entre o wizard público e o caminho de plataforma. O primeiro utilizador do
     * tenant provisionado recebe sempre o papel {@code ADMIN} do próprio tenant -- nunca
     * {@code PLATAFORMA_ADMIN} -- porque é o administrador do escritório, não um operador de
     * plataforma. Devolve a {@link Tenant} guardada (com {@code id} preenchido), ao contrário de
     * {@link #initializeSystem} (que devolve {@code void}), porque o controlador precisa do
     * {@code id}/{@code nome} para construir a resposta 201.
     */
    @Transactional
    public Tenant provisionTenant(SetupInitializeRequest request) {
        validateRequest(request);

        if (userRepository.findByEmail(request.getAdminEmail().trim().toLowerCase()).isPresent()) {
            throw new IllegalArgumentException("Já existe um utilizador com este email.");
        }

        Role adminRole = roleRepository.findByNome("ADMIN")
                .orElseThrow(() -> new IllegalStateException("O papel ADMIN não está configurado."));

        Tenant tenant = Tenant.builder()
                .nome(request.getClientName().trim())
                .email(request.getAdminEmail().trim().toLowerCase())
                .logoDataUrl(normalizeLogo(request.getLogo()))
                .build();
        tenant = tenantRepository.save(tenant);

        User adminUser = User.builder()
                .tenantId(tenant.getId())
                .nome("Administrador")
                .email(request.getAdminEmail().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getAdminPassword()))
                .ativo(true)
                .roles(Set.of(adminRole))
                .build();
        userRepository.save(adminUser);

        instanciarMoldesEAtribuirAdminFundador(tenant.getId(), adminUser, adminRole);

        return tenant;
    }

    /**
     * WR-01 (126-REVIEW.md): instancia o catalogo de moldes do tenant (ver
     * {@link #instanciarMoldes}) e atribui de IMEDIATO o {@link TenantRole} ADMIN ao administrador
     * fundador, aditivamente -- {@code adminUser.roles} (o papel GLOBAL) mantem-se intacto, nunca
     * e trocado nem esvaziado (Decisao 4, 126-CONTEXT.md, reversibilidade).
     *
     * <p>O porque: {@code ResolucaoPapeisService.usaPapeisDeEscritorio} tratava "tenantRoles
     * vazio" como sinonimo de "e o administrador de plataforma" (126-CONTEXT.md Decisao 3), mas
     * essa implicacao nunca foi verdadeira na pratica -- tambem descrevia o administrador
     * fundador de QUALQUER escritorio recem-provisionado, ate ao proximo arranque que corresse
     * {@code MigracaoPapeisEscritorioService.migrar()}. {@code ResolucaoPapeisService} passou a
     * narrar esse gap explicitamente (ver o seu comentario), mas fechar a janela aqui -- na
     * origem, nao so no sintoma -- e o que impede um TenantRole-specific customization futuro
     * (antecipado por {@code TenantRole.java:31-38}) de produzir um mismatch de autoridade real
     * para o fundador de um escritorio novo.
     *
     * <p>Phase 125's Decisao 2 dizia para {@code provisionTenant} continuar a atribuir SO o papel
     * global ao fundador, precisamente para nao criar dois caminhos de resolucao de autoridade em
     * simultaneo -- tenants novos por papel de escritorio, tenants antigos por papel global --
     * enquanto essa fosse exactamente a confusao que a Phase 126 existia para eliminar. A Phase
     * 126 ja eliminou essa confusao: a resolucao de autoridade em producao le SEMPRE
     * {@code ResolucaoPapeisService} (papel de escritorio quando existe, senao global), para TODO
     * tenant, novo ou antigo. A razao que justificava a Decisao 2 desapareceu, por isso a
     * restricao desaparece com ela -- este metodo passa a atribuir o TenantRole ADMIN de imediato.
     *
     * <p>Chamado tanto por {@link #provisionTenant} como por {@link #initializeSystem}
     * (WR-01/IN-01, 126-REVIEW.md): a divergencia entre os dois caminhos -- so provisionTenant
     * instanciava moldes -- nao tinha justificacao escrita, e a leitura de ambos nao encontrou
     * nenhuma razao de desenho para a manter; e tratada aqui como descuido corrigido, nao como
     * assimetria documentada.
     *
     * <p>Se nenhum TenantRole ADMIN existir apos a instanciacao (ex.: ADMIN deixou de ser
     * instanciavel, ou nao havia nenhum molde a instanciar), o fundador fica sem TenantRole --
     * exactamente o mesmo estado "tenantRoles vazio" de hoje, que {@code ResolucaoPapeisService}
     * continua a tratar em seguranca (falha para o lado dos papeis globais, nunca tranca o
     * utilizador fora). Usa deliberadamente a LISTA que {@link #instanciarMoldes} acabou de criar
     * -- nunca uma segunda leitura a {@code tenantRoleRepository} -- para que o caso "sem moldes"
     * continue a nao produzir NENHUMA interacao com esse repositorio (precondicao provada por
     * {@code SetupServiceInstanciacaoMoldesTest.instanciarMoldes_semMoldes_...}).
     */
    void instanciarMoldesEAtribuirAdminFundador(UUID tenantId, User adminUser, Role adminRole) {
        List<TenantRole> instanciados = instanciarMoldes(tenantId);
        instanciados.stream()
                .filter(tr -> adminRole.getNome().equals(tr.getNome()))
                .findFirst()
                .ifPresent(tenantRoleAdmin -> {
                    adminUser.setTenantRoles(new HashSet<>(Set.of(tenantRoleAdmin)));
                    userRepository.save(adminUser);
                });
    }

    /**
     * Instancia, dentro da MESMA transacção de {@link #provisionTenant}, uma cópia própria de
     * cada molde actual para o tenant recém-criado. Quatro invariantes que um refactor futuro
     * não pode quebrar, cada uma com o seu porquê:
     *
     * <p>(1) Corre dentro da MESMA transacção do chamador -- por isso este método não tem
     * {@code @Transactional} próprio. Se a instanciação falhar, a excepção propaga e o
     * {@code @Transactional} de {@link #provisionTenant} rebobina tenant + utilizador ADMIN
     * juntos; um job de instanciação posterior deixaria uma janela com tenant sem papéis.
     *
     * <p>(2) {@code new HashSet<>(molde.getPermissions())} é uma CÓPIA, nunca uma referência
     * partilhada. É isto que torna o papel instanciado um snapshot -- e é a única razão pela
     * qual editar um molde depois de um escritório já ter sido provisionado não muda a cópia
     * desse escritório (MOLD-03). Partilhar a colecção desfaria essa garantia silenciosamente.
     *
     * <p>(3) {@code moldeId} é proveniência histórica, para mostrar a origem no ecrã de moldes e
     * distinguir papel próprio de papel instanciado -- nunca uma referência de leitura viva
     * (não há {@code @ManyToOne} nem navegação JPA de volta a {@link Role}).
     *
     * <p>(4) Este método, por si só, NUNCA toca em nenhum {@code User} -- só cria as linhas de
     * {@link TenantRole} do tenant. A atribuição do TenantRole ADMIN ao administrador fundador é
     * responsabilidade exclusiva de {@link #instanciarMoldesEAtribuirAdminFundador} (WR-01,
     * 126-REVIEW.md), que chama este método e depois faz essa atribuição -- aditivamente, nunca
     * trocando o papel GLOBAL {@code ADMIN} que {@link #provisionTenant}/{@link #initializeSystem}
     * já escreveram. Antes da Phase 126 fechar o cutover de leitura para todos os tenants
     * (126-CONTEXT.md Decisão 1), atribuir o TenantRole aqui teria criado dois caminhos de
     * resolução de autoridade em simultâneo -- essa restrição (Phase 125-CONTEXT.md Decisão 2) já
     * não se aplica: a resolução de autoridade em produção lê sempre
     * {@code ResolucaoPapeisService} para todo tenant, novo ou antigo.
     *
     * <p>(5) Package-private (não {@code private}) desde a Phase 126 Plan 03, exactamente para
     * ser reutilizado por {@link MigracaoPapeisEscritorioService#migrar()} -- a conversão de
     * escritórios já existentes precisa do MESMO laço, não de uma segunda definição capaz de
     * divergir. Não existe, e não deve passar a existir, um segundo laço de instanciação de
     * moldes em nenhum outro ficheiro do pacote.
     *
     * <p>(6) Devolve a lista dos {@link TenantRole} que acabou de instanciar (WR-01,
     * 126-REVIEW.md) -- para que {@link #instanciarMoldesEAtribuirAdminFundador} possa encontrar
     * o TenantRole ADMIN sem uma segunda query a {@code tenantRoleRepository}. Lista vazia,
     * nunca nula, quando não há moldes a instanciar.
     */
    List<TenantRole> instanciarMoldes(UUID tenantId) {
        List<Role> moldes = roleRepository.findAllByInstanciavelTrue();
        List<TenantRole> instanciados = new ArrayList<>();
        for (Role molde : moldes) {
            if (NOME_PAPEL_PLATAFORMA.equals(molde.getNome())) {
                continue;
            }
            TenantRole tenantRole = TenantRole.builder()
                    .tenantId(tenantId)
                    .nome(molde.getNome())
                    .moldeId(molde.getId())
                    .sistema(true)
                    .permissions(new HashSet<>(molde.getPermissions()))
                    .build();
            tenantRoleRepository.save(tenantRole);
            instanciados.add(tenantRole);
        }
        return instanciados;
    }

    private void validateRequest(SetupInitializeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payload de inicialização em falta.");
        }
        if (isBlank(request.getClientName())) {
            throw new IllegalArgumentException("O nome da empresa/cliente é obrigatório.");
        }
        if (isBlank(request.getAdminEmail()) || !EMAIL_PATTERN.matcher(request.getAdminEmail().trim()).matches()) {
            throw new IllegalArgumentException("O email do administrador é inválido.");
        }
        if (isBlank(request.getAdminPassword()) ||
                !STRONG_PASSWORD_PATTERN.matcher(request.getAdminPassword()).matches()) {
            throw new IllegalArgumentException(
                    "A password deve ter no mínimo 8 caracteres, com maiúscula, minúscula, número e caractere especial."
            );
        }
        if (!isBlank(request.getLogo())) {
            String normalizedLogo = request.getLogo().trim();
            if (normalizedLogo.length() > MAX_LOGO_LENGTH) {
                throw new IllegalArgumentException("O logo excede o tamanho máximo permitido.");
            }
            if (!DATA_URL_IMAGE_PATTERN.matcher(normalizedLogo).matches()) {
                throw new IllegalArgumentException("O logo deve ser enviado como imagem base64 válida.");
            }
        }
    }

    private String normalizeLogo(String logo) {
        if (isBlank(logo)) {
            return null;
        }
        return logo.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
