package promptstudio.promptstudio.domain.chat.domain;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatMessage {
    private String role;      // "system", "user", "assistant"
    private String content;
    private List<String> images;  // user만 가질 수 있음

    public static ChatMessage system(String content) {
        return ChatMessage.builder()
                .role("system")
                .content(content)
                .build();
    }

    public static ChatMessage user(String content, List<String> images) {
        return ChatMessage.builder()
                .role("user")
                .content(content)
                .images(images)
                .build();
    }

    public static ChatMessage assistant(String content) {
        return ChatMessage.builder()
                .role("assistant")
                .content(content)
                .build();
    }
}