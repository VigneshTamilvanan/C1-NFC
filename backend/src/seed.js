require('dotenv').config();
const bcrypt = require('bcryptjs');
const pool = require('./db');

(async () => {
  const hash = await bcrypt.hash('mtc@123', 10);
  await pool.query(
    `INSERT INTO users (username, password_hash) VALUES ($1, $2)
     ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash`,
    ['mtc_admin', hash]
  );
  console.log('Seeded user (username: mtc_admin)');
  process.exit(0);
})();
