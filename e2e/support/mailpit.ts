import { expect } from '@playwright/test'
import { env } from './env'

type Summary = { ID: string; Created: string; Subject: string }

/**
 * The newest 6-digit code mailed to `to` after `since`. Polls Mailpit's API: the API sends mail after the request
 * that triggers it has answered, so the message can arrive a moment later.
 */
export async function codeSentTo(to: string, since: Date, subject?: RegExp): Promise<string> {
  let code: string | undefined
  await expect.poll(async () => {
    const search = await fetch(`${env.mailpitURL}/api/v1/search?query=${encodeURIComponent(`to:"${to}"`)}`)
    const { messages } = (await search.json()) as { messages: Summary[] }
    const fresh = messages
      .filter((m) => new Date(m.Created) >= new Date(since.getTime() - 1000))
      .filter((m) => !subject || subject.test(m.Subject))
      .sort((a, b) => b.Created.localeCompare(a.Created))
    if (fresh.length === 0) return undefined
    const message = await fetch(`${env.mailpitURL}/api/v1/message/${fresh[0].ID}`)
    const { Text } = (await message.json()) as { Text: string }
    code = Text.match(/\b(\d{6})\b/)?.[1]
    return code
  }, { message: `a code mailed to ${to}`, timeout: 15_000 }).toBeTruthy()
  return code!
}
