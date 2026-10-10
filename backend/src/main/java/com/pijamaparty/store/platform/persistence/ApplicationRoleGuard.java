package com.pijamaparty.store.platform.persistence;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * RNF-29.2: Row Level Security is skipped silently for superusers, roles with {@code BYPASSRLS} and table owners,
 * including through role membership. The application refuses to start if its database role is any of those or can
 * run DDL, in every environment. It runs once all beans exist (Flyway has migrated) and before the web server
 * accepts requests, so no request is ever served over such a connection.
 */
@Component
class ApplicationRoleGuard implements SmartInitializingSingleton {

    private static final String ROLE_CHECKS = """
        select r.rolsuper,
               r.rolbypassrls,
               exists (select 1 from pg_roles o
                        where o.oid <> r.oid and pg_has_role(r.oid, o.oid, 'MEMBER')
                          and (o.rolsuper or o.rolbypassrls)) as privileged_membership,
               exists (select 1 from pg_class c
                         join pg_namespace n on n.oid = c.relnamespace
                        where n.nspname not in ('pg_catalog', 'information_schema') and n.nspname not like 'pg_toast%'
                          and pg_has_role(r.oid, c.relowner, 'MEMBER')) as owns_relations,
               exists (select 1 from pg_namespace n
                        where n.nspname not in ('pg_catalog', 'information_schema') and n.nspname not like 'pg_%'
                          and pg_has_role(r.oid, n.nspowner, 'MEMBER')) as owns_schemas,
               exists (select 1 from pg_proc p
                         join pg_namespace n on n.oid = p.pronamespace
                        where n.nspname not in ('pg_catalog', 'information_schema')
                          and pg_has_role(r.oid, p.proowner, 'MEMBER')) as owns_functions,
               has_schema_privilege(r.oid, 'public', 'CREATE') as can_create_in_public,
               has_database_privilege(r.oid, current_database(), 'CREATE') as can_create_schemas
          from pg_roles r
         where r.rolname = current_user
        """;

    private final JdbcTemplate jdbc;

    ApplicationRoleGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void afterSingletonsInstantiated() {
        verify();
    }

    void verify() {
        List<String> problems = jdbc.queryForObject(ROLE_CHECKS, (rs, row) -> {
            var found = new ArrayList<String>();
            String[] checks = {"rolsuper", "rolbypassrls", "privileged_membership", "owns_relations", "owns_schemas",
                "owns_functions", "can_create_in_public", "can_create_schemas"};
            for (String check : checks) {
                if (rs.getBoolean(check)) {
                    found.add(check);
                }
            }
            return found;
        });
        if (problems == null || !problems.isEmpty()) {
            throw new IllegalStateException(
                "The application database role must be a plain role without ownership or DDL rights (RNF-29.2): "
                    + problems);
        }
    }
}
