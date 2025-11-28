package promptstudio.promptstudio.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;
import promptstudio.promptstudio.global.jwt.JwtAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;  // WebConfig의 Bean 주입

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // WebConfig의 CORS 설정 재사용
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/prompts").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/prompts/hot").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/prompts/search").permitAll()
                        .requestMatchers(
                                RegexRequestMatcher.regexMatcher("^/api/prompts/[0-9]+$")
                        ).permitAll()

                        .requestMatchers("/api/prompts/*/copy").permitAll()

                        .requestMatchers("/api/auth/google").permitAll()
                        .requestMatchers("/api/auth/google/login").permitAll()
                        .requestMatchers("/api/auth/google/callback").permitAll()
                        .requestMatchers("/api/auth/reissue").permitAll()

                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        .anyRequest().authenticated()
                )

                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> res.sendError(401))
                        .accessDeniedHandler((req, res, ex) -> res.sendError(403))
                )

                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // SecurityConfig의 corsConfigurationSource Bean 제거 (WebConfig 것 사용)
}