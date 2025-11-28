package promptstudio.promptstudio.domain.maker.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MakerListResponse {
    private Long makerId;
    private String title;
    private String resultType;      // TEXT / IMAGE (hasHistory=true일 때)
    private String resultText;      // TEXT 결과
    private String resultImageUrl;  // IMAGE 결과
    private String content;         // 메이커 내용 (hasHistory=false일 때)
    private LocalDateTime updatedAt;
}