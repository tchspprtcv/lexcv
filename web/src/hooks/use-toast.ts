"use client"

import type * as React from "react"
import { toast as sonnerToast, type ExternalToast } from "sonner"

type ToastOptions = ExternalToast

/**
 * Contract-preserving wrapper over sonner's toast API (see
 * `.planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/101-PATTERNS.md`
 * "Toast → Sonner swap"). The public shape (`toast`, `toast.success`,
 * `toast.error`) is unchanged from the previous Radix-Toast-backed
 * implementation, so none of the ~26 existing call sites across the app
 * need to change. Internally this now delegates straight to sonner's own
 * `toast()`/`toast.success()`/`toast.error()`, rendered by the `<Toaster />`
 * mounted once near the app root (see `app/layout.tsx` and
 * `components/ui/sonner.tsx`).
 */
function toast(message: React.ReactNode, options?: ToastOptions) {
  return sonnerToast(message, options)
}

// Utilitários de chamada simplificados (Sucesso e Erro) — títulos e
// distinção visual sucesso(verde)/erro(vermelho) preservados verbatim.
toast.success = (message: React.ReactNode, options?: ToastOptions) =>
  sonnerToast.success("Sucesso", { description: message, ...options })

toast.error = (message: React.ReactNode, options?: ToastOptions) =>
  sonnerToast.error("Erro", { description: message, ...options })

/**
 * Thin compatibility shim. The reactive `useToast()` hook's only prior
 * consumer was `components/ui/toaster.tsx` (deleted by this change — grep
 * confirmed no other call site destructures it). sonner owns its own toast
 * state/rendering internally via the `<Toaster />` mounted at the root, so
 * there is no reducer/bridge machinery left to expose. Kept only so any
 * future import of `useToast` still resolves to the same `{ toast, dismiss }`
 * shape instead of a missing export.
 */
function useToast() {
  return {
    toast,
    dismiss: sonnerToast.dismiss,
  }
}

export { useToast, toast }
