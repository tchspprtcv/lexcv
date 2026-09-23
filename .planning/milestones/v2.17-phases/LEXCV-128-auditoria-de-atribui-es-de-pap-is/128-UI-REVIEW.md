# Phase 128 — UI Review

**Audited:** 2026-09-22
**Baseline:** `128-UI-SPEC.md` (approved design contract)
**Screenshots:** not captured (no dev server / no database in this session — code-only audit per task constraints)
**Advisory:** this review does not block the milestone.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | Every sentence template, fallback string, and copy element matches the contract verbatim, including the WR-02 rename-fallback fix. |
| 2. Visuals | 2/4 | The contract's third `CardTitle` ("Auditoria de Atribuições de Papéis") never shipped, and the two titles that did ship ("Filtros"/"Resultados") render without the `text-xl font-semibold` the Typography table promises for them — the tab has no on-page confirmation of what screen you're on. |
| 3. Color | 4/4 | No accent-blue CTA, no destructive-red on mutation (there is none); the one red instance is the spec's own documented error state. |
| 4. Typography | 3/4 | Sentence-level typography (the two-size/two-weight discipline) is exact; the `CardTitle` sizing gap from Pillar 2 is a genuine Typography-table violation. |
| 5. Spacing | 4/4 | Every declared token and exception (`w-40`, `space-y-1`, `py-3`, `gap-4 sm:grid-cols-2`) is reproduced exactly; no arbitrary values beyond the two the spec pre-authorized. |
| 6. Experience Design | 3/4 | Loading/error/empty/results states, pager clamp, and the Read-Only Guarantee are all solid and independently verified by the structural gate — but the "Utilizador alvo" filter silently degrades to unusable (no options, no explanation) for an `rbac:manage`-only admin who lacks `users:manage`, inside the exact audience the tab is gated for. |

**Overall: 20/24**

---

## Top 3 Priority Fixes

1. **Missing/undersized card titles inside the tab** — a `hasRbacManage` admin can land on "Auditoria" and see two cards labeled "Filtros" / "Resultados" with no larger heading anywhere confirming they're looking at the audit log (the contract's third `CardTitle`, "Auditoria de Atribuições de Papéis", was never implemented) — `web/src/app/(dashboard)/settings/auditoria-tab.tsx:129-131,175-177`. Fix: either add the missing `CardTitle`/heading the Typography table promises, or (if the tab-strip label is judged sufficient) strike that row from `128-UI-SPEC.md`'s Typography table so the contract stops asserting something that was never built.

2. **`CardTitle` sizing inconsistent with the sibling RBAC tab** — "Filtros" and "Resultados" render with no `className` at all (`auditoria-tab.tsx:130,176`), so they inherit whatever ambient font-size applies instead of the declared 20px/`text-xl`. Switching from "Controlo de Acesso (RBAC)" (whose `CardTitle` at `settings/page.tsx:1085` *does* carry `text-xl font-semibold`) to "Auditoria" produces a visibly smaller card header on an otherwise identical layout, one tab strip over. Fix: add `className="text-xl font-semibold"` to both `CardTitle` usages in `auditoria-tab.tsx`, matching the RBAC tab's own convention the spec cited (and correcting the same silent gap already present in the `notificacoes/page.tsx` precedent this file copied from).

3. **"Utilizador alvo" filter silently empties out for part of the tab's own gated audience** — `GET /admin/users` requires `users:manage`, not `rbac:manage`; the tab is gated on `hasRbacManage` alone (per Decision 5), so an admin with `rbac:manage` but not `users:manage` opens the tab, sees the "Utilizador alvo" combobox, and finds it offers only "Todos os utilizadores" with no other rows and no explanation (`auditoria-tab.tsx:57-66,83-89`). This is a well-reasoned, well-commented choice to avoid a 403 toast, but it leaves AUDT-01/02's "filter by target user" baseline unreachable — with no on-screen signal — for a real subset of the tab's own permitted users. Fix: when `!hasUsersManage`, either disable the combobox with a `title`/inline note explaining why, or fall back to a lighter-weight endpoint that only needs `rbac:manage` to resolve target names.

---

## Detailed Findings

### Pillar 1: Copywriting (4/4)

