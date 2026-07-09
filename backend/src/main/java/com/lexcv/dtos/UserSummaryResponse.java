package com.lexcv.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * Minimal, non-admin-safe user projection for tenant-scoped "assign to" pickers
 * (e.g. reatribuir responsável, novo prazo responsável) — deliberately excludes
 * roles/permissions/email/telefone/avatar_url, unlike UserResponse (admin-only).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {
    private UUID id;
    private String nome;
    // WR-01 (Phase 87 code review, iteration 2): exposes whether the user can
    // still log in, so "assign to" pickers can filter out deactivated accounts
    // while historical name-lookup consumers of this same endpoint keep working.
    private Boolean ativo;
}
