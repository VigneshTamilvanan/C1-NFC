const express = require('express');
const router = express.Router();

// MTC stage-wise fare table [Effective 29-01-2018, G.O(Ms) No.48]
// Index = stage count (1-based), value = fare in rupees
const FARE_TABLE = {
  ordinary: [0, 5,6,7,8,9,10,11,12,13,14,15,15,16,16,17,17,18,18,19,19,20,20,21,21,22,22,23,23,24,24],
  express:  [0, 7,9,10,12,13,15,16,18,19,21,22,22,24,24,25,25,27,27,28,28,30,30,31,31,33,33,34,34,35,35],
  deluxe:   [0, 11,13,15,17,19,21,23,25,27,29,31,31,33,33,35,35,37,37,39,39,41,41,43,43,45,45,47,47,49,49],
  ac:       [0, 15,15,20,20,20,30,30,30,40,40,40,40,40,40,50,50,50,50,60,60,60,60,60,70,70,70,70,80,80,80],
  // Beyond 30 stages — extrapolate last known increment
};

// Route stop sequences (same as ETM app)
const ROUTE_STOPS = {
  '21': [
    'ROYAPURAM B.S','PARRYS CORNER','M.G.R.CENTRAL','GOVT ESTATE METRO R.S',
    'WESLEY H.S.S','Y.M.I.A','MANDAVELI','ADYAR GATE',
    'KOTTURPURAM','ANNA UNIVERSITY','GUINDY TVK ESTATE'
  ],
  '15': [
    'Island Ground','PARK STATION','EGMORE HOTEL EVEREST','DASAPRAKASH',
    'KMC HOSPITAL','TAYLORS ROAD','PACHAIYAPPAS COLLEGE',
    'AMINJIKARAI','ARUMBAKKAM POST OFFICE','KOYAMBEDU SCHOOL','M.G.R.KOYAMBEDU B.T'
  ],
  'M70': [
    'M.G.R.KOYAMBEDU B.T','VADAPALANI TEMPLE','ASHOK PILLAR',
    'KASI THEATRE','EKKATTUTHANGAL','GUINDY B.T',
    'VELACHERY CHECK POST','VELACHERY','THARAMANI PILLAIYAR TEMPLE',
    'TIDEL PARK','THIRUVANMIYUR B.T'
  ],
  '47': [
    'BESANT NAGAR B.T','ADYAR DEPOT','MADHYA KAILASH',
    'ANNA UNIVERSITY','SAIDAPET','THYAGARAYA NAGAR BUS TERMINUS',
    'T.NAGAR JEEVA PARK','VALLUVAR KOTTAM','NUNGAMBAKKAM RAILWAY STATION',
    'AMINJIKARAI','ANNA NAGAR 14 SHOP COMPLEX','ICF'
  ],
  '70': [
    'AVADI B.T','AMBATTUR O.T B.T','AMBATTUR INDUSTRIAL ESTATE B.T',
    'PADI LUCAS T.V.S','ANNA NAGAR WEST DEPOT','M.G.R.KOYAMBEDU B.T',
    'VADAPALANI TEMPLE','EKKATTUTHANGAL','GUINDY CIPET',
    'ALANDUR METRO R.S','PALLAVARAM BUS STAND','CHROMEPET',
    'TAMBARAM WEST BUS STAND'
  ],
};

function getFare(stages, serviceType) {
  const type = (serviceType || 'ordinary').toLowerCase();
  const table = FARE_TABLE[type] || FARE_TABLE.ordinary;

  if (stages <= 0) return 0;
  if (stages < table.length) return table[stages];

  // Beyond table — extrapolate: last two entries give increment pattern
  const last = table[table.length - 1];
  const extra = stages - (table.length - 1);
  // MTC pattern: +1 every 2 stages above last
  return last + Math.ceil(extra / 2);
}

function computeStages(routeNo, fromStop, toStop) {
  const stops = ROUTE_STOPS[routeNo];
  if (!stops) return null;

  const normalize = s => s.trim().toUpperCase();
  const from = normalize(fromStop);
  const to   = normalize(toStop);

  const fromIdx = stops.findIndex(s => normalize(s) === from || normalize(s).includes(from) || from.includes(normalize(s)));
  const toIdx   = stops.findIndex(s => normalize(s) === to   || normalize(s).includes(to)   || to.includes(normalize(s)));

  if (fromIdx === -1 || toIdx === -1) return null;
  return Math.abs(toIdx - fromIdx);
}

// GET /api/transit/fare?route=21&from=ROYAPURAM+B.S&to=GUINDY+TVK+ESTATE&service=ordinary
router.get('/fare', (req, res) => {
  const { route, from, to, service } = req.query;

  if (!route || !from || !to) {
    return res.status(400).json({ error: 'route, from, to required' });
  }

  const stages = computeStages(route, from, to);
  if (stages === null) {
    return res.status(404).json({
      error: 'Stop not found on route',
      availableRoutes: Object.keys(ROUTE_STOPS),
      stopsForRoute: ROUTE_STOPS[route] || null
    });
  }

  if (stages === 0) {
    return res.status(400).json({ error: 'Source and destination are the same stop' });
  }

  const serviceType = service || 'ordinary';
  const fare = getFare(stages, serviceType);

  res.json({
    route, from, to, stages, serviceType, fare,
    fareDisplay: '₹' + fare
  });
});

// GET /api/transit/stops/:routeNo — list stops for a route
router.get('/stops/:routeNo', (req, res) => {
  const stops = ROUTE_STOPS[req.params.routeNo];
  if (!stops) return res.status(404).json({ error: 'Route not found', available: Object.keys(ROUTE_STOPS) });
  res.json({ route: req.params.routeNo, stops });
});

// GET /api/transit/routes — list all routes
router.get('/routes', (req, res) => {
  const routes = Object.entries(ROUTE_STOPS).map(([id, stops]) => ({
    id,
    stops: stops.length,
    from: stops[0],
    to: stops[stops.length - 1]
  }));
  res.json(routes);
});

module.exports = router;
