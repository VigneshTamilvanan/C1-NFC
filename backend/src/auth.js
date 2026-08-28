const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const pool = require('./db');

const router = express.Router();

router.post('/login', async (req, res) => {
  const { username, password } = req.body;
  if (!username || !password)
    return res.status(400).json({ error: 'username and password required' });

  const { rows } = await pool.query('SELECT * FROM users WHERE username = $1', [username]);
  const user = rows[0];
  if (!user) return res.status(401).json({ error: 'Invalid credentials' });

  const ok = await bcrypt.compare(password, user.password_hash);
  if (!ok) return res.status(401).json({ error: 'Invalid credentials' });

  const token = jwt.sign(
    { userId: user.id, username: user.username, role: user.role, depotId: user.depot_id, depotName: user.depot_name },
    process.env.JWT_SECRET, { expiresIn: '8h' }
  );

  res.json({ token, role: user.role, depotName: user.depot_name, username: user.username });
});

// Admin-only: create a user (requires valid admin token)
router.post('/create-user', async (req, res) => {
  const header = req.headers.authorization;
  if (!header) return res.status(401).json({ error: 'No token' });
  try {
    const caller = jwt.verify(header.replace('Bearer ', ''), process.env.JWT_SECRET);
    if (caller.role !== 'admin') return res.status(403).json({ error: 'Admin only' });
  } catch {
    return res.status(401).json({ error: 'Invalid token' });
  }

  const { username, password, role, depotId, depotName } = req.body;
  if (!username || !password) return res.status(400).json({ error: 'username and password required' });

  try {
    const hash = await bcrypt.hash(password, 10);
    await pool.query(
      `INSERT INTO users (username, password_hash, role, depot_id, depot_name)
       VALUES ($1, $2, $3, $4, $5)
       ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash, role = EXCLUDED.role, depot_id = EXCLUDED.depot_id, depot_name = EXCLUDED.depot_name`,
      [username, hash, role || 'depot', depotId || null, depotName || null]
    );
    res.json({ ok: true, username, role: role || 'depot' });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

function requireAuth(req, res, next) {
  const header = req.headers.authorization;
  if (!header) return res.status(401).json({ error: 'No token' });
  const token = header.replace('Bearer ', '');
  try {
    req.user = jwt.verify(token, process.env.JWT_SECRET);
    next();
  } catch {
    res.status(401).json({ error: 'Invalid token' });
  }
}

module.exports = { router, requireAuth };
