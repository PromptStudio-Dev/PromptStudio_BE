package promptstudio.promptstudio.domain.prompt.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PromptResponse {
    private Long memberId;
    private Long promptId;
    private String name;
    private String title;
    private String introduction;
    private String aiEnvironment;
    private String category;
    private String content;
    private String imageUrl;
    private String result;
    private boolean imageRequired;
    private long likeCount;
    private long copyCount;
    private long viewCount;
    private boolean liked;
    private LocalDateTime createdAt;
}
