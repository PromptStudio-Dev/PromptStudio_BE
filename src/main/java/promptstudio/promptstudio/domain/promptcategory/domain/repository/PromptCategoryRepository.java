package promptstudio.promptstudio.domain.promptcategory.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import promptstudio.promptstudio.domain.promptcategory.domain.entity.PromptCategory;

public interface PromptCategoryRepository extends JpaRepository<PromptCategory, Long> {
}
