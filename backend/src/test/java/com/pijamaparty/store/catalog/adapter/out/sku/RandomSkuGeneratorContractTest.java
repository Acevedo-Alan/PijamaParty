package com.pijamaparty.store.catalog.adapter.out.sku;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.application.SkuGeneratorContractTest;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class RandomSkuGeneratorContractTest extends SkuGeneratorContractTest<RandomSkuGenerator> {

    @Override
    protected RandomSkuGenerator createSubject() {
        return new RandomSkuGenerator();
    }

    @Test
    void givenANewVariant_whenItsSkuIsGenerated_thenItIsPpAnd8CharactersWithoutLookAlikes() {
        assertThat(subject.newSku().value()).matches("PP-[0-9A-HJKMNP-TV-Z]{8}");
    }

    @Test
    void givenTheLowestAndHighestRandomValues_whenGenerating_thenTheFirstAndLastCharactersOfTheAlphabetAreUsed() {
        assertThat(new RandomSkuGenerator(fixed(0)).newSku().value()).isEqualTo("PP-00000000");
        assertThat(new RandomSkuGenerator(fixed(31)).newSku().value()).isEqualTo("PP-ZZZZZZZZ");
    }

    /** Always the same bounded value; the generator only asks for {@code nextInt(32)}. */
    private static RandomGenerator fixed(int value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int nextInt(int bound) {
                return value;
            }
        };
    }
}
