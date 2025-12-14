package promptstudio.promptstudio.domain.chat.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class ChatSession {
    private String sessionId;
    private Long memberId;

    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public void addMessage(ChatMessage message) {
        this.messages.add(message);
    }
}