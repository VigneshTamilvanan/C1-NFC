const express = require('express');
const pool = require('./db');
const { requireAuth } = require('./auth');

const router = express.Router();

router.post('/tap', async (req, res) => {
  const {
    txnId, route, source, destination, fare, timestamp,
    tripId, waybillNo, conductorId, paymentMode, passengerCount
  } = req.body;
  if (!txnId || !fare) return res.status(400).json({ error: 'txnId and fare required' });

  const dupe = await pool.query('SELECT ticket_no FROM tickets WHERE txn_id = $1', [txnId]);
  if (dupe.rows.length) return res.json({ ticketNo: dupe.rows[0].ticket_no, status: 'issued' });

  const countRes = await pool.query('SELECT COUNT(*) FROM tickets');
  const seq = parseInt(countRes.rows[0].count, 10) + 1;
  const ticketNo = 'TK-' + String(seq).padStart(5, '0');
  const pax = parseInt(passengerCount || 1, 10);

  await pool.query(
    `INSERT INTO tickets
      (ticket_no, txn_id, waybill_no, trip_id, conductor_id, route, source, destination, fare, payment_mode, passenger_count)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
    [ticketNo, txnId, waybillNo || null, tripId || null, conductorId || null,
     route, source, destination, fare, paymentMode || 'CARD', pax]
  );

  res.json({ ticketNo, status: 'issued' });
});

router.get('/tickets', requireAuth, async (req, res) => {
  const page  = parseInt(req.query.page  || '1', 10);
  const limit = parseInt(req.query.limit || '50', 10);
  const offset = (page - 1) * limit;

  const { rows } = await pool.query(
    `SELECT * FROM tickets ORDER BY created_at DESC LIMIT $1 OFFSET $2`,
    [limit, offset]
  );
  const { rows: [{ count }] } = await pool.query('SELECT COUNT(*) FROM tickets');
  res.json({ tickets: rows, total: parseInt(count, 10), page, limit });
});

router.get('/stats', requireAuth, async (req, res) => {
  const { rows: [total] } = await pool.query(
    `SELECT COUNT(*) AS tickets, COALESCE(SUM(fare),0) AS revenue FROM tickets`
  );
  const { rows: [today] } = await pool.query(
    `SELECT COUNT(*) AS tickets, COALESCE(SUM(fare),0) AS revenue
     FROM tickets WHERE created_at >= CURRENT_DATE`
  );
  res.json({
    total:  { tickets: parseInt(total.tickets, 10),  revenue: parseFloat(total.revenue)  },
    today:  { tickets: parseInt(today.tickets, 10),  revenue: parseFloat(today.revenue)  },
  });
});

module.exports = router;
