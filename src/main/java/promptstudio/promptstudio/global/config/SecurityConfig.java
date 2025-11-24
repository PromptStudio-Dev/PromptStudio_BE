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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import promptstudio.promptstudio.global.jwt.JwtAuthenticationFilter;

import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth

                        // OPTIONS 요청 허용
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        
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
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 프로덕션 환경에서는 실제 프론트엔드 도메인으로 변경 필요
        configuration.setAllowedOriginPatterns(Arrays.asList("" +
                "http://localhost:5173",
                "https://www.promptstudio.kr"
        ));

        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        // Authorization 헤더를 노출하도록 설정 (JWT 토큰 사용 시 필요)
        configuration.setExposedHeaders(Arrays.asList("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
