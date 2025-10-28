package Com.Spacefinders.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Password encoder bean for encrypting passwords
     * Uses BCrypt hashing algorithm with strength 12
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Security filter chain configuration
     * Currently configured to permit all requests (no authentication required)
     * TODO: Implement JWT or session-based authentication in future sprints
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for stateless API (using JWT or session management)
                .csrf(csrf -> csrf.disable())

                // Configure CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Configure authorization
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/v1/api/user/addUser",
                                "/v1/api/user/loginUser",
                                "/v1/api/client/searchByFields",
                                "/v1/api/client/viewClickedProperty/**",
                                "/h2-console/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health"
                        ).permitAll()

                        // All other endpoints require authentication
                        // TODO: Implement role-based access control
                        .anyRequest().permitAll() // Changed to permitAll() for now - CHANGE THIS IN PRODUCTION
                )

                // Stateless session management (for JWT)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Allow H2 console frames
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                );

        return http.build();
    }

    /**
     * CORS configuration
     * Allows requests from the Angular frontend
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:4200"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/v1/api/**", configuration);
        return source;
    }

    /**
     * TODO: Future enhancements
     * 1. Implement JWT token generation and validation
     * 2. Add role-based authorization (@PreAuthorize)
     * 3. Implement refresh token mechanism
     * 4. Add rate limiting for login endpoint
     * 5. Implement account lockout after failed login attempts
     */
}