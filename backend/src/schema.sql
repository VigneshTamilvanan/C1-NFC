CREATE TABLE IF NOT EXISTS users (
  id            SERIAL PRIMARY KEY,
  username      VARCHAR(100) UNIQUE NOT NULL,
  password_hash VARCHAR(200) NOT NULL,
  role          VARCHAR(20)  DEFAULT 'admin',
  depot_id      VARCHAR(50),
  depot_name    VARCHAR(100),
  created_at    TIMESTAMPTZ  DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS conductors (
  staff_no   VARCHAR(20) PRIMARY KEY,
  name       VARCHAR(100) NOT NULL,
  depot_id   VARCHAR(50),
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS drivers (
  staff_no   VARCHAR(20) PRIMARY KEY,
  name       VARCHAR(100) NOT NULL,
  depot_id   VARCHAR(50),
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS waybills (
  id                  SERIAL PRIMARY KEY,
  waybill_no          VARCHAR(30) UNIQUE NOT NULL,
  depot_id            VARCHAR(50),
  depot_name          VARCHAR(100),
  conductor_staff_no  VARCHAR(20),
  conductor_name      VARCHAR(100),
  driver_staff_no     VARCHAR(20),
  driver_name         VARCHAR(100),
  bus_schedule_no     VARCHAR(30),
  route_no            VARCHAR(20),
  fleet_no            VARCHAR(20),
  device_serial       VARCHAR(30),
  duty_date           DATE NOT NULL,
  schedule_start_time TIME,
  shift               VARCHAR(10),
  type_of_service     VARCHAR(5),
  service_type        VARCHAR(50),
  schedule_type       VARCHAR(50),
  ticket_box_no       VARCHAR(20),
  no_of_devices       INT DEFAULT 1,
  status              VARCHAR(20) DEFAULT 'open',
  created_at          TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS trips (
  id               SERIAL PRIMARY KEY,
  trip_id          VARCHAR(30) UNIQUE NOT NULL,
  waybill_no       VARCHAR(30),
  trip_no          INT NOT NULL,
  route            VARCHAR(20),
  source           VARCHAR(200),
  destination      VARCHAR(200),
  start_time       TIMESTAMPTZ,
  end_time         TIMESTAMPTZ,
  status           VARCHAR(20) DEFAULT 'pending',
  passenger_count  INT DEFAULT 0,
  luggage_count    INT DEFAULT 0,
  passenger_amount NUMERIC(10,2) DEFAULT 0,
  luggage_amount   NUMERIC(10,2) DEFAULT 0,
  total_amount     NUMERIC(10,2) DEFAULT 0,
  cash_amount      NUMERIC(10,2) DEFAULT 0,
  upi_amount       NUMERIC(10,2) DEFAULT 0,
  ncmc_amount      NUMERIC(10,2) DEFAULT 0,
  cash_count       INT DEFAULT 0,
  upi_count        INT DEFAULT 0,
  ncmc_count       INT DEFAULT 0,
  created_at       TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tickets (
  id           SERIAL PRIMARY KEY,
  ticket_no    VARCHAR(20)  UNIQUE NOT NULL,
  txn_id       VARCHAR(100) UNIQUE NOT NULL,
  waybill_no   VARCHAR(30),
  trip_id      VARCHAR(30),
  conductor_id VARCHAR(20),
  route        VARCHAR(20),
  source       VARCHAR(200),
  destination  VARCHAR(200),
  fare         NUMERIC(8,2),
  payment_mode VARCHAR(20)  DEFAULT 'UPI',
  status       VARCHAR(20)  DEFAULT 'issued',
  created_at   TIMESTAMPTZ  DEFAULT NOW()
);
