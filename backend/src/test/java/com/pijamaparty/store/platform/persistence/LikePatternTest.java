package com.pijamaparty.store.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** RNF-28: LIKE wildcards in user text are escaped, checked against PostgreSQL itself. */
@ApiTest
class LikePatternTest {

    private static final String MATCHES = """
        select v from (values ('50% off'), ('500 pijamas'), ('pijama_corto'), ('pijamaXcorto'), ('a\\b')) as t(v)
        where v like :q escape '\\' order by v
        """;

    private final NamedParameterJdbcTemplate jdbc;

    LikePatternTest(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Test
    void givenTextWithWildcards_whenSearching_thenTheyMatchLiterallyNotAsWildcards() {
        assertThat(search(LikePattern.contains("50%"))).containsExactly("50% off");
        assertThat(search(LikePattern.contains("_corto"))).containsExactly("pijama_corto");
        assertThat(search(LikePattern.contains("\\"))).containsExactly("a\\b");
    }

    @Test
    void givenPlainText_whenSearchingByPrefixOrContent_thenTheUsualMatchesAreFound() {
        assertThat(search(LikePattern.startsWith("50"))).containsExactly("50% off", "500 pijamas");
        assertThat(search(LikePattern.contains("pijama"))).containsExactly("500 pijamas", "pijamaXcorto", "pijama_corto");
    }

    @Test
    void givenText_whenEscaping_thenOnlyWildcardsAndTheEscapeCharacterArePrefixed() {
        assertThat(LikePattern.escape("a%b_c\\d e")).isEqualTo("a\\%b\\_c\\\\d e");
    }

    private List<String> search(String pattern) {
        return jdbc.queryForList(MATCHES, Map.of("q", pattern), String.class);
    }
}
