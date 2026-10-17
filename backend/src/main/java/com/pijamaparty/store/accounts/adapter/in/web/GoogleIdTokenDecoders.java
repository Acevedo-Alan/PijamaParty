package com.pijamaparty.store.accounts.adapter.in.web;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * UC-27 (T-E4-14): validates Google's ID token as Spring Security's own factory does (signature with Google's keys,
 * {@code iss}, {@code aud}, expiry; the {@code nonce} is checked by {@code oauth2Login}), but downloads the keys with the
 * bounded timeouts of RNF-21. {@code oauth2Login} uses this bean for every OpenID Connect provider.
 */
@Component
class GoogleIdTokenDecoders implements JwtDecoderFactory<ClientRegistration> {

    private final RestTemplate keys;
    private final Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();

    GoogleIdTokenDecoders(@Value("${store.accounts.oauth2.connect-timeout}") Duration connectTimeout,
                          @Value("${store.accounts.oauth2.read-timeout}") Duration readTimeout) {
        this.keys = new RestTemplate(ExternalSignInFlow.requestFactory(connectTimeout, readTimeout));
    }

    @Override
    public JwtDecoder createDecoder(ClientRegistration registration) {
        return decoders.computeIfAbsent(registration.getRegistrationId(), id -> decoder(registration));
    }

    private JwtDecoder decoder(ClientRegistration registration) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(registration.getProviderDetails().getJwkSetUri())
            .restOperations(keys)
            .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(),
            new OidcIdTokenValidator(registration)));
        decoder.setClaimSetConverter(OidcIdTokenDecoderFactory.createDefaultClaimTypeConverter());
        return decoder;
    }
}
