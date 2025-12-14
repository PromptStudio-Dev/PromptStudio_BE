package promptstudio.promptstudio.domain.chat.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatImageDownloadData {
    private String fileName;
    private byte[] imageBytes;
}