- Every string in the Copywriting Contract table is reproduced verbatim: `"Filtros"`, `"Resultados"`, `"Utilizador alvo"`, `"Todos os utilizadores"`, `"Pesquisar utilizador por nome..."`, `"Papel"`, `"Todos os papéis"`, `"Limpar filtros"`, `"A carregar..."`, the exact error copy, both empty-state variants, both category badge labels, and `"Página {n} de {total}"` — `auditoria-tab.tsx:140,154,167,176,180,183,189,193,199-202,236`.
- All eight sentence templates in `web/src/lib/auditoria-rbac.ts:57-120` match the UI-SPEC §3 table character-for-character, including punctuation (the em-dash spacing in `" — utilizador eliminado."`, `auditoria-rbac.ts:114-118`) and the deliberately non-bold "A plataforma" subject for the provisioning template (`auditoria-rbac.ts:90-97`), which correctly mirrors the UI-SPEC table's own markdown (only `{papel}`/`{alvo}` are bolded in that row, not "A plataforma").
- **Null-name fallback, verified as shipped:** missing actor → `"um administrador removido"` (`auditoria-rbac.ts:23,38`); missing target → `"um utilizador removido"` (`:24,42`); missing role on a create/delete/permission/assign/unassign event → `"um papel removido"` (`:25,46`). The rename-specific fallback is **correctly distinct** — `"nome desconhecido"` (`:34,50,54`) rather than reusing `"um papel removido"` — with a code comment (`:26-33`) explicitly documenting the WR-02 fix and why conflating them would falsely imply the role was deleted. This is the exact defect flagged in code review, and it is fixed as claimed.
- Unknown `acao` never leaks a raw code: `auditoriaEventoToSentence` checks `Object.prototype.hasOwnProperty` against the `CONSTRUTORES` map and falls back to a fixed placeholder string, `"Evento de auditoria não reconhecido."` (`auditoria-rbac.ts:129-135`), never interpolating the raw value. Wording differs slightly from the UI-SPEC's example phrase ("evento desconhecido") but the spec explicitly left this open ("should throw or return a clearly-marked … placeholder") — not a deviation.
- `web/scripts/verify-auditoria-rbac.mjs` independently pins the no-raw-`acao`-render assertion (`sem-acao-bruta-no-ecra`) and the three fallback strings (`fallbacks-de-nome-nulo`); both pass (`11/11 PASS`, run during this audit).

### Pillar 2: Visuals (2/4)

