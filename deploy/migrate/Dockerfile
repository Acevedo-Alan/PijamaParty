# One-shot migration container (T-E0-06, RNF-27): Flyway runs as tienda_owner and exits; the API container never
# receives the owner credentials. Same Flyway version as Spring Boot 3.5.16 (11.7.2). Build context: repository root.
FROM flyway/flyway:11.7.2-alpine
COPY backend/src/main/resources/db/migration/ /flyway/sql/
COPY deploy/migrate/migrate.sh /flyway/migrate.sh
ENTRYPOINT ["sh", "/flyway/migrate.sh"]
