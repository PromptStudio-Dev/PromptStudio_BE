package promptstudio.promptstudio.global.google.client;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import promptstudio.promptstudio.global.google.dto.GoogleUserInfo;

import java.util.Collections;

@Component
public class GoogleIdTokenVerifierClient {

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdTokenVerifierClient(@Value("${google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                JacksonFactory.getDefaultInstance()
        )
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleUserInfo verify(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) throw new IllegalArgumentException("Invalid Google ID Token");

            GoogleIdToken.Payload payload = token.getPayload();

            return GoogleUserInfo.builder()
                    .providerId(payload.getSubject())
                    .email(payload.getEmail())
                    .name((String) payload.get("name"))
                    .picture((String) payload.get("picture"))
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Google token verification failed", e);
        }
    }
}
