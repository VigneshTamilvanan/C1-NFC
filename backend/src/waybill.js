const express = require('express');
const pool = require('./db');
const { requireAuth } = require('./auth');

const router = express.Router();

function genWaybillNo() {
  return 'T' + Date.now() + Math.floor(Math.random() * 1000);
}

function genTripId(waybillNo, tripNo) {
  return `${waybillNo}-T${tripNo}`;
}

// Depot creates waybill and assigns conductor
router.post('/waybill', requireAuth, async (req, res) => {
  try {
    const {
      conductorStaffNo, conductorName, driverStaffNo, driverName,
      busScheduleNo, routeNo, fleetNo, deviceSerial,
      dutyDate, scheduleStartTime, shift, typeOfService,
      serviceType, scheduleType, ticketBoxNo, noOfDevices, tripCount
    } = req.body;

    if (!conductorStaffNo || !dutyDate) {
      return res.status(400).json({ error: 'conductorStaffNo and dutyDate required' });
    }

    const waybillNo = genWaybillNo();
    const depotId   = req.user.depotId || 'DEFAULT';
    const depotName = req.user.depotName || 'Default Depot';

    await pool.query(
      `INSERT INTO waybills (
        waybill_no, depot_id, depot_name,
        conductor_staff_no, conductor_name, driver_staff_no, driver_name,
        bus_schedule_no, route_no, fleet_no, device_serial,
        duty_date, schedule_start_time, shift, type_of_service,
        service_type, schedule_type, ticket_box_no, no_of_devices
      ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19)`,
      [
        waybillNo, depotId, depotName,
        conductorStaffNo, conductorName, driverStaffNo, driverName,
        busScheduleNo, routeNo, fleetNo, deviceSerial,
        dutyDate, scheduleStartTime, shift, typeOfService,
        serviceType, scheduleType, ticketBoxNo, noOfDevices || 1
      ]
    );

    // Pre-create trip slots
    const count = parseInt(tripCount || 6, 10);
    for (let i = 1; i <= count; i++) {
      await pool.query(
        `INSERT INTO trips (trip_id, waybill_no, trip_no) VALUES ($1, $2, $3)`,
        [genTripId(waybillNo, i), waybillNo, i]
      );
    }

    res.json({ waybillNo, tripCount: count, status: 'open' });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: e.message });
  }
});

