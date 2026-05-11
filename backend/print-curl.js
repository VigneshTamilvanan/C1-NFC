require('dotenv').config();
const forge = require('node-forge');

const pem = (process.env.MOVING_TECH_RSA_KEY || '').replace(/\\n/g, '\n');
const privateKey = forge.pki.privateKeyFromPem(pem);
const timestamp = new Date().toISOString();
const body = {
  identifierType:     'CONDUCTORTOKEN',
  merchantId:         'MTC',
  operatorBadgeToken: 'O30228',
  timestamp,
  deviceSerialNumber: '3170049436',
  vehicleType:        'BUS',
};
const md = forge.md.sha256.create();
md.update(JSON.stringify(body), 'utf8');
const sig = forge.util.encode64(privateKey.sign(md));

console.log(`curl -s -X POST 'https://api.moving.tech/pilot/app/v2/auth/signature' \\
  -H 'Content-Type: application/json' \\
  -H 'x-sdk-authorization: ${sig}' \\
  -d '${JSON.stringify(body)}'`);
