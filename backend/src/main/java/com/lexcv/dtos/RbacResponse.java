package com.lexcv.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RbacResponse {
    private Map<String, List<String>> rolePermissions;
    private List<PermissionDefDto> systemPermissions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionDefDto {
        private String key;
        private String nome;
        private String descricao;
        private String modulo;
    }
}
