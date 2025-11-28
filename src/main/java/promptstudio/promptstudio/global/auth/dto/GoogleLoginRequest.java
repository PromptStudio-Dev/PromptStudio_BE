package promptstudio.promptstudio.global.auth.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GoogleLoginRequest {
    private String code;
    private String redirectUri;   // 프론트 redirect uri
    private String codeVerifier;  // PKCE
}