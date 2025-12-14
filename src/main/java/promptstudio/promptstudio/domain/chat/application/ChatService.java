package promptstudio.promptstudio.domain.chat.application;

import promptstudio.promptstudio.domain.chat.dto.*;

public interface ChatService {

    ChatStartResponse startChat(Long memberId, ChatStartRequest request);

    ChatSendResponse sendMessage(Long memberId, ChatSendRequest request);
}