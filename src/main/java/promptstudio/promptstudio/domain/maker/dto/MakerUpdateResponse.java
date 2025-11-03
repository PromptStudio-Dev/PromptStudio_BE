package promptstudio.promptstudio.domain.maker.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MakerUpdateResponse {
    private Long makerId;
    private String title;
    private LocalDateTime updatedAt;
}