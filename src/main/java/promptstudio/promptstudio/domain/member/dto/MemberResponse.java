package promptstudio.promptstudio.domain.member.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberResponse {
    private String name;
    private String profileImageUrl;
    private String introduction;
}
