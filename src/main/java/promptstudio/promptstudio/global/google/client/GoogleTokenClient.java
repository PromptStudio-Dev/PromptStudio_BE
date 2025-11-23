package promptstudio.promptstudio.global.google.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import promptstudio.promptstudio.global.google.dto.GoogleTokenResponse;

@Component
@RequiredArgsConstructor
public class GoogleTokenClient {

    private final WebClient webClient = WebClient.builder().build();

    @Value("${google.token-uri}")
    private String tokenUri;

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    public GoogleTokenResponse exchangeCode(String code, String redirectUri, String codeVerifier) {

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        if (codeVerifier != null && !codeVerifier.isBlank()) {
            form.add("code_verifier", codeVerifier);
        }

        return webClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(GoogleTokenResponse.class)
                .block();
    }
}
