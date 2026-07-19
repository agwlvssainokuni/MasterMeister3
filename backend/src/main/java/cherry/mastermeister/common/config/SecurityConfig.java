/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cherry.mastermeister.common.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Security 構成(NFR-U3-01/04)。
 * 単一チェーン・STATELESS・Bearer(JWT HS256)検証。CSRF は無効
 * (トークンは Cookie に置かず Authorization ヘッダーのみのため攻撃面が存在しない)。
 * セキュリティヘッダー(CSP ほか — H-06 本則)は API・静的配信の両方に適用する。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** CSP 本則(business-logic-model.md §3)。②の資産は外部参照ゼロのため 'self' のみで成立。 */
    public static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                    + "font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
            throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, exception) ->
                writeProblem(response, objectMapper, HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
        AccessDeniedHandler accessDeniedHandler = (request, response, exception) ->
                writeProblem(response, objectMapper, HttpStatus.FORBIDDEN, "FORBIDDEN");

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/registration/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny()));
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null
                    ? List.<GrantedAuthority>of()
                    : List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    private static void writeProblem(
            HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String code)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("code", code);
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    @Bean
    public JwtDecoder jwtDecoder(AppProperties properties) {
        SecretKeySpec key = new SecretKeySpec(
                properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtEncoder jwtEncoder(AppProperties properties) {
        SecretKeySpec key = new SecretKeySpec(
                properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
