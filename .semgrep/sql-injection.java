// Test fixtures for sql-injection.yml (semgrep --test .semgrep). Not compiled: it only exercises the rule.
// "ruleid" lines must be reported, "ok" lines must not (RNF-28, E0: "Dado código con SQL concatenado ... falla").

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Root;
import java.util.Map;
import org.hibernate.Session;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

class SqlInjectionFixtures {

    private static final String BY_SLUG = "select p from Product p where p.slug = :slug";
    private static final String COUNT_PRODUCTS = """
        select count(*) from product
        """;

    private final EntityManager em;
    private final Session session;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final JdbcClient client;

    SqlInjectionFixtures(EntityManager em, Session session, JdbcTemplate jdbc, NamedParameterJdbcTemplate named,
                         JdbcClient client) {
        this.em = em;
        this.session = session;
        this.jdbc = jdbc;
        this.named = named;
        this.client = client;
    }

    void jpa(String name, String sort, String id) {
        // ruleid: sql-must-be-a-constant
        em.createQuery("select p from Product p where p.name = '" + name + "'");
        String byVariable = "select p from Product p order by " + sort;
        // ruleid: sql-must-be-a-constant
        em.createQuery(byVariable);
        var inferred = "select p from Product p order by " + sort;
        // ruleid: sql-must-be-a-constant
        em.createQuery(inferred);
        // ruleid: sql-must-be-a-constant
        em.createNativeQuery("select * from product where id = %s".formatted(id));
        // ruleid: sql-must-be-a-constant
        em.createNativeQuery(String.format("select * from product where id = %s", id));
        // ruleid: sql-must-be-a-constant
        em.createQuery(buildQuery(sort));
        // ruleid: sql-must-be-a-constant
        session.createSelectionQuery("from Product order by " + sort, Object.class);

        // ok: sql-must-be-a-constant
        em.createQuery("select p from Product p where p.name = :name").setParameter("name", name);
        // ok: sql-must-be-a-constant
        em.createQuery(BY_SLUG).setParameter("slug", name);
        // ok: sql-must-be-a-constant
        em.createNativeQuery(COUNT_PRODUCTS);
    }

    void jdbc(String slug, String sort) {
        // ruleid: sql-must-be-a-constant
        jdbc.queryForObject("select count(*) from product where slug = '" + slug + "'", Integer.class);
        // ruleid: sql-must-be-a-constant
        jdbc.queryForList("select id from product order by " + sort);
        // ruleid: sql-must-be-a-constant
        named.query("select id from product order by " + sort, Map.of(), (rs, row) -> rs.getLong(1));
        StringBuilder sql = new StringBuilder("select id from product where slug = '").append(slug).append("'");
        // ruleid: sql-must-be-a-constant
        named.queryForList(sql.toString(), Map.of());
        // ruleid: sql-must-be-a-constant
        named.update("delete from product where slug = '" + slug + "'", Map.of());
        // ruleid: sql-must-be-a-constant
        client.sql("select * from product where slug = '" + slug + "'");

        // ok: sql-must-be-a-constant
        jdbc.queryForObject(COUNT_PRODUCTS, Integer.class);
        // ok: sql-must-be-a-constant
        named.queryForObject("select count(*) from product where slug = :slug", Map.of("slug", slug), Integer.class);
        // ok: sql-must-be-a-constant
        client.sql("select * from product where slug = :slug").param("slug", slug);
    }

    private String buildQuery(String sort) {
        return "select p from Product p order by " + sort;
    }
}

class SortAndLikeFixtures {

    interface Repository {
        // ruleid: query-annotation-with-spel
        @Query("select p from Product p where p.name = ?#{[0]}")
        Object bySpel(String name);

        // ok: query-annotation-with-spel
        @Query("select p from Product p where p.name = :name")
        Object byName(String name);
    }

    private static final String NAME_COLUMN = "name";
    private static final String SEARCH_ESCAPED = "select p from Product p where p.name like :q escape '\\'";
    // ruleid: like-without-escape
    private static final String SEARCH_UNESCAPED = "select p from Product p where p.name like :q";

    void sorting(String requested, Root<Object> root, CriteriaBuilder cb, SortColumn column) {
        // ruleid: sort-from-request-string
        JpaSort.unsafe("lower(" + requested + ")");
        // ruleid: sort-from-request-string
        Sort.by(requested);
        // ruleid: sort-from-request-string
        root.get(requested);
        // ruleid: like-without-escape
        cb.like(root.get(NAME_COLUMN), "%" + requested + "%");

        // ok: sort-from-request-string
        Sort.by(NAME_COLUMN);
        // ok: sort-from-request-string
        Sort.by("name");
        // ok: like-without-escape
        cb.like(root.get(NAME_COLUMN), LikePattern.contains(requested), '\\');
    }

    enum SortColumn { NAME }
}
