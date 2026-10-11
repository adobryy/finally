-- Seed only an empty database, so watchlist removals survive restarts.
INSERT INTO watchlist (id, user_id, ticker, added_at)
SELECT lower(hex(randomblob(16))), 'default', column1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
FROM (VALUES ('AAPL'),('GOOGL'),('MSFT'),('AMZN'),('TSLA'),('NVDA'),('META'),('JPM'),('V'),('NFLX'))
WHERE NOT EXISTS (SELECT 1 FROM users_profile);

INSERT OR IGNORE INTO users_profile (id, cash_balance, created_at)
VALUES ('default', 10000.0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));
