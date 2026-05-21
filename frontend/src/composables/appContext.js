import { inject } from 'vue'

export const appContextKey = Symbol('smart-exam-app-context')

export function useAppContext() {
  const context = inject(appContextKey)
  if (!context) {
    throw new Error('Smart exam app context is not provided')
  }
  return context
}
