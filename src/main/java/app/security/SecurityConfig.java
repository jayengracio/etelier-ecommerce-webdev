package app.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/",
                                "/products",
                                "/details/**",
                                "/login",
                                "/register",
                                "/stock",
                                "/forbid",
                                "/error",
                                "/stripe/webhook", // Stripe has no login; the signature check is the auth
                                "/app.css",
                                "/code.js",
                                "/images/**")
                        .permitAll()
                        .requestMatchers("/owners", "/product/add", "/edit/**", "/view/orders", "/orders/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                // Stripe can't send our CSRF token, so CSRF stays on for every other path
                .csrf(csrf -> csrf.ignoringRequestMatchers("/stripe/webhook"))
                .formLogin(form -> form.loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .exceptionHandling(ex -> ex.accessDeniedPage("/forbid"));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
