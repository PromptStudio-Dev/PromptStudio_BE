package promptstudio.promptstudio.domain.history.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImageDownloadData {
    private String fileName;
    private byte[] imageBytes;
}