- **The promised section heading never shipped.** UI-SPEC's Typography table (line 75) lists three `CardTitle` instances for this composed screen: "Auditoria de Atribuições de Papéis", "Filtros", "Resultados". Only the last two exist in `auditoria-tab.tsx` (`:130,176`) — there is no top-level title anywhere in the tab confirming to the user they're on the audit log, distinct from the small 14px active-tab label in the strip above. The Screen Structure §2 ASCII wireframe (UI-SPEC lines 150-183) also never included this heading, so the gap traces back to an internal inconsistency in the contract itself between its own wireframe and its own Typography table — but the net effect on the shipped screen is the same: the promised element is absent.
- The list *is* the correct focal point once a user is oriented (matches the spec's stated intent), and there's no competing visual noise — no stray icons, no decorative elements. The concern here is orientation/confirmation, not competition for attention.
- Icon-only affordances are correctly avoided: the only icon (`X` on "Limpar filtros") is always paired with visible text, never icon-only (`auditoria-tab.tsx:166-168,192-194`).
- No `DropdownMenu`/kebab, no per-row action surface — confirmed both by manual read and by the `sem-dropdown-menu` structural-gate assertion.

### Pillar 3: Color (4/4)

- No accent-blue is authored as a CTA/decorative choice by this phase — there is no primary button, no save affordance anywhere in `auditoria-tab.tsx`, consistent with the "read-only, no CTA" contract.
- Only two blue/red hits in the file: `focus-visible:ring-blue-500` on the `<select>` (`auditoria-tab.tsx:150`) — a functional keyboard-focus indicator, reused verbatim from the `notificacoes/page.tsx:150` precedent the spec names, not a decorative accent choice — and `text-red-600` on the error message (`:182`), which is exactly what the spec's own Screen Structure §2 (line 172) specifies for that state. (Note: the Color table's blanket "no red anywhere" line technically contradicts the Screen Structure's explicit `text-red-600` error copy a few sections later — a self-inconsistency in the contract itself, not a shipped defect; using red for an error message is correct UX regardless.)
- One inherited arbitrary hex value, `dark:bg-[#020617]` on the `<select>` (`:150`), copied byte-for-byte from the `notificacoes/page.tsx` precedent the spec directs reuse from — pre-existing codebase debt, not new drift introduced by this phase.
- No destructive/mutating action exists anywhere in the file (confirmed by the structural gate's `sem-usemutation` and `sem-metodos-mutadores` assertions, both PASS).

### Pillar 4: Typography (3/4)

- The phase's own newly-authored prose (sentence text, filter/empty/error copy) is exactly two sizes and effectively one weight it controls directly: `text-sm` / `text-xs` only (`grep` of `auditoria-tab.tsx` finds only these two size tokens, at lines 150,180,182,188,235,286,290,298), and `font-semibold` only on the actor/target/role highlight spans (`:270,292` in file, i.e. the `AuditoriaRow` segment rendering) — matches the contract's discipline precisely.
- The gap: `CardTitle` for "Filtros"/"Resultados" carries no `className` (`:130,176`), so it does not get the `text-xl` the Typography table declares for it (`components/ui/card.tsx:28-36` confirms the primitive itself applies `font-semibold leading-none tracking-tight` with **no size class** — Tailwind's preflight resets heading font-size to `inherit`, so absent an explicit size class these titles render at whatever the ambient/body size is, not 20px). This is the same Pillar 2 finding, restated as a literal Typography-table miss rather than a structural-completeness miss.
- Worth noting for the planner: this isn't unique to Phase 128 — `notificacoes/page.tsx:138,195` has the identical unstyled-`CardTitle` pattern, which is exactly what `auditoria-tab.tsx` copied "verbatim in shape." The UI-SPEC's own citation of `settings/page.tsx:1085` (`<CardTitle className="text-xl font-semibold">`) as "the existing per-usage convention" is true for the RBAC tab but not true for the `notificacoes` precedent this screen was told to copy — the spec cited the wrong precedent for this specific element.

### Pillar 5: Spacing (4/4)

- Filter grid: `CardContent className="grid gap-4 sm:grid-cols-2"` (`:132`) — exact match to spec §4.
- Row rhythm: `py-3` per row (`:285`), `divide-y divide-slate-100 dark:divide-slate-800` (`:209`) — matches spec's Prazos-list-derived precedent.
- Sentence/detail-subline gap: `space-y-1` (`:289`) — matches the spec's explicit correction away from the earlier `space-y-0.5` draft.
- Timestamp column: `w-40 shrink-0` (`:286`) — matches the spec's named exception exactly.
- No arbitrary spacing values beyond the two the spec pre-authorized (`w-40`, `space-y-1`); no stray `p-`/`m-`/`gap-` values outside the declared scale anywhere in the file.

### Pillar 6: Experience Design (3/4)

- State coverage matches the spec's ordering precisely: `isPending` → `isError` → empty-with-filters → empty-true-zero → results+pager (`:179-260`), with the pager only rendered when `totalPages > 1` (`:218`), matching the `notificacoes/page.tsx:236` guard.
- Filter-change-resets-page and clamp-on-shrink are both correctly implemented (`:107-115`, `:97-103`), mirroring the `notificacoes/page.tsx:80-107` pattern the spec cites, generalized correctly to two filters.
- Read-Only Guarantee (AUDT-04) is thoroughly honored: no edit/delete/kebab-menu affordance anywhere; `"Limpar filtros"` only touches `utilizadorAlvoId`/`papelId`/`page` state (`:120-124`), never the query itself, and is explicitly commented as such; the hook (`useOfficeRbacAuditoria`, `use-admin.ts:106-121`) is a pure `useQuery`, no `useMutation` anywhere in the read path. All independently confirmed via `verify-auditoria-rbac.mjs` (11/11 PASS, re-run during this audit) which asserts, at the literal-substring level, the absence of `useMutation`, `DropdownMenu`, `Trash`, `Pencil`, and any `method: "DELETE"/"PUT"/"POST"/"PATCH"` inside this tab's files.
- Phase 127's work is intact and unaffected by this addition: the module-level draft cache (`rbacRascunhoPermissoes`/`rbacRascunhoTocados`, `settings/page.tsx:857-858`), the `beforeunload` guard (`:940-948`), and the dirty-count label (`rotuloPapeisPorGravar(papeisAlterados.length)`, `:1103`) are all present and unmodified — confirmed by direct read and by the structural gate's `wiring-aditivo-em-page` assertion (PASS).
- **Gap:** the "Utilizador alvo" filter is wired to `useAdminUsers({ enabled: hasUsersManage })` (`:66`), where `hasUsersManage = can.manage("users")` — a *different* permission than the `hasRbacManage` gate that controls whether the tab is visible at all. A user who can see the tab (has `rbac:manage`) but lacks `users:manage` gets a combobox that only ever offers `"Todos os utilizadores"` (`:83-89`), silently, with no disabled state, no tooltip, and no copy explaining the limitation — a real, documented (in code comments) but user-invisible degradation of one of the two baseline filters AUDT-01/02 call for.
- Accessibility of controls: both filters are correctly `<Label htmlFor>`-paired with matching `id`s (`:134-143,147-149`); the `Combobox` trigger carries `role="combobox"` + `aria-expanded` via the underlying Radix `Popover`/`Command` primitives (`components/shared/combobox.tsx:89-95`); the plain `<select>` needs no additional ARIA. Pager buttons render visible `"Anterior"`/`"Seguinte"` text (not icon-only) with `aria-disabled` correctly toggled at both ends (`:225-226,243-246`). No accessibility defects found in the row list itself (no interactive controls inside a row to trap focus, consistent with Read-Only Guarantee).

---

## Registry Safety

`components.json` is present, but `128-UI-SPEC.md`'s Registry Safety table declares zero third-party blocks for this phase ("third-party: none declared — not applicable"), and no new shadcn primitives were added. Registry audit: 0 third-party blocks checked, no flags.

---

## Files Audited

- `.planning/phases/LEXCV-128-auditoria-de-atribui-es-de-pap-is/128-UI-SPEC.md`
- `web/src/app/(dashboard)/settings/auditoria-tab.tsx`
- `web/src/app/(dashboard)/settings/page.tsx` (tab wiring, lines 1-224; Phase 127 draft-cache/beforeunload/dirty-label, lines 840-960, 1080-1120)
- `web/src/lib/auditoria-rbac.ts`
- `web/src/app/(dashboard)/notificacoes/page.tsx` (precedent comparison, full file)
- `web/scripts/verify-auditoria-rbac.mjs` (executed: 11/11 PASS)
- `web/src/types/auditoria-rbac.ts`
- `web/src/hooks/use-admin.ts` (`useOfficeRbacAuditoria`, lines 106-121)
- `web/src/components/shared/combobox.tsx`
- `web/src/components/ui/card.tsx`

---

## Disposição das Correcções (aplicada depois da revisão)

Revisão advisory, 20/24. As três correcções prioritárias foram aplicadas; as três
recomendações menores foram avaliadas e não aplicadas, com motivo.

| # | Achado | Disposição |
|---|--------|-----------|
| 1 | Falta o `CardTitle` "Auditoria de Atribuições de Papéis" — a aba não identifica o ecrã | **Corrigido.** A spec contradiz-se: a tabela de tipografia declara três `CardTitle`, o diagrama de estrutura desenha dois. Resolvido a favor do cabeçalho, porque todas as outras abas das Definições se identificam no primeiro título que mostram e esta abria em "Filtros", sem dizer filtros de quê. Acrescentado como bloco de cabeçalho (`h2` + descrição), não como terceiro cartão — o diagrama de estrutura mantém-se de dois cartões. |
| 2 | `CardTitle` de "Filtros"/"Resultados" sem `text-xl font-semibold` | **Corrigido.** Contrato inequívoco: os seis `CardTitle` de `settings/page.tsx` usam todos as duas classes, e a tabela de tipografia da spec declara-as. Sem elas, os títulos desta aba eram visivelmente menores do que os da aba ao lado. |
| 3 | Filtro "Utilizador alvo" degrada em silêncio para quem só tem `rbac:manage` | **Corrigido, sem mudar a limitação.** A query continua desativada sem `users:manage` (evita um 403 ao abrir a aba, decisão do Plano 1). O que muda é que deixa de ser silenciosa: o combobox fica `disabled` e uma linha explica que filtrar por utilizador exige a permissão de gestão de utilizadores, e que os eventos de todos continuam listados. |
| m1 | Cor hexadecimal literal herdada do precedente | Não aplicado. `dark:bg-[#020617]` é copiado verbatim do `<select>` de `notificacoes/page.tsx`; trocá-lo só nesta aba criava divergência entre dois controlos idênticos. Pertence a uma normalização de tokens, transversal, não a esta fase. |
| m2 | Spec contradiz-se sobre vermelho (tabela de Cor vs. Estrutura de Ecrã) | Não aplicado — defeito do documento, não do código. O código está do lado certo: o único vermelho é o do estado de erro (`text-red-600`), e esta fase não escreve nada, logo não tem acção destrutiva a pintar. |
| m3 | `EVENTO_DESCONHECIDO` difere do exemplo da spec | Não aplicado. A própria spec dá latitude aqui, e a redacção actual cumpre o que importa: nunca deixa escapar um código de `acao` cru para o ecrã. |

**Verificação depois das correcções:** `tsc --noEmit` limpo; `verify:auditoria-rbac` 11/11 PASS; `verify:papeis-escritorio` 18/18 PASS.
