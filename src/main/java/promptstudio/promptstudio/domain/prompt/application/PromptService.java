package promptstudio.promptstudio.domain.prompt.application;

import org.springframework.web.multipart.MultipartFile;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.prompt.dto.PromptCreateRequest;

public interface PromptService {
    Long createPrompt(Long memberId, PromptCreateRequest request, MultipartFile file);
}
