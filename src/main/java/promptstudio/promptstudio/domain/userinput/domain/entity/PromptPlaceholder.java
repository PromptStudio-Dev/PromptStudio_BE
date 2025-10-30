package promptstudio.promptstudio.domain.userinput.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import promptstudio.promptstudio.domain.prompt.domain.entity.Prompt;
import promptstudio.promptstudio.global.common.entity.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromptPlaceholder extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prompt_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Prompt prompt;

    @Column(nullable=false)
    private String fieldName;

    @Builder
    public PromptPlaceholder(Prompt prompt, String fieldName) {
        this.prompt = prompt;
        this.fieldName = fieldName;
    }
}
