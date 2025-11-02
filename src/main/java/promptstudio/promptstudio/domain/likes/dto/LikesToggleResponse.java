package promptstudio.promptstudio.domain.likes.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LikesToggleResponse {
    private boolean liked;
    private long likeCount;
}