// ETM app: get today's waybill for a conductor
router.get('/waybill/conductor/:staffNo', async (req, res) => {
  try {
    const { staffNo } = req.params;
    const { rows } = await pool.query(
      `SELECT w.*,
        json_agg(t ORDER BY t.trip_no) AS trips
       FROM waybills w
       LEFT JOIN trips t ON t.waybill_no = w.waybill_no
       WHERE w.conductor_staff_no = $1
         AND w.duty_date = CURRENT_DATE
         AND w.status = 'open'
       GROUP BY w.id
       ORDER BY w.created_at DESC
       LIMIT 1`,
      [staffNo]
    );
    if (!rows.length) return res.status(404).json({ error: 'No waybill assigned for today' });
    res.json(rows[0]);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Get waybill detail
router.get('/waybill/:waybillNo', requireAuth, async (req, res) => {
  try {
    const { rows } = await pool.query(
      `SELECT w.*,
        json_agg(t ORDER BY t.trip_no) AS trips
       FROM waybills w
       LEFT JOIN trips t ON t.waybill_no = w.waybill_no
       WHERE w.waybill_no = $1
       GROUP BY w.id`,
      [req.params.waybillNo]
    );
    if (!rows.length) return res.status(404).json({ error: 'Not found' });
    res.json(rows[0]);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// List waybills
router.get('/waybills', requireAuth, async (req, res) => {
  try {
    const { date, depotId } = req.query;
    const conditions = [];
    const params = [];

    if (req.user.role === 'depot') {
      params.push(req.user.depotId);
      conditions.push(`w.depot_id = $${params.length}`);
    } else if (depotId) {
      params.push(depotId);
      conditions.push(`w.depot_id = $${params.length}`);
    }

    if (date) {
      params.push(date);
      conditions.push(`w.duty_date = $${params.length}`);
    }

    const where = conditions.length ? 'WHERE ' + conditions.join(' AND ') : '';
    const { rows } = await pool.query(
      `SELECT w.*,
        COUNT(t.id) AS trip_count,
        SUM(t.total_amount) AS total_revenue,
        SUM(t.passenger_count) AS total_passengers
       FROM waybills w
       LEFT JOIN trips t ON t.waybill_no = w.waybill_no
       ${where}
       GROUP BY w.id
       ORDER BY w.created_at DESC`,
      params
    );
    res.json(rows);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ETM: Begin trip
router.post('/trip/start', async (req, res) => {
  try {
    const { waybillNo, tripNo, route, source, destination } = req.body;
    if (!waybillNo || !tripNo) return res.status(400).json({ error: 'waybillNo and tripNo required' });

    const tripId = genTripId(waybillNo, tripNo);

    // Check trip exists and is pending
    const { rows } = await pool.query('SELECT * FROM trips WHERE trip_id = $1', [tripId]);
    if (!rows.length) return res.status(404).json({ error: 'Trip not found' });
    if (rows[0].status === 'closed') return res.status(400).json({ error: 'Trip already closed' });

    await pool.query(
      `UPDATE trips SET status = 'active', start_time = NOW(),
        route = COALESCE($2, route),
        source = COALESCE($3, source),
        destination = COALESCE($4, destination)
       WHERE trip_id = $1`,
      [tripId, route, source, destination]
    );

    // Mark waybill as active if not already
    await pool.query(
      `UPDATE waybills SET status = 'active' WHERE waybill_no = $1 AND status = 'open'`,
      [waybillNo]
    );

    res.json({ tripId, waybillNo, tripNo, status: 'active' });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ETM: End trip — aggregates ticket data
router.post('/trip/end', async (req, res) => {
  try {
    const { tripId } = req.body;
    if (!tripId) return res.status(400).json({ error: 'tripId required' });

    // Aggregate from tickets
    const { rows: agg } = await pool.query(
      `SELECT
        COUNT(*) AS passenger_count,
        COALESCE(SUM(fare), 0) AS total_amount,
        COALESCE(SUM(CASE WHEN payment_mode = 'CASH' THEN fare ELSE 0 END), 0) AS cash_amount,
        COALESCE(SUM(CASE WHEN payment_mode = 'UPI'  THEN fare ELSE 0 END), 0) AS upi_amount,
        COALESCE(SUM(CASE WHEN payment_mode = 'NCMC' THEN fare ELSE 0 END), 0) AS ncmc_amount,
        COUNT(CASE WHEN payment_mode = 'CASH' THEN 1 END) AS cash_count,
        COUNT(CASE WHEN payment_mode = 'UPI'  THEN 1 END) AS upi_count,
        COUNT(CASE WHEN payment_mode = 'NCMC' THEN 1 END) AS ncmc_count
       FROM tickets WHERE trip_id = $1`,
      [tripId]
    );

    const a = agg[0];
    await pool.query(
      `UPDATE trips SET
        status = 'closed', end_time = NOW(),
        passenger_count  = $2,
        total_amount     = $3,
        passenger_amount = $3,
        cash_amount      = $4,
        upi_amount       = $5,
        ncmc_amount      = $6,
        cash_count       = $7,
        upi_count        = $8,
        ncmc_count       = $9
       WHERE trip_id = $1`,
      [
        tripId,
        parseInt(a.passenger_count, 10),
        parseFloat(a.total_amount),
        parseFloat(a.cash_amount),
        parseFloat(a.upi_amount),
        parseFloat(a.ncmc_amount),
        parseInt(a.cash_count, 10),
        parseInt(a.upi_count, 10),
        parseInt(a.ncmc_count, 10)
      ]
    );

    res.json({ tripId, status: 'closed', summary: a });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Close waybill — all trips must be closed
router.post('/waybill/:waybillNo/close', async (req, res) => {
  try {
    const { waybillNo } = req.params;

    const { rows: open } = await pool.query(
      `SELECT COUNT(*) FROM trips WHERE waybill_no = $1 AND status = 'active'`,
      [waybillNo]
    );
    if (parseInt(open[0].count, 10) > 0) {
      return res.status(400).json({ error: 'Close all active trips first' });
    }

    const { rows: summary } = await pool.query(
      `SELECT
        SUM(passenger_count) AS total_passengers,
        SUM(total_amount)    AS total_revenue,
        SUM(cash_amount)     AS cash_total,
        SUM(upi_amount)      AS upi_total,
        SUM(ncmc_amount)     AS ncmc_total,
        SUM(cash_count)      AS cash_tickets,
        SUM(upi_count)       AS upi_tickets,
        SUM(ncmc_count)      AS ncmc_tickets
       FROM trips WHERE waybill_no = $1`,
      [waybillNo]
    );

    await pool.query(
      `UPDATE waybills SET status = 'closed' WHERE waybill_no = $1`,
      [waybillNo]
    );

    res.json({ waybillNo, status: 'closed', summary: summary[0] });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

module.exports = router;
