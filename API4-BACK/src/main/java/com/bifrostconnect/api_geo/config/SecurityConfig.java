package com.bifrostconnect.api_geo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().permitAll()
            )
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        @SuppressWarnings("deprecation")
        UserDetails operador = User.withDefaultPasswordEncoder()
                .username("operador")
                .password("Troca123")
                .roles("OPERADOR")
                .build();

        @SuppressWarnings("deprecation")
        UserDetails auditor = User.withDefaultPasswordEncoder()
                .username("auditor")
                .password("Troca123")
                .roles("AUDITOR")
                .build();

        @SuppressWarnings("deprecation")
        UserDetails analista = User.withDefaultPasswordEncoder()
                .username("analista")
                .password("Troca123")
                .roles("ANALISTA")
                .build();

        @SuppressWarnings("deprecation")
        UserDetails admin = User.withDefaultPasswordEncoder()
                .username("admin")
                .password("Troca123")
                .roles("ADMINISTRADOR")
                .build();

        return new InMemoryUserDetailsManager(operador, auditor, analista, admin);
    }
}
