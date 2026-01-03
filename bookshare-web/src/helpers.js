export function now() { return Date.now() }

export const BOOK_STATUS = {
  HIDDEN: 'HIDDEN',
  LENDING: 'LENDING',
  LENT: 'LENT', // used in Kotlin screens when accepted
}

export function trimOrNull(s) {
  const t = (s||'').trim()
  return t.length ? t : null
}