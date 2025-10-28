package promptstudio.promptstudio.domain.promptcategory.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import promptstudio.promptstudio.domain.category.domain.entity.Category;
import promptstudio.promptstudio.domain.prompt.domain.entity.Prompt;
import promptstudio.promptstudio.global.common.entity.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"prompt_id","category_id"}))
public class PromptCategory extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prompt_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Prompt prompt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Category category;

    @Builder
    public PromptCategory(Prompt prompt, Category category) {
        this.prompt = prompt;
        this.category = category;
    }
}
