### Phase 73.1: Fechar gap CLI-05: remover sync legado de NIF nos formulários e adicionar validação server-side (INSERTED)

**Goal:** NIF válido, obrigatório (9 dígitos numéricos) é garantido system-wide — o campo dedicado `nif` é a única fonte de verdade em ambos os formulários (sem overwrite legado) e é enforced server-side no backend (Bean Validation + @Valid).
**Requirements**: CLI-05
**Depends on:** Phase 73
**Plans:** 1 plan

Plans:
- [ ] 73.1-01-PLAN.md — Remover sync legado de NIF nos 2 formulários; adicionar @NotBlank/@Pattern a Cliente.nif + @Valid nos endpoints; remover sync backend; gates de build/typecheck
