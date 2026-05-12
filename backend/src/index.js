require('dotenv').config();
const express = require('express');
const cors    = require('cors');
const path    = require('path');

const { router: authRouter } = require('./auth');
const ticketsRouter           = require('./tickets');
const validateRouter          = require('./validate');
const waybillRouter           = require('./waybill');
const fareRouter              = require('./fare');

const app  = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

app.use('/api/auth',    authRouter);
app.use('/api/transit', ticketsRouter);
app.use('/api/transit', validateRouter);
app.use('/api/transit', waybillRouter);
app.use('/api/transit', fareRouter);

app.use(express.static(path.join(__dirname, '../../dashboard')));
app.get('/{*path}', (req, res) =>
  res.sendFile(path.join(__dirname, '../../dashboard/index.html'))
);

app.listen(PORT, () => console.log(`Server running on :${PORT}`));
