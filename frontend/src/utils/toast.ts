/**
 * Simple toast utility.
 * In production this would integrate with a toast library (e.g. react-hot-toast).
 * For now it logs to console and can be replaced later.
 */

export type ToastType = 'success' | 'error' | 'warning' | 'info'

export interface Toast {
  id: string
  type: ToastType
  message: string
  duration?: number
}

type ToastListener = (toast: Toast) => void

const listeners: ToastListener[] = []

export const toastEmitter = {
  subscribe: (listener: ToastListener) => {
    listeners.push(listener)
    return () => {
      const idx = listeners.indexOf(listener)
      if (idx !== -1) listeners.splice(idx, 1)
    }
  },
  emit: (toast: Toast) => {
    listeners.forEach((l) => l(toast))
  },
}

let toastCounter = 0

function showToast(type: ToastType, message: string, duration = 5000): void {
  const id = `toast-${++toastCounter}`
  const toast: Toast = { id, type, message, duration }

  // Emit to any registered listeners (e.g. ToastContainer component)
  toastEmitter.emit(toast)

  // Fallback: console output
  if (type === 'error') {
    console.error(`[Toast ${type.toUpperCase()}]`, message)
  } else {
    console.log(`[Toast ${type.toUpperCase()}]`, message)
  }
}

export const toast = {
  success: (message: string, duration?: number) => showToast('success', message, duration),
  error: (message: string, duration?: number) => showToast('error', message, duration),
  warning: (message: string, duration?: number) => showToast('warning', message, duration),
  info: (message: string, duration?: number) => showToast('info', message, duration),
}
