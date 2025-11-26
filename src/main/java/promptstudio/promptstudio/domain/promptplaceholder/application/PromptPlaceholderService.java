package promptstudio.promptstudio.domain.promptplaceholder.application;

import promptstudio.promptstudio.domain.promptplaceholder.dto.PromptPlaceholderResponse;

public interface PromptPlaceholderService {
    PromptPlaceholderResponse getPromptPlaceholders(Long promptId);
}
