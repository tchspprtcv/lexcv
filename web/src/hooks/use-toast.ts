"use client"

import * as React from "react"

import type {
  ToastActionElement,
  ToastProps,
} from "@/components/ui/toast"

const TOAST_LIMIT = 3
const TOAST_REMOVE_DELAY = 1000000

type ToasterToast = ToastProps & {
  id: string
  title?: React.ReactNode
  description?: React.ReactNode
  action?: ToastActionElement
}

const actionTypes = {
  ADD_TOAST: "ADD_TOAST",
  UPDATE_TOAST: "UPDATE_TOAST",
  DISMISS_TOAST: "DISMISS_TOAST",
  REMOVE_TOAST: "REMOVE_TOAST",
} as const

let count = 0

function genId() {
  count = (count + 1) % Number.MAX_SAFE_INTEGER
  return count.toString()
}

type ActionType = typeof actionTypes

type Action =
  | {
      type: ActionType["ADD_TOAST"]
      toast: ToasterToast
    }
  | {
      type: ActionType["UPDATE_TOAST"]
      toast: Partial<ToasterToast>
    }
  | {
      type: ActionType["DISMISS_TOAST"]
      toastId?: ToasterToast["id"]
    }
  | {
      type: ActionType["REMOVE_TOAST"]
      toastId?: ToasterToast["id"]
    }

interface State {
  toasts: ToasterToast[]
}

const toastTimeouts = new Map<string, ReturnType<typeof setTimeout>>()

function addToRemoveQueue(toastId: string, dispatch: (action: Action) => void) {
  if (toastTimeouts.has(toastId)) {
    return
  }

  const timeout = setTimeout(() => {
    toastTimeouts.delete(toastId)
    dispatch({
      type: actionTypes.REMOVE_TOAST,
      toastId: toastId,
    })
  }, TOAST_REMOVE_DELAY)

  toastTimeouts.set(toastId, timeout)
}

export function reducer(state: State, action: Action): State {
  switch (action.type) {
    case actionTypes.ADD_TOAST:
      return {
        ...state,
        toasts: [action.toast, ...state.toasts].slice(0, TOAST_LIMIT),
      }

    case actionTypes.UPDATE_TOAST:
      return {
        ...state,
        toasts: state.toasts.map((t) =>
          t.id === action.toast.id ? { ...t, ...action.toast } : t
        ),
      }

    case actionTypes.DISMISS_TOAST: {
      // Side effect handled by caller (needs a dispatch reference) — see dismissToast().
      const { toastId } = action
      return {
        ...state,
        toasts: state.toasts.map((t) =>
          t.id === toastId || toastId === undefined
            ? {
                ...t,
                open: false,
              }
            : t
        ),
      }
    }
    case actionTypes.REMOVE_TOAST:
      if (action.toastId === undefined) {
        return {
          ...state,
          toasts: [],
        }
      }
      return {
        ...state,
        toasts: state.toasts.filter((t) => t.id !== action.toastId),
      }
  }
}

/**
 * Bridge to the currently-mounted <Toaster />'s dispatch function.
 *
 * Previous implementation stored listeners/state in a module-level array
 * anchored on `globalThis` so that plain `toast()` calls (from files that
 * don't render <Toaster />) could reach whichever component instance was
 * actually mounted. In practice that pattern relied on the *same* module
 * instance being imported everywhere, which Next.js Fast Refresh / Turbopack
 * does not guarantee when only some importing files are hot-reloaded — a
 * `toast()` call could resolve against a stale module graph that no longer
 * matched the live <Toaster />, silently dropping the dispatch.
 *
 * This bridge avoids that failure mode entirely: <Toaster /> re-registers
 * itself on every render (not just on mount), so the bridge always points at
 * whichever provider instance React actually has on screen, self-healing
 * across hot reloads instead of depending on a single frozen closure.
 */
let bridgeDispatch: ((action: Action) => void) | null = null

function registerToastBridge(dispatch: (action: Action) => void) {
  bridgeDispatch = dispatch
  return () => {
    if (bridgeDispatch === dispatch) {
      bridgeDispatch = null
    }
  }
}

function dispatchToast(action: Action) {
  if (!bridgeDispatch) {
    if (process.env.NODE_ENV !== "production") {
      console.warn(
        "[toast] dispatched before <Toaster /> mounted — toast will be dropped:",
        action,
      )
    }
    return
  }
  bridgeDispatch(action)
}

function dismissToast(toastId?: string) {
  if (toastId) {
    addToRemoveQueue(toastId, dispatchToast)
  }
  dispatchToast({ type: actionTypes.DISMISS_TOAST, toastId })
}

type Toast = Omit<ToasterToast, "id">

function toast({ ...props }: Toast) {
  const id = genId()

  const update = (props: ToasterToast) =>
    dispatchToast({
      type: actionTypes.UPDATE_TOAST,
      toast: { ...props, id },
    })
  const dismiss = () => dismissToast(id)

  dispatchToast({
    type: actionTypes.ADD_TOAST,
    toast: {
      ...props,
      id,
      open: true,
      onOpenChange: (open) => {
        if (!open) dismiss()
      },
    },
  })

  return {
    id: id,
    dismiss,
    update,
  }
}

/**
 * Owns the canonical toast state and registers itself as the live dispatch
 * target on every render. Must be mounted once, near the root (see
 * app/layout.tsx's <Toaster />) — that's the only place this hook is used.
 */
function useToast() {
  const [state, setState] = React.useState<State>({ toasts: [] })

  React.useEffect(() => {
    return registerToastBridge((action) => setState((prev) => reducer(prev, action)))
  })

  return {
    ...state,
    toast,
    dismiss: dismissToast,
  }
}

// Utilitários de chamada simplificados (Sucesso e Erro)
toast.success = (message: React.ReactNode, options?: Partial<Toast>) =>
  toast({ title: "Sucesso", description: message, variant: "default", ...options });

toast.error = (message: React.ReactNode, options?: Partial<Toast>) =>
  toast({ title: "Erro", description: message, variant: "destructive", ...options });

export { useToast, toast }
