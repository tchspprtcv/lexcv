# Feature Research

**Domain:** Legal case/matter management — "processos" (litigation) module deep dive: court/chamber hierarchy, case-origin categorization, court decisions, facts, witnesses, and engagement-letter generation
**Researched:** 2026-07-07
**Confidence:** MEDIUM-HIGH (grounded in named products' public help centers/docs + official Portuguese/Brazilian judicial-system sources; some claims are WebSearch-derived and marked accordingly)

## Context Recap

This research covers 4 specific asks for LexCV v2.9, each mapped against how established products model the same concept. LexCV already has: `Processo` (numeroProcesso, tipoProcesso, areaJuridica, tribunal, estado, datas, legalHold), `Parte` (parties CRUD), `Fase` (phase/stage catalog CRUD), `Movimentacao` (generic case-log: tipo/descricao/data), `Documento` (generic upload, already usable via processoId FK), `Honorario` (fee entity, already has processoId FK, currently 100% manual entry via `/financeiro`), intake flow with conflict-check gating TRIAGEM→ATIVO, and a Timeline that aggregates movimentações/transições/eventos/documentos. This file evaluates what to add without duplicating what exists.

## Feature Landscape

### Table Stakes (Users Expect These)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Campo Juízo (texto livre, ao lado de Tribunal/Área Jurídica) | Table stakes for civil-law/Portuguese-tradition litigation tracking — every PT/BR/CV court record shows tribunal + juízo together. Confirmed both by Portuguese court-hierarchy sources (Comarca → Tribunal → Juízo) and by Brazil's PJe "órgão julgador" convention | LOW | Free text is the *correct* scope, not a normalized hierarchy table — see Anti-Features |
| Campo Origem obrigatório (Petição Inicial \| Notificações Avulsas) | Fundamental PT/BR civil-procedure distinction — whether the firm authored the first procedural act or is reacting to one already filed elsewhere (e.g., representing a defendant who was served). Determines initial deadline posture and task list | LOW | Standalone required enum on `Processo`/intake. Not the same concept as "referral source" (marketing attribution) used in Clio/MyCase — do not conflate |
| Sub-secção Decisões (data, tipo, resumo, anexo opcional) | Table stakes for litigation tracking in PT/BR-tradition firms — lawyers need a scannable list of "what did the court decide and when," distinct from the noisy generic Movimentações log. General PM tools (Clio/MyCase/PracticePanther) treat decisions as tagged documents, not a separate entity — LexCV's scoped middle ground (first-class record, still reusing Documento for the file) fits a small/medium firm better | LOW-MEDIUM | `tipo` should use the closed PT/BR taxonomy: Despacho, Decisão Interlocutória, Sentença, Acórdão (confirmed via TJDFT/TJPR official explainers) — not free text |
| Sub-secção Testemunhas (nome, contacto, tipo/arrolada por, notas) | Table stakes for any PT/BR civil suit involving oral evidence — witnesses are formally listed ("rol de testemunhas") and are a procedurally distinct actor from parties | LOW | Must be a separate entity from `Parte`, not a role flag on it — see Dependencies |
| Aba Documentos dedicada na ficha do processo | `Documento` already has a `processoId` FK and is fully functional server-side; only the dedicated frontend tab is missing. Direct precedent: Clientes v2.8 "Documentos Entregues" tab (Phase 79) already solved this exact upload/list pattern | LOW | Pure pattern-reuse — no new backend capability needed |
| Criação automática de Honorário ao TRIAGEM→ATIVO + Termo de Honorários imprimível | Template + merge-field document generation on matter data is standard across all three named common-law leaders (Clio Draft, PracticePanther templates, MyCase templates). Auto-linking matter-open to a billing record is *not* fully native even in those tools (usually manual/paid-automation-layer) — LexCV can do this natively because it controls its own state machine | LOW-MEDIUM (auto-create) / MEDIUM (printable template) | "Imprimível" (printable) matches how small/mid firms actually operate day to day — not e-signature workflow automation |

### Differentiators (Competitive Advantage)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Sub-secção Factos (descrição, data, ordem) | No general practice-management tool (Clio/MyCase/PracticePanther) has an equivalent — facts normally live as narrative in matter notes. A structured, ordered chronology is genuinely useful for drafting petições and preparing for hearings without importing litigation-support-tier complexity | LOW | Scope strictly to the 3 fields already defined in PROJECT.md — no linking to Documentos/Testemunhas/Decisões in this milestone (see Anti-Features) |
| Decisões surfaced in Timeline aggregator | The existing Timeline already aggregates movimentações/transições/eventos/documentos; extending it to include Decisões avoids creating a second, disconnected view of case history | LOW-MEDIUM | Recommended as a fast-follow within v2.9 if low-cost, otherwise first candidate for immediate next iteration |
| Auto-linking Honorario prefill from intake's honorários propostos (totalidade, por extenso, previsão — already captured in v2.4) | Turns already-collected intake data into a working draft billing record automatically, rather than requiring re-entry — a genuine efficiency edge over Clio/MyCase's manual matter-to-billing setup | LOW | Purely additive to the existing state-transition hook; no new data capture required |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|------------------|-------------|
| Normalized Tribunal→Juízo hierarchy table (cascading dropdown, FK catalog) | Feels "more correct" than free text; Brazil's PJe does maintain such catalogs nationally | Disproportionate for Cape Verde's small, stable court system — neither the common-law pattern (Clio's flat "Court" field) nor CV's actual court-structure size justifies a maintained hierarchy catalog. Adds migration/maintenance burden with no real user benefit at this scale | Free-text Juízo field next to existing Tribunal/Área Jurídica fields (already scoped correctly in PROJECT.md) |
| Marketing/referral-source (lead origination) tracking bundled into "Origem" | Common-law PM tools (Clio/MyCase) do track "how did this client find us" and it's tempting to conflate with "origem do processo" | It's a different concept entirely (business development vs. procedural posture) — conflating the two fields would corrupt the procedural meaning of Origem and add unrequested scope | Keep Origem strictly procedural (Petição Inicial \| Notificações Avulsas); if lead-source tracking is wanted later, model it as a separate field on Cliente intake, not Processo |
| Facts↔Documentos↔Testemunhas cross-linking, "favorability" tagging (Casefleet/CaseMap+-style chronology) | Sounds valuable — "connect everything" | This is trial-prep-grade litigation-support tooling (built for large firms managing thousands of discovery documents), wildly disproportionate to a CV small/medium firm's day-to-day docket tracking. High implementation cost for low realized value at this scale | Flat, unlinked Factos list (as scoped); revisit only if the firm's litigation volume/complexity genuinely grows to justify it |
| Deposition/testimony status tracking on Testemunha (scheduled/taken/transcribed, Everchron-style witness hub) | Feels like "of course a witness needs a status" | Formal pre-trial deposition practice is a common-law (US) procedural concept; PT/BR civil procedure's witness testimony happens primarily at trial hearings (audiência), already modeled by LexCV's existing Evento/Agenda module. Building deposition tracking imports a procedural model that doesn't match CV practice | Keep Testemunha to the 4 scoped fields (nome, contacto, tipo/arrolada por, notas); link witness appearance to Evento/Agenda if/when needed, not a new status field |
| Full unattended engagement-letter automation (auto-generate → auto-send → auto-track e-signature → auto-flip matter status on signed return) | Sounds like "modern" SaaS automation | Confirmed by research that even Clio, MyCase, and PracticePanther — mature, well-funded market leaders — do NOT do this natively; achieving it requires bolting on a third-party workflow-orchestration layer (e.g., Zapier-style tooling) even for them. Building this in-house for v2.9 is disproportionate scope and not what PROJECT.md requests ("imprimível" = printable) | Auto-create the `Honorario` record on TRIAGEM→ATIVO (LexCV's own controlled trigger) + generate a printable Termo de Honorários from a template — matches real day-to-day usage (print/manually send) at this firm scale |
| Modeling Testemunha as a variant/role of the existing `Parte` entity | Reuses an existing table, seems DRY | Procedurally incorrect in PT/BR civil law — a witness ("testemunha arrolada") is not a party to the suit. Conflating them risks corrupting logic that assumes `Parte` rows represent litigants (e.g., the existing conflict-of-interest check built on Partes) | Separate, lightweight `Testemunha` entity as already scoped in PROJECT.md |

## Feature Dependencies

```
[Campo Juízo] (standalone field, Processo)
[Campo Origem] (standalone field, Processo/intake)
    └──informs──> [Fase catalog relevance] (existing entity — may hide/show phases based on origem, future iteration)

[Sub-secção Decisões] (new entity)
    ├──requires──> [Documento] (existing — reused for anexo opcional, no new upload mechanism)
    └──enhances──> [Timeline] (existing aggregator — should surface Decisões alongside Movimentações)

[Sub-secção Factos] (new entity)
    └──independent──> no FK dependency on Documento/Partes/Testemunhas in this milestone
    (explicitly deferred: Factos↔Documento/Testemunhas linking — future milestone only if justified)

[Sub-secção Testemunhas] (new entity)
    ├──conceptually-distinct-from──> [Parte] (existing — witnesses are NOT parties; separate table, not a role flag)
    └──could-link-to──> [Evento/Agenda] (existing — testimony happens at audiência, already modeled) — optional, not required for v2.9

[Aba Documentos dedicada] (new UI, reuses existing pattern)
    ├──requires──> [Documento] (existing entity, already has processoId FK)
    └──reuses-pattern-from──> [Clientes v2.8 Documentos Entregues tab] (Phase 79 — same upload/list pattern)

[Criação automática de Honorário] (new trigger)
    ├──requires──> [Honorario] (existing entity, processoId FK already present)
    ├──requires──> [TRIAGEM→ATIVO transition] (existing state machine — hook point)
    └──prefills-from──> [Intake honorários propostos fields] (existing, v2.4: totalidade, por extenso, previsão)

[Geração Termo de Honorários imprimível] (new feature)
    ├──requires──> [Honorario] (existing, post auto-creation)
    └──reuses-pattern-from──> [Ficha Cliente imprimível] (existing print-view pattern, v2.4)
```

### Dependency Notes

- **Decisões requires Documento:** the "anexo opcional" field should be implemented via the existing generic `Documento` upload entity (processoId-scoped), exactly as the new Documentos tab will do — do not build a second file-upload mechanism.
- **Decisões enhances Timeline:** the existing Timeline aggregator (movimentações/transições/eventos/documentos) should be extended to also surface Decisões entries, otherwise this becomes a second, disconnected view of case history — a likely UX gap if skipped.
- **Testemunhas is conceptually distinct from Parte, not a variant of it:** do not add a `tipo=TESTEMUNHA` row to the existing `Parte` table. Procedurally (PT/BR civil law) a witness is not a party to the suit; conflating them would corrupt any future logic that assumes `Parte` rows represent litigants (e.g., conflict-of-interest checks already built on Partes).
- **Honorario auto-creation requires the TRIAGEM→ATIVO hook:** this already exists as a state transition in the intake/conflict-check flow — the new logic is additive at that exact point, not a new workflow.
- **Termo de Honorários reuses the Ficha Cliente printable pattern:** v2.4 already solved "printable view that reproduces a real office form" — re-deriving that CSS/print approach avoids introducing a new PDF-generation dependency (e.g., no need for a headless-Chrome or PDF library if the existing print-stylesheet approach is reused).

## MVP Definition

### Launch With (v2.9)

Goal: deepen the processos module with structured legal data (Juízo, origem/tramitação), Decisões/Factos/Testemunhas sub-sections, a dedicated documents tab, and automatic fee-agreement creation on formalization — following international standards without over-building for firm scale.

- [ ] **Campo Juízo** (texto livre) — direct, matches both PT/BR convention and Clio's flat-field pattern
- [ ] **Campo Origem** (Petição Inicial \| Notificações Avulsas, enum, obrigatório no intake) — standalone, procedural (not marketing) field
- [ ] **Sub-secção Decisões** (data, tipo enum: Despacho/Decisão Interlocutória/Sentença/Acórdão, resumo, anexo opcional via Documento)
- [ ] **Sub-secção Factos** (descrição, data, ordem) — flat ordered list, no cross-linking
- [ ] **Sub-secção Testemunhas** (nome, contacto, tipo/arrolada por, notas) — separate entity from Parte
- [ ] **Aba Documentos dedicada** — reuses padrão v2.8 de Clientes (Phase 79)
- [ ] **Criação automática de Honorário** ao TRIAGEM→ATIVO, prefilled from intake honorários propostos
- [ ] **Termo de Honorários imprimível** — reuses padrão de impressão de Ficha Cliente (v2.4)

### Add After Validation (fast-follow within v2.9 or immediate next iteration)

- [ ] **Decisões surfaced in Timeline aggregator** — extend existing Timeline to include Decisões entries alongside Movimentações/transições/eventos/documentos
- [ ] **Fase catalog conditionally informed by Origem** — Petição Inicial vs. Notificação Avulsa likely imply different early-phase relevance

### Future Consideration (v2+, likely never needed at this firm scale)

- [ ] **Facts↔Documentos↔Testemunhas cross-linking** (Casefleet/CaseMap+-style chronology) — defer until/unless the firm handles complex multi-document litigation requiring trial-prep-grade chronology building
- [ ] **Deposition/testimony status tracking on Testemunha** (scheduled/taken/transcribed) — defer; CV/PT procedure doesn't have a deposition-equivalent pre-trial practice at US litigation scale; witness testimony already covered by Evento/Agenda
- [ ] **Normalized Tribunal→Juízo hierarchy catalog** (cascading dropdown) — defer; CV's court system is small/stable enough that free text is sufficient
- [ ] **E-signature-integrated engagement letter workflow** (auto-send, auto-track, auto-activate matter on signature) — defer; not even Clio/MyCase/PracticePanther do this natively
- [ ] **Marketing/referral-source (lead origination) tracking** — explicitly a different concept from "Origem" as scoped; out of scope unless separately requested

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|----------------------|----------|
| Campo Juízo | MEDIUM | LOW | P1 |
| Campo Origem | HIGH | LOW | P1 |
| Sub-secção Decisões | HIGH | MEDIUM | P1 |
| Sub-secção Factos | MEDIUM | LOW | P1 |
| Sub-secção Testemunhas | HIGH | LOW | P1 |
| Aba Documentos dedicada | HIGH | LOW (pattern reuse) | P1 |
| Auto-criação Honorário | HIGH | LOW-MEDIUM | P1 |
| Termo de Honorários imprimível | HIGH | MEDIUM | P1 |
| Decisões no Timeline aggregator | MEDIUM | LOW-MEDIUM | P2 |
| Facts/Testemunhas/Documentos cross-linking | LOW (at this firm scale) | HIGH | Do not build |
| Deposition tracking em Testemunha | LOW (procedural mismatch) | MEDIUM | Do not build |
| Hierarquia Tribunal→Juízo normalizada | LOW | MEDIUM | Do not build |
| E-signature workflow automation | LOW (not native even in market leaders) | HIGH | Do not build |

## Competitor Feature Analysis

| Feature | Clio / MyCase / PracticePanther (common-law) | Casefleet / CaseMap+ / Everchron (litigation-support tier) | LexCV v2.9 Approach |
|---------|------------------------------------------------|---------------------------------------------------------------|----------------------|
| Court/chamber field | Flat "Court" custom field, no hierarchy (Clio also has separate flat Judge/Case Number/County fields in litigation field sets) | N/A (not their focus) | Flat "Juízo" text field next to Tribunal — matches both |
| Case origin/intake type | Referral-source (marketing) + intake→matter one-click conversion; no procedural-origin field | N/A | Procedural enum (Petição Inicial \| Notificação Avulsa) — CV/PT-specific, not borrowed from either |
| Court decisions | Tagged documents in shared repository, no dedicated entity | Dedicated fact/decision chronology objects, linked to evidence, built for trial teams | Dedicated lightweight entity (data/tipo/resumo/anexo), reusing Documento for the file — middle ground |
| Case facts | Not modeled at all | Dedicated chronology entity with linking/favorability tagging | Flat ordered list (descrição/data/ordem), no linking — differentiator vs. general PM tools, far lighter than litigation-support tools |
| Witnesses | Contact/party record with a role tag, no dedicated entity | Hub entity auto-linked to depositions/documents/issues, real-time updates | Dedicated lightweight entity, separate from Parte, no hub/linking behavior |
| Engagement letter generation | Template + merge fields (Clio Draft, PracticePanther templates, Actionstep), manual trigger/send, no native auto-activation of matter on signature | N/A | Template + merge fields (reuse Ficha Cliente print pattern), auto-triggered on TRIAGEM→ATIVO transition — better native automation than named competitors for the one thing LexCV fully controls, still print-based not e-sign-based |

## Sources

- [Clio: Create Matters — Help Center](https://help.clio.com/hc/en-us/articles/9285959663131-Create-Matters) — MEDIUM confidence (WebSearch-extracted; direct WebFetch returned 403)
- [Clio: Get Started With Custom Fields](https://help.clio.com/hc/en-us/articles/9285493193115-Get-Started-With-Custom-Fields) — MEDIUM
- [Clio: Create Custom Fields](https://help.clio.com/hc/en-us/articles/9285496802331-Create-Custom-Fields) — MEDIUM
- [Legal Cloud Technology: Using Clio as a Case Management System — Part Two, Custom Fields](https://legalcloudtechnology.com/using-clio-as-a-case-management-system-part-two-custom-fields/) — MEDIUM
- [Everchron: Witnesses](https://everchron.com/witnesses) — HIGH (direct WebFetch)
- [Casefleet: Case Management Software](https://www.casefleet.com/use-cases/case-management-software) — MEDIUM (WebSearch)
- [Casefleet: Litigation Management Software](https://www.casefleet.com/use-cases/litigation-management-software) — MEDIUM
- [LexisNexis: CaseMap+ AI](https://www.lexisnexis.com/en-us/products/casemap.page) — MEDIUM
- [SmartAdvocate: Case Management Software vs. Document Management Systems](https://www.smartadvocate.com/article/case-management-software-vs-document-management-systems-what-law-firms-need-to-know) — MEDIUM
- [One Legal: Should your firm implement case, practice, or document management software?](https://www.onelegal.com/blog/case-practice-document-management-software-for-law-firms/) — MEDIUM
- [tribunais.org.pt: Os Tribunais / Judicial](https://tribunais.org.pt/Os-Tribunais/Judicial) — HIGH (direct WebFetch; defines comarca/tribunal/juízo hierarchy)
- [Portal TJPE: Órgãos Julgadores com PJe](https://portal.tjpe.jus.br/web/processo-judicial-eletronico/orgaos-julgadores-com-pje/unidades-com-pje) — MEDIUM (WebSearch, Brazilian PJe court-registration terminology, "órgão julgador" convention)
- [TJDFT: Sentença, decisão interlocutória, despacho e acórdão](https://www.tjdft.jus.br/institucional/imprensa/campanhas-e-produtos/direito-facil/edicao-semanal/sentenca-decisao-interlocutoria-despacho-e-acordao) — HIGH (official court public-education source, confirms 4-type taxonomy)
- [TJPR: Saiba a diferença entre sentença, decisão e despacho](https://www.tjpr.jus.br/noticias/-/asset_publisher/9jZB/content/saiba-a-diferenca-entre-sentenca-decisao-e-despacho/18319) — HIGH (corroborating official source)
- [Practiq.dev: Clio vs MyCase vs PracticePanther comparison 2026](https://practiq.dev/blog/clio-vs-mycase-vs-practicepanther-solo-small-firms) — MEDIUM (vendor-comparison blog, engagement-letter automation gap analysis across all 3 products)
- [US Tech Automations: Automate Law Firm Client Intake 2026 (Clio vs MyCase)](https://ustechautomations.com/resources/blog/automate-law-firm-client-intake-2026) — MEDIUM
- [CloudLex: Matter Management Software](https://www.cloudlex.com/applications/matter-management-software/) — MEDIUM (intake→matter one-click conversion pattern)
- [SimpleLaw: Legal Case Management Software / Matter Management](https://www.simplelaw.com/matter-management) — MEDIUM
- `.planning/PROJECT.md` — HIGH (primary source for current entity model, v2.9 milestone scope, and existing v2.4/v2.8 pattern precedents to reuse)

---
*Feature research for: LexCV processos module (v2.9 Melhoria Módulo Processos)*
*Researched: 2026-07-07*
