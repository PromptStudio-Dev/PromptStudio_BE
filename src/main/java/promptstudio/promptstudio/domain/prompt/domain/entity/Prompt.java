package promptstudio.promptstudio.domain.prompt.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.global.common.entity.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prompt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Member member;

    @Column(nullable=false)
    private String title;

    @Column(nullable=false)
    private String introduction;

    @Column(columnDefinition="TEXT")
    private String content;

    @Column
    private String category;

    @Column(nullable=false)
    private boolean visible;

    @Column(length = 2048)
    private String imageUrl;

    @Column(columnDefinition="TEXT")
    private String result;

    @Column(nullable=false)
    private boolean imageRequired;

    @Column(nullable=false)
    private String aiEnvironment;

    @Column(nullable=false)
    private Integer copyCount;

    @Column(nullable=false)
    private Integer viewCount;

    @Builder
    public Prompt(Member member, String title,
                  String introduction, String content,
                  String category, boolean visible,
                  String imageUrl, String result,
                  boolean imageRequired, String aiEnvironment,
                  Integer copyCount, Integer viewCount) {
        this.member = member;
        this.title = title;
        this.introduction = introduction;
        this.content = content;
        this.visible = visible;
        this.imageUrl = imageUrl;
        this.result = result;
        this.imageRequired = imageRequired;
        this.aiEnvironment = aiEnvironment;
        this.copyCount = copyCount;
        this.viewCount = viewCount;
    }

}
