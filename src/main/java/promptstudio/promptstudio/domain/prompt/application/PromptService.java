package promptstudio.promptstudio.domain.prompt.application;

import org.springframework.web.multipart.MultipartFile;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.prompt.dto.*;

import java.util.List;

public interface PromptService {
    Long createPrompt(Long memberId, PromptCreateRequest request, MultipartFile file);
    List<PromptCardNewsResponse> getAllPrompts(Long memberId, String category);
    List<PromptCardNewsResponse> getHotPrompts(Long memberId, String category);
    List<PromptCardNewsResponse> getLikedPrompts(Long memberId, String category);
    List<PromptCardNewsResponse> getMyPrompts(Long memberId, String category);
    PromptResponse getPromptDetail(Long memberId, Long promptId);
    List<PromptCardNewsResponse> searchPrompts(Long memberId, String category, String query);
    List<PromptCardNewsResponse> getViewedPrompts(Long memberId);
    PromptCopyResponse copyPrompt(Long promptId);
    PromptUpdateResponse updatePrompt(Long memberId, Long promptId, PromptUpdateRequest request);
}
