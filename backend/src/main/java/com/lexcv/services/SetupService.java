package com.lexcv.services;

import com.lexcv.dtos.SetupInitializeRequest;
import com.lexcv.models.Role;
import com.lexcv.models.SystemSetting;
import com.lexcv.models.Tenant;
import com.lexcv.models.User;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.SystemSettingRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
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

    private final SystemSettingRepository systemSettingRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

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

        return tenant;
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
