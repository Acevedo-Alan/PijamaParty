-- Flyway callback, run as tienda_owner after every migrate. The default privileges of deploy/db/init/01-roles.sh give
-- the application DML on every table, which must not include Flyway's history: otherwise a compromised application
-- could mark a future migration (for example one that enables Row Level Security) as already applied (RNF-27, RNF-28).
REVOKE ALL ON TABLE flyway_schema_history FROM tienda_app;
REVOKE ALL ON TABLE flyway_schema_history FROM PUBLIC;
