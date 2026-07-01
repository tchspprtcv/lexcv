---
phase: 67-elabora-o-e-versionamento
verified: 2026-07-01T00:00:00Z
status: human_needed
score: 8/8 must-haves verified (static analysis) — human verification required for live UI/upload flow
overrides_applied: 0
human_verification:
  - test: "Como advogado responsável de uma solicitação EM_ELABORACAO, abrir /pareceres/[id] e confirmar que o card 'Nova Versão' aparece"
    expected: "Card visível com textarea 'Resumo da versão' e FileDropZone 'Anexo (obrigatório)'"
    why_human: "Depende de renderização condicional em runtime combinando dados de sessão (usePermissions) e dados da solicitação (useParecer) — não pode ser confirmado por análise estática de código isolada, requer sessão autenticada real"
  - test: "Submeter o formulário sem selecionar ficheiro"
    expected: "Submissão bloqueada, mensagem 'É necessário anexar um ficheiro para submeter esta versão.' exibida, nenhum POST enviado à rede"
    why_human: "Comportamento de validação client-side em runtime (zodResolver + RHF) — a lógica foi confirmada estaticamente no schema, mas o comportamento de bloqueio de submissão real (nenhuma rede disparada) requer execução no browser"
  - test: "Submeter com resumo (10+ caracteres) + anexo válido"
    expected: "Barra de progresso aparece durante upload, toast de sucesso 'Nova versão submetida com sucesso.', nova versão aparece no topo/fim da timeline imediatamente sem reload de página"
    why_human: "Comportamento de rede real (XHR progress events), invalidação de cache do TanStack Query e re-render da timeline só são observáveis em execução real, não por grep"
  - test: "Como utilizador sem pareceres:edit, ou que não é advogado responsável nem ADMIN, abrir a mesma solicitação"
    expected: "Card 'Nova Versão' não aparece (omitido silenciosamente, sem botão desativado)"
    why_human: "Depende de estado de sessão/roles real; a lógica condicional foi verificada estaticamente (showNovaVersaoForm), mas a ausência visual completa do card precisa de confirmação em runtime com um utilizador de facto sem permissão"
  - test: "Abrir uma solicitação com status CONCLUIDO"
    expected: "Banner 'Parecer já entregue' / 'Não é possível submeter novas versões após a entrega final.' em vez do formulário"
    why_human: "Requer uma solicitação real em estado CONCLUIDO (nenhuma existe ainda no fluxo, já que Phase 68/entrega não está implementada) — condicional confirmada estaticamente (isConcluido), mas visual real não testável sem dados de sessão ao vivo"
  - test: "Submeter duas versões sucessivas com o mesmo ficheiro (repeat submission)"
    expected: "Segunda submissão funciona corretamente (FileDropZone remontada via fileInputKey, sem estado de ficheiro 'preso' da submissão anterior)"
    why_human: "Comportamento de remount de componente React e reset de <input type=file> nativo só é observável interativamente (WR-01 fix)"
---

# Phase 67: Elaboração e Versionamento Verification Report

