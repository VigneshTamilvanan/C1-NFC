const express = require('express');
const forge   = require('node-forge');

const router  = express.Router();
const BASE    = 'https://api.moving.tech/pilot/app/v2';

let cachedToken     = null;
let cachedTokenExp  = 0;

function signPayload(data) {
  const pem = (process.env.MOVING_TECH_RSA_KEY || '').replace(/\\n/g, '\n');
  const privateKey = forge.pki.privateKeyFromPem(pem);
  const md = forge.md.sha256.create();
  md.update(data, 'utf8');
  const sig = privateKey.sign(md);
  return forge.util.encode64(sig);
}

async function getToken() {
  if (cachedToken && Date.now() < cachedTokenExp) return cachedToken;

  const timestamp = new Date().toISOString();
  const body = {
    identifierType:     'CONDUCTORTOKEN',
    merchantId:         'MTC',
    operatorBadgeToken: process.env.MOVING_TECH_OPERATOR_TOKEN || 'O30228',
    timestamp,
    deviceSerialNumber: process.env.MOVING_TECH_DEVICE_SERIAL || '3170049436',
    vehicleType:        'BUS',
  };

  const signature = signPayload(JSON.stringify(body));

  const res = await fetch(`${BASE}/auth/signature`, {
    method:  'POST',
    headers: {
      'Content-Type':      'application/json',
      'x-sdk-authorization': signature,
    },
    body: JSON.stringify(body),
  });

  const text = await res.text();
  console.log('Auth response', res.status, text);

  if (!res.ok) throw new Error(`Auth failed ${res.status}: ${text}`);

  const data = JSON.parse(text);
  cachedToken    = data.token || data.authToken || data.access_token;
  cachedTokenExp = Date.now() + 55 * 60 * 1000; // cache 55 min
  return cachedToken;
}

router.post('/validate', async (req, res) => {
  const { qrData, provider } = req.body;
  if (!qrData) return res.status(400).json({ error: 'qrData required' });

  try {
    const token = await getToken();

    const verifyRes = await fetch(
      `${BASE}/multimodal/ticket/verify?platformType=MULTIMODAL&city=Chennai`,
      {
        method:  'POST',
        headers: {
          'Content-Type':  'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify({
          tag:      'IntegratedQR',
          contents: {
            integratedQR: qrData,
            provider:     provider || 'MTC',
          },
        }),
      }
    );

    const verifyText = await verifyRes.text();
    console.log('Verify response', verifyRes.status, verifyText);

    const verifyData = JSON.parse(verifyText);
    const valid = Array.isArray(verifyData.legInfo) && verifyData.legInfo.length > 0;

    res.json({
      valid,
      legInfo:  verifyData.legInfo  || [],
      provider: verifyData.provider || provider || 'MTC',
      raw:      verifyData,
    });
  } catch (e) {
    console.error('Validate error', e);
    res.status(502).json({ error: e.message });
  }
});

module.exports = router;
