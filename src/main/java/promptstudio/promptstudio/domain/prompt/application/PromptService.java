package promptstudio.promptstudio.domain.prompt.application;

import org.springframework.web.multipart.MultipartFile;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.prompt.dto.PromptCardNewsResponse;
import promptstudio.promptstudio.domain.prompt.dto.PromptCreateRequest;

import java.util.List;

public interface PromptService {
    Long createPrompt(Long memberId, PromptCreateRequest request, MultipartFile file);
    List<PromptCardNewsResponse> getAllPrompts(Long memberId);
    List<PromptCardNewsResponse> getHotPrompts(Long memberId);
    List<PromptCardNewsResponse> getLikedPrompts(Long memberId);
}