**Phase Goal:** O advogado responsável consegue efetivamente elaborar o parecer através da aplicação, submetendo versões sucessivas e imutáveis com anexo (a cláusula "mantido informado"/NOTF-06 foi descoped do milestone v1 e é ignorada nesta verificação, por decisão explícita registada em ROADMAP.md).
**Verified:** 2026-07-01
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Advogado responsável ou ADMIN vê um formulário "Nova Versão" no detalhe de uma solicitação não CONCLUIDO | ✓ VERIFIED (static) | `pareceres/[id]/page.tsx:140-141,276-277` — `showNovaVersaoForm = !permissions.isLoading && canEditPareceres && isResponsavelOuAdmin && !isConcluido`; renders `<NovaVersaoForm>`. Live rendering needs human check. |
| 2 | Utilizador sem `pareceres:edit`, ou que não é advogado responsável nem ADMIN, não vê o formulário | ✓ VERIFIED (static) | Same conditional; `else null` branch (line 278) omits the card entirely — no disabled button rendered ("no dead buttons" principle honored). Backend enforces the same via 403 in `ParecerController.createVersao:446-449` (isAdmin \|\| isResponsavel), so UI omission is defense-in-depth, not the sole gate. |
| 3 | O formulário bloqueia a submissão se nenhum anexo for fornecido | ✓ VERIFIED (static) | `pareceres.ts:57-60` — `file: fileListSchema.refine((files) => files.length === 1, "É necessário anexar um ficheiro...")`; zodResolver wired via `useForm({ resolver: zodResolver(parecerVersaoCreateFormSchema) })` at `page.tsx:294-296`. Blocking behavior at runtime needs human check. |
| 4 | O formulário bloqueia a submissão se o resumo (conteudo) tiver menos de 10 caracteres | ✓ VERIFIED (static) | `pareceres.ts:46-56` — `superRefine` rejects `val.length === 0 \|\| val.length < 10` with the exact UI-SPEC copy. |
| 5 | Após submissão bem-sucedida, a nova versão aparece imediatamente na timeline sem navegar para fora da página | ✓ VERIFIED (static) | `use-pareceres.ts:136-142` invalidates all 3 query keys (`versoes`, `detail`, `list`) on success; `page.tsx` submit handler (306-310) does not call `router.push`/`navigate`, stays on page, resets form. Live re-render/timeline-append needs human check. |
| 6 | O upload mostra barra de progresso reutilizando o padrão do módulo Documentos | ✓ VERIFIED (static) | `use-pareceres.ts:104-134` replicates XHR+FormData+`xhr.upload.onprogress` shape of `useUploadDocumentoComProgresso`; `page.tsx:358-375` renders the same progress-bar markup (`bg-neutral-200`/`bg-blue-600` track+fill) as `documentos/novo/page.tsx`. |
| 7 | Solicitação com status CONCLUIDO mostra um banner só-leitura em vez do formulário | ✓ VERIFIED (static) | `page.tsx:265-275` — `isConcluido` branch renders the exact UI-SPEC copy ("Parecer já entregue" / "Não é possível submeter novas versões após a entrega final."), no button/input present. |
| 8 | Nenhuma versão existente ganha affordance de editar/eliminar | ✓ VERIFIED (static) | Timeline rendering (`page.tsx:213-254`, unchanged from Phase 65) has no edit/delete button anywhere. Backend confirms: `grep` for `@PutMapping`/`@DeleteMapping` on `.../versoes...` in `ParecerController.java` finds only `/{id}/versoes/{versaoId}/aprovar` (an approval action, out of Phase 67/68 scope, not an edit/delete of version content) — no mutation endpoint for existing versions exists at all. |

