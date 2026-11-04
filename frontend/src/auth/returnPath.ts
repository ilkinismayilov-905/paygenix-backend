/** Only same-site paths: never `//host` or a full URL. */
export function safeReturnPath(from: unknown): string {
  return typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') ? from : '/'
}
