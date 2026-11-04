import { en } from './en'

export type MessageKey = keyof typeof en

// shortcut: English only; add az.ts (typed as Record<MessageKey, string>) and a locale switch with the Azerbaijani pass.
export function t(key: MessageKey): string {
  return en[key]
}
