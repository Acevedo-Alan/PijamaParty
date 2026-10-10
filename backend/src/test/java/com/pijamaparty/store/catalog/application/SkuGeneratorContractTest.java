package com.pijamaparty.store.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.Sku;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Contract of {@link SkuGenerator}. */
public abstract class SkuGeneratorContractTest<T extends SkuGenerator> extends ContractTest<T> {

    @Test
    void givenManySkus_whenGenerated_thenTheyFitTheDatabaseAndDoNotRepeat() {
        List<Sku> skus = Stream.generate(subject::newSku).limit(1_000).toList();

        assertThat(skus).doesNotHaveDuplicates()
            .allSatisfy(sku -> assertThat(sku.value()).startsWith("PP-").hasSizeLessThanOrEqualTo(Sku.MAX_LENGTH));
    }
}
