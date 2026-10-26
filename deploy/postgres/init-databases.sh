#!/bin/sh
# Database-per-service on one PostgreSQL instance: each service gets its own database AND its own
# login that owns only that database, so no service can read another service's tables.
set -eu

create() {
  db="$1"; user="$2"; password="$3"
  echo "Creating database $db owned by $user"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-SQL
    CREATE USER $user WITH PASSWORD '$password';
    CREATE DATABASE $db OWNER $user;
    REVOKE ALL ON DATABASE $db FROM PUBLIC;
SQL
}

create identity_db identity "${IDENTITY_DB_PASSWORD:-identity}"
create order_db    order_svc "${ORDER_DB_PASSWORD:-order_svc}"
create payment_db  payment_svc "${PAYMENT_DB_PASSWORD:-payment_svc}"
