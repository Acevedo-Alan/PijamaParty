package com.pijamaparty.store.activity.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FieldChangesTest {

    @Test
    void givenValuesThatDiffer_whenAdded_thenTheFieldIsKeptWithBoth() {
        FieldChanges changes = new FieldChanges().add("price", "$20.000", "$22.000");

        assertThat(changes.list()).containsExactly(FieldChange.of("price", "$20.000", "$22.000"));
    }

    @Test
    void givenTheSameValueOrTwoEmptyOnes_whenAdded_thenNothingIsKept() {
        FieldChanges changes = new FieldChanges().add("price", "$20.000", "$20.000").add("body", "", null);

        assertThat(changes.isEmpty()).isTrue();
    }

    @Test
    void givenAnEmptyText_whenAdded_thenItIsNoValue() {
        FieldChanges changes = new FieldChanges().add("body", "", "Para dormir bien.");

        assertThat(changes.list()).containsExactly(FieldChange.of("body", null, "Para dormir bien."));
    }

    @Test
    void givenAChangedSetWithTheSameCount_whenAddedWhenChanged_thenItIsKeptAnyway() {
        FieldChanges changes = new FieldChanges().addWhen(true, "scopeProducts", "2", "2")
            .addWhen(false, "scopeCategories", "1", "1");

        assertThat(changes.list()).containsExactly(FieldChange.of("scopeProducts", "2", "2"));
    }
}
