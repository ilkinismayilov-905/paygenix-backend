/** Only same-site paths: never `//host`, `/\host` (browsers read it as `//host`) or a full URL. */
export function safeReturnPath(from: unknown): string {
  return typeof from === 'string' && /^\/(?![/\\])/.test(from) ? from : '/'
}
