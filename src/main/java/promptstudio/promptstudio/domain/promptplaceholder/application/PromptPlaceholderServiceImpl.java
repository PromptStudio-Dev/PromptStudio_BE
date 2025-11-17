package promptstudio.promptstudio.domain.promptplaceholder.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import promptstudio.promptstudio.domain.prompt.domain.entity.Prompt;
import promptstudio.promptstudio.domain.prompt.domain.repository.PromptRepository;
import promptstudio.promptstudio.domain.promptplaceholder.domain.entity.PromptPlaceholder;
import promptstudio.promptstudio.domain.promptplaceholder.domain.repository.PromptPlaceholderRepository;
import promptstudio.promptstudio.domain.promptplaceholder.dto.PromptPlaceholderResponse;
import promptstudio.promptstudio.global.exception.http.NotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PromptPlaceholderServiceImpl implements PromptPlaceholderService{

    private final PromptRepository promptRepository;
    private final PromptPlaceholderRepository promptPlaceholderRepository;

    @Override
    public PromptPlaceholderResponse getPromptPlaceholders(Long promptId) {

        Prompt prompt = promptRepository.findById(promptId).orElseThrow(
                () -> new NotFoundException("프롬프트가 존재하지 않습니다.")
        );

        List<PromptPlaceholder> placeholders =
                promptPlaceholderRepository.findByPromptId(promptId);

        List<String> placeholderNames = placeholders.stream()
                .map(PromptPlaceholder::getFieldName)
                .toList();

        PromptPlaceholderResponse response = new PromptPlaceholderResponse();
        response.setPromptId(prompt.getId());
        response.setContent(prompt.getContent());
        response.setImageRequired(prompt.isImageRequired());
        response.setPlaceholderRequired(true);
        response.setPlaceholders(placeholderNames);

        if (placeholders.isEmpty()) {
            response.setPlaceholderRequired(false);
        }

        return response;
    }
}
