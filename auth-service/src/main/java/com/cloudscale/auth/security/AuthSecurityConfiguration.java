package com.cloudscale.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AuthSecurityConfiguration {
    @Bean
    SecurityFilterChain authSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/**", "/actuator/health", "/actuator/info",
                                "/actuator/prometheus").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    JwtTokenService jwtTokenService(
            @Value("${cloudscale.jwt.issuer}") String issuer,
            @Value("${cloudscale.jwt.secret}") String secret,
            @Value("${cloudscale.jwt.ttl-seconds:900}") long ttlSeconds
    ) {
        return new JwtTokenService(issuer, secret, ttlSeconds);
    }
}
