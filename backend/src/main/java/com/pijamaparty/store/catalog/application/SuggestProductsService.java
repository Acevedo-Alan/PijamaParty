package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.SearchText;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SuggestProductsService implements SuggestProducts {

    private final ProductSearchQuery search;

    SuggestProductsService(ProductSearchQuery search) {
        this.search = search;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        Optional<SearchText> text = SearchText.parse(query.text());
        if (text.isEmpty() || text.get().words().isEmpty()) {
            return new Result.Suggested(List.of());
        }
        return new Result.Suggested(search.suggest(text.get(), LIMIT));
    }
}
