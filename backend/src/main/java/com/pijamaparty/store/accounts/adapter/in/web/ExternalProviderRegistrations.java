package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.accounts.domain.ExternalProvider;
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

/**
 * UC-27 (T-E4-14): the providers a customer can sign in with, each one enabled by its client id (RNF-29.10). The
 * registration id is the provider in lower case, the last segment of both routes. Google is OpenID Connect; Facebook
 * is OAuth2 with its Graph API, at the configured version. The redirect URI is built on the storefront's origin, the
 * one registered at each provider, so it never depends on the request's host.
 */
@Component
class ExternalProviderRegistrations implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    static final String AUTHORIZATION_BASE_URI = "/api/account/oauth2/authorization";
    static final String RETURN_BASE_URI = "/api/account/oauth2/code";

    private final Map<String, ClientRegistration> registrations = new LinkedHashMap<>();

    ExternalProviderRegistrations(@Value("${store.public-url}") URI publicUrl,
                                  @Value("${store.accounts.oauth2.google.client-id:}") String googleClientId,
                                  @Value("${store.accounts.oauth2.google.client-secret:}") String googleClientSecret,
                                  @Value("${store.accounts.oauth2.facebook.client-id:}") String facebookClientId,
                                  @Value("${store.accounts.oauth2.facebook.client-secret:}")
                                  String facebookClientSecret,
                                  @Value("${store.accounts.oauth2.facebook.graph-version}") String graphVersion,
                                  @Value("${store.accounts.oauth2.google.token-uri}") String googleTokenUri,
                                  @Value("${store.accounts.oauth2.google.jwk-set-uri}") String googleJwkSetUri,
                                  @Value("${store.accounts.oauth2.facebook.graph-url}") String graphUrl) {
        String redirectUri = publicUrl.toString().replaceAll("/+$", "") + RETURN_BASE_URI + "/{registrationId}";
        if (!googleClientId.isBlank()) {
            add(CommonOAuth2Provider.GOOGLE.getBuilder(registrationId(ExternalProvider.GOOGLE))
                .clientId(googleClientId.strip())
                .clientSecret(googleClientSecret.strip())
                .redirectUri(redirectUri)
                .scope("openid", "profile", "email")
                .tokenUri(googleTokenUri)
                .jwkSetUri(googleJwkSetUri)
                .build());
        }
        if (!facebookClientId.isBlank()) {
            add(CommonOAuth2Provider.FACEBOOK.getBuilder(registrationId(ExternalProvider.FACEBOOK))
                .clientId(facebookClientId.strip())
                .clientSecret(facebookClientSecret.strip())
                .redirectUri(redirectUri)
                .scope("public_profile", "email")
                .authorizationUri("https://www.facebook.com/" + graphVersion + "/dialog/oauth")
                .tokenUri(graphUrl + "/" + graphVersion + "/oauth/access_token")
                .userInfoUri(graphUrl + "/" + graphVersion + "/me?fields=id,name,email")
                .build());
        }
    }

    private void add(ClientRegistration registration) {
        registrations.put(registration.getRegistrationId(), registration);
    }

    static String registrationId(ExternalProvider provider) {
        return provider.name().toLowerCase(Locale.ROOT);
    }

    /** The provider of a registration id; empty for anything else. */
    static Optional<ExternalProvider> provider(String registrationId) {
        for (ExternalProvider provider : ExternalProvider.values()) {
            if (registrationId(provider).equals(registrationId)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }

    /** The enabled providers, so the storefront shows only the buttons that work. */
    List<ExternalProvider> enabled() {
        return registrations.keySet().stream().flatMap(id -> provider(id).stream()).toList();
    }

    /** @return null when that provider is not enabled, as the interface asks */
    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return registrations.get(registrationId);
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
        return registrations.values().iterator();
    }
}
