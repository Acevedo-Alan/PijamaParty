-- T-E8-10, UC-34: the back office's "Clientes". The administrator reads the customers' users, and their linked Google
-- and Facebook accounts for the profile (T-E8-11), which shows only the provider of each (never the external subject
-- or its email). Read only: no policy here inserts, updates or deletes, and no other role gains anything. The
-- administrator's own row stays readable through app_user_admin_select (V5); addresses already through
-- address_admin_select (V29). Every external account belongs to a customer (external_identity_system_insert, V34).
CREATE POLICY app_user_admin_customer_select ON app_user FOR SELECT TO tienda_app
    USING (app_role() = 'ADMIN' AND role = 'CUSTOMER');

CREATE POLICY external_identity_admin_select ON external_identity FOR SELECT TO tienda_app
    USING (app_role() = 'ADMIN');
