package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.platform.security.SignInFlows;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * UC-27 (T-E4-14): sign-in with Google and Facebook through Spring Security's {@code oauth2Login}, on the routes under
 * {@code /api} (Authorization Code with {@code state} and PKCE; for Google, the ID token with its {@code nonce}).
 * Nothing of the provider is kept: the authorized client (its access token) is never stored, and the provider's
 * authentication lives only for the request; {@link ExternalSignInHandlers} opens the customer's own session through
 * {@code SessionAuthentication}, which also changes the session id. The provider calls are bounded (RNF-21).
 */
@Component
class ExternalSignInFlow implements SignInFlows {

    /** The storefront's page; set so Spring Security never generates a login page of its own. */
    private static final String SIGN_IN_PAGE = "/cuenta/ingresar";

    private final ExternalProviderRegistrations registrations;
    private final ExternalSignInHandlers handlers;
    private final SimpleClientHttpRequestFactory requestFactory;

    ExternalSignInFlow(ExternalProviderRegistrations registrations, ExternalSignInHandlers handlers,
                       @Value("${store.accounts.oauth2.connect-timeout}") Duration connectTimeout,
                       @Value("${store.accounts.oauth2.read-timeout}") Duration readTimeout) {
        this.registrations = registrations;
        this.handlers = handlers;
        this.requestFactory = requestFactory(connectTimeout, readTimeout);
    }

    static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.oauth2Login(login -> login
            .loginPage(SIGN_IN_PAGE)
            .clientRegistrationRepository(registrations)
            .authorizedClientRepository(new NoAuthorizedClients())
            .authorizationEndpoint(authorization -> authorization.authorizationRequestResolver(requestResolver()))
            .redirectionEndpoint(redirection ->
                redirection.baseUri(ExternalProviderRegistrations.RETURN_BASE_URI + "/*"))
            .tokenEndpoint(token -> token.accessTokenResponseClient(tokenResponseClient()))
            .userInfoEndpoint(userInfo -> userInfo.userService(userService()).oidcUserService(oidcUserService()))
            .successHandler(handlers)
            .failureHandler(handlers)
            .withObjectPostProcessor(new ObjectPostProcessor<OAuth2LoginAuthenticationFilter>() {
                @Override
                public <O extends OAuth2LoginAuthenticationFilter> O postProcess(O filter) {
                    // SessionAuthentication changes the session id and the CSRF token, and only on a real sign-in.
                    filter.setSessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy());
                    // The provider's authentication lives for this request only: it never reaches the session.
                    filter.setSecurityContextRepository(new RequestAttributeSecurityContextRepository());
                    return filter;
                }
            }));
    }

    /** PKCE (S256) for both providers; a provider that is not enabled is simply not found ({@code 404}). */
    private OAuth2AuthorizationRequestResolver requestResolver() {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations,
            ExternalProviderRegistrations.AUTHORIZATION_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        String prefix = ExternalProviderRegistrations.AUTHORIZATION_BASE_URI + "/";
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                if (path.startsWith(prefix) && registrations.findByRegistrationId(path.substring(prefix.length()))
                    == null) {
                    return null;
                }
                return resolver.resolve(request);
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
                return registrations.findByRegistrationId(registrationId) == null
                    ? null : resolver.resolve(request, registrationId);
            }
        };
    }

    private RestClientAuthorizationCodeTokenResponseClient tokenResponseClient() {
        var client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(RestClient.builder()
            .requestFactory(requestFactory)
            .messageConverters(converters -> {
                converters.clear();
                converters.add(new FormHttpMessageConverter());
                converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
            })
            .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
            .build());
        return client;
    }

    /** Facebook's profile from its Graph API. */
    private DefaultOAuth2UserService userService() {
        var restTemplate = new RestTemplate(requestFactory);
        restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        var service = new DefaultOAuth2UserService();
        service.setRestOperations(restTemplate);
        return service;
    }

    /** Google's ID token already has the subject, the email, whether it is verified and the name. */
    private OidcUserService oidcUserService() {
        var service = new OidcUserService();
        service.setOauth2UserService(userService());
        service.setRetrieveUserInfo(request -> false);
        return service;
    }

    /** The provider's access token is used once, to read the profile, and never kept (UC-27). */
    private static final class NoAuthorizedClients implements OAuth2AuthorizedClientRepository {

        @Override
        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String registrationId,
                                                                         Authentication principal,
                                                                         HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
                                         HttpServletRequest request, HttpServletResponse response) {
            // Nothing is stored.
        }

        @Override
        public void removeAuthorizedClient(String registrationId, Authentication principal,
                                           HttpServletRequest request, HttpServletResponse response) {
            // Nothing was stored.
        }
    }
}
