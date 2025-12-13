package promptstudio.promptstudio.domain.history.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImageDownloadResponse {
    private Long historyId;
    private String downloadUrl;
    private String fileName;
}