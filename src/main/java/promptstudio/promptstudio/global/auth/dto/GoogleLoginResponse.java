package promptstudio.promptstudio.global.auth.dto;

import lombok.*;

@Getter
@Setter
public class GoogleLoginResponse {
    private Long memberId;
    private String accessToken;
    private String refreshToken;
}
