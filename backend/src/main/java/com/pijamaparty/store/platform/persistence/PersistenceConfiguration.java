package com.pijamaparty.store.platform.persistence;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class PersistenceConfiguration {

    /** Replaces Boot's JpaTransactionManager, so every {@code @Transactional} sets the RLS context (RNF-27). */
    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new RlsTransactionManager(entityManagerFactory);
    }
}
