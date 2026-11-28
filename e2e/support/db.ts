import pg from 'pg'
import { env } from './env'

// Fixtures the product deliberately has no API for. Everything else goes through the API or the UI.

async function withClient<T>(work: (client: pg.Client) => Promise<T>): Promise<T> {
  const client = new pg.Client({ connectionString: env.databaseURL })
  await client.connect()
  try {
    return await work(client)
  } finally {
    await client.end()
  }
}

/** Admins are users with an ADMIN_ROLE row (README); there is no API that grants it. */
export async function makeAdmin(email: string): Promise<void> {
  await withClient(async (c) => {
    const res = await c.query(
      `INSERT INTO user_roles (user_id, role) SELECT id, 'ADMIN_ROLE' FROM users WHERE lower(email) = lower($1)`,
      [email],
    )
    if (res.rowCount !== 1) throw new Error(`no user ${email}`)
  })
}

/**
 * Gives a card money the way the V7 migration booked old balances: one balanced OPENING_BALANCE posting, so the
 * ledger invariants hold (a top-up needs Stripe, which the suite does not have).
 */
export async function fundCard(cardNumber: string, ownerEmail: string, amount: string): Promise<void> {
  const last4 = cardNumber.slice(-4)
  await withClient(async (c) => {
    await c.query('BEGIN')
    try {
      const card = await c.query(
        `SELECT c.id, c.currency, a.id AS account_id FROM cards c
           JOIN users u ON u.id = c.owner_id JOIN ledger_accounts a ON a.card_id = c.id
          WHERE c.last4 = $1 AND lower(u.email) = lower($2) AND c.closed_at IS NULL FOR UPDATE OF c`,
        [last4, ownerEmail],
      )
      if (card.rowCount !== 1) throw new Error(`no open card ...${last4} for ${ownerEmail}`)
      const { id, currency, account_id } = card.rows[0]
      const system = await c.query(`SELECT id FROM ledger_accounts WHERE code = 'OPENING_BALANCE' AND currency = $1`, [currency])
      await c.query(
        `INSERT INTO ledger_entries (tx_id, account_id, amount, currency, kind, reference)
         SELECT tx, acc, amt, $3, 'OPENING_BALANCE', 'e2e'
           FROM (SELECT gen_random_uuid() AS tx) t,
                (VALUES ($1::bigint, $4::numeric), ($2::bigint, -$4::numeric)) AS legs(acc, amt)`,
        [account_id, system.rows[0].id, currency, amount],
      )
      await c.query('UPDATE cards SET amount = amount + $1::numeric WHERE id = $2', [amount, id])
      await c.query('COMMIT')
    } catch (e) {
      await c.query('ROLLBACK')
      throw e
    }
  })
}
