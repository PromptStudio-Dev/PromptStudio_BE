package promptstudio.promptstudio.global.auth.dto;

import lombok.Getter;

@Getter
public class GoogleLoginRequest {
    private String code;
    private String redirectUri;   // 프론트 redirect uri
    private String codeVerifier;  // PKCE
}