package promptstudio.promptstudio.domain.promptplaceholder.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import promptstudio.promptstudio.domain.promptplaceholder.domain.entity.PromptPlaceholder;

import java.util.List;

public interface PromptPlaceholderRepository extends JpaRepository<PromptPlaceholder, Long> {
    List<PromptPlaceholder> findByPromptId(Long promptId);
}
