package promptstudio.promptstudio.domain.prompt.application;

import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.prompt.dto.PromptCreateRequest;

public interface PromptService {
    void createPrompt(Member member, PromptCreateRequest request);
}
