package promptstudio.promptstudio.domain.chat.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class ChatSession {
    private final String sessionId;
    private final Long memberId;

    @Setter
    private String lastGeneratedImageUrl;

    @Builder.Default
    private final List<ChatMessage> messages = new ArrayList<>();

    public void addMessage(ChatMessage message) {
        messages.add(message);
    }
}