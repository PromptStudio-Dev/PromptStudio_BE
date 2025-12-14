package promptstudio.promptstudio.domain.chat.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatSendResponse {
    private String resultType;  // "TEXT" or "IMAGE"
    private String content;
    private String imageUrl;
}