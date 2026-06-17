package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.RbacResponse;
import com.lexcv.dtos.UserResponse;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.User;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        List<User> users = userRepository.findByTenantId(principal.getTenantId());
        // Map to UserResponse
        List<UserResponse> responses = users.stream().map(u -> {
            Set<String> roles = u.getRoles().stream().map(Role::getNome).collect(Collectors.toSet());
            Set<String> permissions = u.getRoles().stream()
                    .flatMap(r -> r.getPermissions().stream())
                    .map(Permission::getNome)
                    .collect(Collectors.toSet());
            u.getPermissions().forEach(permissions::add);

            return UserResponse.builder()
                    .id(u.getId())
                    .tenant_id(u.getTenantId())
                    .nome(u.getNome())
                    .email(u.getEmail())
                    .telefone(u.getTelefone())
                    .avatar_url(u.getAvatarUrl())
                    .roles(roles)
                    .permissions(permissions)
                    .ativo(u.getAtivo())
                    .build();
        }).collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("nome") || !body.containsKey("email") || !body.containsKey("password") || !body.containsKey("roles")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nome, email, password e roles são obrigatórios."));
        }

        String password = (String) body.get("password");
        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "A password deve ter no mínimo 8 caracteres, uma maiúscula, uma minúscula, um número e um caractere especial."));
        }

        String email = (String) body.get("email");
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Já existe um utilizador registado com este endereço de email."));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        List<?> rolesList = (List<?>) body.get("roles");
        Set<Role> roles = new HashSet<>();
        for (Object rObj : rolesList) {
            String roleName = (String) rObj;
            roleRepository.findByNome(roleName).ifPresent(roles::add);
        }

        if (roles.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Pelo menos uma role válida é obrigatória."));
        }

        List<?> permsList = body.containsKey("permissions") ? (List<?>) body.get("permissions") : Collections.emptyList();
        Set<String> permissions = new HashSet<>();
        for (Object pObj : permsList) {
            permissions.add((String) pObj);
        }

        User user = User.builder()
                .tenantId(principal.getTenantId())
                .nome((String) body.get("nome"))
                .email(email)
                .passwordHash(passwordEncoder.encode((String) body.get("password")))
                .roles(roles)
                .permissions(permissions)
                .ativo(body.get("ativo") == null || (Boolean) body.get("ativo"))
                .telefone(body.containsKey("telefone") ? (String) body.get("telefone") : "")
                .avatarUrl(body.containsKey("avatar_url") ? (String) body.get("avatar_url") : "")
                .build();

        user = userRepository.save(user);

        UserResponse response = UserResponse.builder()
                .id(user.getId())
                .tenant_id(user.getTenantId())
                .nome(user.getNome())
                .email(user.getEmail())
                .roles(user.getRoles().stream().map(Role::getNome).collect(Collectors.toSet()))
                .permissions(permissions)
                .ativo(user.getAtivo())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        User user = userRepository.findById(id).orElse(null);
        if (user == null || !user.getTenantId().equals(principal.getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Utilizador não encontrado"));
        }

        if (body.containsKey("email")) {
            String email = (String) body.get("email");
            Optional<User> existing = userRepository.findByEmail(email);
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Já existe outro utilizador com este email."));
            }
            user.setEmail(email);
        }

        if (body.containsKey("nome")) user.setNome((String) body.get("nome"));
        if (body.containsKey("telefone")) user.setTelefone((String) body.get("telefone"));
        if (body.containsKey("avatar_url")) user.setAvatarUrl((String) body.get("avatar_url"));
        if (body.containsKey("ativo")) user.setAtivo((Boolean) body.get("ativo"));

        if (body.containsKey("password") && ((String) body.get("password")).trim().length() > 0) {
            String password = (String) body.get("password");
            if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$")) {
                return ResponseEntity.badRequest().body(Map.of("message", "A password deve ter no mínimo 8 caracteres, uma maiúscula, uma minúscula, um número e um caractere especial."));
            }
            user.setPasswordHash(passwordEncoder.encode(password));
        }

        if (body.containsKey("roles")) {
            List<?> rolesList = (List<?>) body.get("roles");
            Set<Role> roles = new HashSet<>();
            for (Object rObj : rolesList) {
                String roleName = (String) rObj;
                roleRepository.findByNome(roleName).ifPresent(roles::add);
            }
            if (!roles.isEmpty()) {
                user.setRoles(roles);
            }
        }

        if (body.containsKey("permissions")) {
            List<?> permsList = (List<?>) body.get("permissions");
            Set<String> permissions = new HashSet<>();
            for (Object pObj : permsList) {
                permissions.add((String) pObj);
            }
            user.setPermissions(permissions);
        }

        user = userRepository.save(user);

        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        if (principal.getUserId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Não é permitido apagar a sua própria conta de utilizador administrador."));
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null || !user.getTenantId().equals(principal.getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Utilizador não encontrado"));
        }

        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Utilizador removido com sucesso!"));
    }

    @GetMapping("/rbac")
    public ResponseEntity<?> getRbac() {
        List<Role> roles = roleRepository.findAll();
        Map<String, List<String>> rolePermissions = new HashMap<>();

        for (Role role : roles) {
            List<String> perms = role.getPermissions().stream()
                    .map(Permission::getNome)
                    .collect(Collectors.toList());
            rolePermissions.put(role.getNome(), perms);
        }

        List<RbacResponse.PermissionDefDto> systemPermissions = Arrays.asList(
                new RbacResponse.PermissionDefDto("clientes:view", "Visualizar Clientes", "Ver lista e detalhes de clientes", "Clientes"),
                new RbacResponse.PermissionDefDto("clientes:edit", "Gerir Clientes", "Criar, editar e apagar clientes", "Clientes"),
                new RbacResponse.PermissionDefDto("processos:view", "Visualizar Processos", "Ver lista e detalhes de processos judiciais", "Processos"),
                new RbacResponse.PermissionDefDto("processos:edit", "Gerir Processos", "Criar, editar, alterar fases e apagar processos", "Processos"),
                new RbacResponse.PermissionDefDto("agenda:view", "Visualizar Agenda", "Ver calendário e prazos/eventos", "Agenda"),
                new RbacResponse.PermissionDefDto("agenda:edit", "Gerir Agenda", "Criar, editar e concluir eventos/prazos", "Agenda"),
                new RbacResponse.PermissionDefDto("documentos:view", "Visualizar Documentos", "Ver e descarregar documentos", "Documentos"),
                new RbacResponse.PermissionDefDto("documentos:edit", "Gerir Documentos", "Carregar e apagar documentos", "Documentos"),
                new RbacResponse.PermissionDefDto("financeiro:view", "Visualizar Financeiro", "Ver honorários, pagamentos e conta corrente", "Financeiro"),
                new RbacResponse.PermissionDefDto("financeiro:edit", "Gerir Financeiro", "Lançar honorários, pagamentos e gerir conta corrente", "Financeiro"),
                new RbacResponse.PermissionDefDto("rbac:manage", "Gerir Permissões (RBAC)", "Alterar regras de acesso globais por função", "Administração"),
                new RbacResponse.PermissionDefDto("users:manage", "Gerir Utilizadores", "Criar, ativar/desativar, e configurar utilizadores", "Administração")
        );

        RbacResponse response = RbacResponse.builder()
                .rolePermissions(rolePermissions)
                .systemPermissions(systemPermissions)
                .build();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/rbac")
    public ResponseEntity<?> updateRbac(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("rolePermissions")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mapeamento rolePermissions é obrigatório"));
        }

        Map<?, ?> newRolePermissions = (Map<?, ?>) body.get("rolePermissions");

        for (Map.Entry<?, ?> entry : newRolePermissions.entrySet()) {
            String roleName = (String) entry.getKey();
            if ("ADMIN".equals(roleName)) {
                continue; // Protection: Admin is immutable
            }

            Role role = roleRepository.findByNome(roleName).orElse(null);
            if (role != null) {
                List<?> permsList = (List<?>) entry.getValue();
                Set<Permission> permissions = new HashSet<>();
                for (Object pObj : permsList) {
                    String pName = (String) pObj;
                    permissionRepository.findByNome(pName).ifPresent(permissions::add);
                }
                role.setPermissions(permissions);
                roleRepository.save(role);
            }
        }

        return ResponseEntity.ok(Map.of("message", "Permissões de perfis (RBAC) atualizadas com sucesso!"));
    }
}
