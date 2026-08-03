package com.wealthora.server.config;

import com.wealthora.server.security.SessionAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SessionAuthenticationFilter sessionAuthenticationFilter)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/verify-email",
                                "/api/auth/resend-verification",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/google/start",
                                "/api/auth/google/poll")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/auth/status",
                                "/api/auth/google/status",
                                "/api/auth/google/callback")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/api/auth/me",
                                "/api/auth/logout",
                                "/api/auth/logout-all",
                                "/api/auth/change-password",
                                "/api/auth/set-password",
                                "/api/auth/sessions/**")
                        .authenticated()
                        .requestMatchers("/api/speech/**")
                        .authenticated()
                        .requestMatchers("/api/finance/**")
                        .authenticated()
                        .requestMatchers("/api/admin/**")
                        .authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, failure) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"code\":\"AUTHENTICATION_REQUIRED\","
                                    + "\"message\":\"Authentication is required.\"}");
                        })
                        .accessDeniedHandler((request, response, failure) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"code\":\"ACCESS_DENIED\","
                                    + "\"message\":\"Access is denied.\"}");
                        }))
                .addFilterBefore(sessionAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .build();
    }

    @Bean
    UserDetailsService disabledDefaultUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(
                    "Username/password security is handled by Wealthora services.");
        };
    }
}
