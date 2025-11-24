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
import promptstudio.promptstudio.global.jwt.JwtAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // 조회, 검색 관련 경로
                        .requestMatchers(HttpMethod.GET, "/api/prompts").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/prompts/hot").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/prompts/search").permitAll()
                        // 상세조회 경로
                        .requestMatchers(
                                RegexRequestMatcher.regexMatcher("^/api/prompts/[0-9]+$")
                        ).permitAll()

                        // 복사 경로
                        .requestMatchers("/api/prompts/*/copy").permitAll()

                        // 로그인 관련 경로
                        .requestMatchers("/api/auth/google").permitAll()
                        .requestMatchers("/api/auth/reissue").permitAll()

                        // 상태 검사 경로
                        .requestMatchers("/actuator/**").permitAll()
                        // swagger 경로
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
}
