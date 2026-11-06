/**
 * The parts of a multipart request, read from the raw bytes. Test stubs use this instead of Request.formData(): on Node 24
 * undici's parser rejects the File it builds while jsdom's File is the global, so formData() throws in tests (not in browsers).
 * On Node 24 undici also sends a jsdom File as an empty part, so tests check that a part was sent and its type, not its bytes.
 */
export async function multipartParts(req: Request): Promise<Record<string, { filename?: string; type?: string; size: number }>> {
  const boundary = /boundary=([^;]+)/.exec(req.headers.get('content-type') ?? '')?.[1]
  if (!boundary) throw new Error('not multipart')
  const raw = new TextDecoder('latin1').decode(await req.clone().arrayBuffer()) // one char per byte
  const parts: Record<string, { filename?: string; type?: string; size: number }> = {}
  for (const chunk of raw.split(`--${boundary}`).slice(1, -1)) {
    const split = chunk.indexOf('\r\n\r\n')
    const head = chunk.slice(0, split)
    const body = chunk.slice(split + 4, chunk.length - 2) // drop the CRLF before the next boundary
    const name = /name="([^"]*)"/.exec(head)?.[1]
    if (name) parts[name] = { filename: /filename="([^"]*)"/.exec(head)?.[1], type: /Content-Type: (.+)/i.exec(head)?.[1]?.trim(), size: body.length }
  }
  return parts
}
