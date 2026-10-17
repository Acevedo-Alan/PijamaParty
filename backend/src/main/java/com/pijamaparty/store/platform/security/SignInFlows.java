package com.pijamaparty.store.platform.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Lets a module add a sign-in that Spring Security itself drives, such as the return from Google or Facebook of
 * UC-27, to the single filter chain, without the platform knowing the module. It must open the session only through
 * {@link SessionAuthentication}, as the use-case sign-ins do: the session stores only the user id and the role.
 */
@FunctionalInterface
public interface SignInFlows {

    void configure(HttpSecurity http) throws Exception;
}
