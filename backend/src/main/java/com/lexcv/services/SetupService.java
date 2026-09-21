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

        instanciarMoldes(tenant.getId());

        return tenant;
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
     * <p>(4) Esta instanciação nunca toca em {@code adminUser.roles}: o administrador inicial
     * continua a receber o papel GLOBAL {@code ADMIN} (ver {@link #provisionTenant}). Trocar
     * para o papel instanciado aqui criaria dois caminhos de resolução de autoridade em
     * simultâneo -- tenants novos por papel de escritório, tenants antigos por papel global --
     * precisamente o que a Phase 126 existe para eliminar de uma vez.
     *
     * <p>(5) Package-private (não {@code private}) desde a Phase 126 Plan 03, exactamente para
     * ser reutilizado por {@link MigracaoPapeisEscritorioService#migrar()} -- a conversão de
     * escritórios já existentes precisa do MESMO laço, não de uma segunda definição capaz de
     * divergir. Não existe, e não deve passar a existir, um segundo laço de instanciação de
     * moldes em nenhum outro ficheiro do pacote.
     */
    void instanciarMoldes(UUID tenantId) {
        List<Role> moldes = roleRepository.findAllByInstanciavelTrue();
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
        }
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
