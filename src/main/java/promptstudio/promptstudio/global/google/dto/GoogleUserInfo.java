package promptstudio.promptstudio.global.google.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoogleUserInfo {
    private String providerId;
    private String email;
    private String name;
    private String picture;
}