**Score:** 8/8 truths verified via static analysis. All 8 require a live-browser pass to confirm actual runtime behavior (see Human Verification below) — no dev server was available in this verification pass.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/schemas/pareceres.ts` | `parecerVersaoCreateFormSchema` with conteudo (min 10) + file (required FileList) | ✓ VERIFIED | Present at lines 45-63, matches PLAN spec exactly (superRefine pattern copied from sibling `descricao` field, single-message variant as explicitly requested). |
| `web/src/hooks/use-pareceres.ts` | `useCreateParecerVersao` — XHR multipart mutation with progress + 3-key invalidation cascade | ✓ VERIFIED | Present at lines 91-144. XHR (not apiFetch), `withCredentials=true`, `onprogress`, `xhr.timeout=60000` + `ontimeout` handler (WR-03 fix applied), invalidates exactly the 3 documented query keys. |
| `web/src/app/(dashboard)/pareceres/[id]/page.tsx` | Section "Nova Versão" with RBAC gate + instance check + FileDropZone + progress bar | ✓ VERIFIED | `NovaVersaoForm` component (lines 285-393) wired into `ParecerDetailContent`; `CardTitle` uses `className="text-lg font-bold"` (UI-SPEC mandatory fix applied, line 325) — the exact gap flagged as Phase 66's Top-3 finding is not repeated here. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `pareceres/[id]/page.tsx` | `useCreateParecerVersao` | hook mutation call | ✓ WIRED | `page.tsx:290-292` calls `useCreateParecerVersao(solicitacaoId, {onProgress})`; `versaoUpload.mutateAsync` invoked in submit handler (line 304). |
| `use-pareceres.ts` | `POST /pareceres/solicitacoes/{id}/versoes` | XMLHttpRequest multipart | ✓ WIRED | `use-pareceres.ts:105-108` — exact path matches `ParecerController.createVersao`'s `@PostMapping(value = "/{solicitacaoId}/versoes", consumes = MULTIPART_FORM_DATA_VALUE)` mapping (mounted under `/pareceres/solicitacoes` base path); field names `conteudo`/`file` match `@RequestParam` names exactly (backend lines 432-433). |
| `pareceres/[id]/page.tsx` | `parecerVersaoCreateFormSchema` | zodResolver | ✓ WIRED | `page.tsx:3,25-27,295` — imported and passed to `useForm({ resolver: zodResolver(parecerVersaoCreateFormSchema) })`. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| Version timeline (`versoes.data`) | `useParecerVersoes(id)` → `apiFetch<ParecerVersao[]>(.../versoes)` | Real GET against backend-persisted `ParecerVersao` rows (backend confirmed to `save()` via JPA repository, not a static stub) | Yes | ✓ FLOWING |
| Nova Versão form → POST result | `versaoUpload.mutateAsync` response resolved from `xhr.responseText` (backend returns the persisted `ParecerVersao`, 201) | Backend `createVersao` persists via `parecerVersaoRepository.save(versao)` (line 486) with real `numeroVersao` sequencing (`findMaxNumeroVersaoBySolicitacaoId`, line 473) | Yes | ✓ FLOWING |
| `me` / RBAC instance check | `permissions.data` from `usePermissions()` (spreads `useMe()`) | Real `/auth/me` network call, not hardcoded | Yes | ✓ FLOWING |

No hollow props, no hardcoded empty-array/static returns detected in the modified files.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| PARV-05 | 67-01-PLAN.md | Advogado responsável pode criar nova versão via formulário com resumo (10+ chars) e anexo obrigatório | ✓ SATISFIED (static) | Schema + form wiring confirmed; runtime confirmation pending (human_verification). |
| PARV-06 | 67-01-PLAN.md | Upload de anexo reutiliza componente/padrão de Documentos (progress bar, drag-drop, MinIO-backed) | ✓ SATISFIED (static) | `FileDropZone` reused verbatim (not duplicated); XHR+progress pattern replicated per-domain as explicitly directed by CONTEXT.md/PLAN (not a violation — plan explicitly called for replication, not import, due to differing endpoint/fields). No new upload primitive introduced (UI-SPEC "Registry Safety" / Out-of-Scope table confirms this decision). |

No orphaned requirements: both PARV-05 and PARV-06 map to Phase 67 per REQUIREMENTS.md traceability table, and both are addressed in `67-01-PLAN.md`'s `requirements` frontmatter.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | No TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER markers found in the 3 modified files | — | None — clean |
| — | — | No `return null`/`return {}`/empty-handler stubs found in new code | — | None — clean |

`pnpm lint` reports 5 pre-existing errors / 18 warnings, all in files untouched by this phase (`use-toast.ts`, `dashboard-shell.tsx`, `pareceres/nova/page.tsx` from Phase 66, `settings/page.tsx`). None reference the 3 files modified by Phase 67. This matches the SUMMARY's claim and was independently re-confirmed by grepping the phase's own file paths out of the lint output.

`tsc --noEmit` passes with zero errors across the whole `web/` project.

Code review (`67-REVIEW.md`) found 0 Critical, 3 Warning findings — all 3 were subsequently fixed per `67-REVIEW-FIX.md` (commits `c78d449`, `979cf8e`, `681bdd7`), independently confirmed present in the current file content (fileInputKey remount, `!permissions.isLoading` gate, `xhr.timeout`/`ontimeout`).

### Human Verification Required

See YAML frontmatter `human_verification` list. Six items, all requiring a live browser session against a running dev server (none was available in this verification pass, per task instructions). All underlying logic was confirmed correct via static analysis (schema validation rules, RBAC conditionals, query invalidation, endpoint field names matching backend `@RequestParam`s exactly, absence of any version edit/delete endpoint). The remaining risk is purely in runtime behavior (React re-render timing, XHR progress events, toast display, visual banner correctness) which cannot be verified without executing the code.

### Gaps Summary

No code-level gaps found. All 8 derived observable truths, all 3 required artifacts, and all 3 key links pass static verification at all three levels (exists, substantive, wired) plus a Level 4 data-flow trace confirming no hollow/static data paths. The backend contract (`ParecerController.createVersao`) was independently read and cross-checked against the frontend hook's field names, HTTP method, path, and RBAC/instance-check logic — all match exactly. No stub patterns, no debt markers, no lint regressions attributable to this phase's files.

The phase is blocked from a clean `passed` status only because a live browser/dev-server pass was not available in this environment, per the verification task's explicit instruction to mark live-UI-dependent items as `human_verification` rather than assume success. This is a process gate, not a code defect — recommend a human run through the 6 listed scenarios (ideally with a real EM_ELABORACAO solicitação, a non-privileged user session, and if possible a CONCLUIDO solicitação once available) before considering Phase 67 fully closed.

---

_Verified: 2026-07-01_
_Verifier: Claude (gsd-verifier)_
