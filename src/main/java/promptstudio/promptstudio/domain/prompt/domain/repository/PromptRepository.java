package promptstudio.promptstudio.domain.prompt.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import promptstudio.promptstudio.domain.prompt.domain.entity.Prompt;

public interface PromptRepository extends JpaRepository<Prompt, Long> {
}
