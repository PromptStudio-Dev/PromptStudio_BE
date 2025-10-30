package promptstudio.promptstudio.domain.userinput.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import promptstudio.promptstudio.domain.userinput.domain.entity.PromptPlaceholder;

public interface PromptPlaceholderRepository extends JpaRepository<PromptPlaceholder, Long> {
}
