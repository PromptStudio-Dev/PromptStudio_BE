package promptstudio.promptstudio.domain.prompt.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PromptCardNewsResponse {
    private Long promptId;
    private Long memberId;
    private String category;
    private String aiEnvironment;
    private String title;
    private String introduction;
    private String imageUrl;
    private boolean liked;
    private long likeCount;

    public PromptCardNewsResponse(
            Long promptId,
            Long memberId,
            String category,
            String aiEnvironment,
            String title,
            String introduction,
            String imageUrl,
            boolean liked,
            long likeCount
    ) {
        this.promptId = promptId;
        this.memberId = memberId;
        this.category = category;
        this.aiEnvironment = aiEnvironment;
        this.title = title;
        this.introduction = introduction;
        this.imageUrl = imageUrl;
        this.liked = liked;
        this.likeCount = likeCount;
    }
}
