---
phase: 28
slug: clientes-enriquecimento-cadastral
status: approved
shadcn_initialized: true
preset: none
created: 2026-06-10
---

# Phase 28 — UI Design Contract

> Visual and interaction contract for frontend phases.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | shadcn / tailwind v4 |
| Preset | Anti-Safe Harbor (sharp edges, high contrast) |
| Component library | radix-ui |
| Icon library | lucide-react |
| Font | var(--font-geist-sans) |

---

## Spacing Scale

Declared values (multiples of 4):

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4px | Icon gaps, badge spacing |
| sm | 8px | Form item spacing |
| md | 16px | Input padding, inner cards |
| lg | 24px | Outer grid, dialog padding |
| xl | 32px | Section spacing |

---

## Color

| Role | Value | Usage |
|------|-------|-------|
| Dominant (60%) | #f8fafc (Light) / #020617 (Dark) | Page background, surfaces |
| Secondary (30%) | #ffffff (Light) / #0f172a (Dark) | Form cards, sidebar, table containers |
| Accent (10%) | #0f766e (Teal) | Focus rings, primary buttons, active states |
| Destructive | #dc2626 (Red) | Errors, validation indicators |

---

## Copywriting Contract

| Element | Copy |
|---------|------|
| Document Type Label | Tipo de Documento |
| Document Number Label | Número do Documento |
| Sector Label | Ramo de Atividade |
| Details Label | Detalhes Adicionais |
| Document Placeholder | Introduza o número do documento |
| Sector Placeholder | Selecione o ramo de atividade |
| Details Placeholder | Observações e detalhes adicionais sobre o cliente |
| Document Number Error | Número de documento é obrigatório se o tipo estiver selecionado |
| NIF Length Error | NIF de Cabo Verde deve ter exatamente 9 dígitos |

---

## Layout e Comportamento do Formulário
- **Grid de Campos**: Os novos campos serão exibidos sob a seção "Informações Adicionais". Usará um grid de duas colunas:
  - Coluna 1: `Tipo de Documento` (Select) e `Número do Documento` (Input)
  - Coluna 2: `Ramo de Atividade` (Select) e `Detalhes Adicionais` (Textarea)
- **Selects**:
  - `Tipo de Documento` terá opções: `NIF`, `CNI`, `Passaporte`.
  - `Ramo de Atividade` terá opções: `Banca`, `Telecom`, `Construção`, `Serviços`, `Comércio`, `Outros`.
- **Estilos**: Todos os inputs terão cantos retos (`rounded-none`) respeitando a diretriz "Anti-Safe Harbor" da aplicação.

---

## Checker Sign-Off

- [x] Dimension 1 Copywriting: PASS
- [x] Dimension 2 Visuals: PASS
- [x] Dimension 3 Color: PASS
- [x] Dimension 4 Typography: PASS
- [x] Dimension 5 Spacing: PASS
- [x] Dimension 6 Registry Safety: PASS

**Approval:** approved 2026-06-10
