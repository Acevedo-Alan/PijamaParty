package com.pijamaparty.store.platform.testing;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.payments.adapter.out.mercadopago.MercadoPagoStubConfiguration;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;

/**
 * Whole application behind MockMvc, with the real security filter chain, on Testcontainers PostgreSQL and with
 * MercadoPago replaced by a local stub ({@code MercadoPagoStub}). Add test-only routes with {@code @Import} of a
 * {@code @TestConfiguration}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MercadoPagoStubConfiguration.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public @interface ApiTest {
}
