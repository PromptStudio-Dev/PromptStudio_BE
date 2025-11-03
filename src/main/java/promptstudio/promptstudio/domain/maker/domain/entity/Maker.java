package promptstudio.promptstudio.domain.maker.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.global.common.entity.BaseEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Maker extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Member member;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @OneToMany(mappedBy = "maker", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MakerImage> images = new ArrayList<>();

    @Builder
    public Maker(Member member, String title, String content) {
        this.member = member;
        this.title = title == null || title.isBlank() ? "새로운 프롬프트" : title;
        this.content = content;
    }

    public void updateTitle(String title) {
        this.title = title == null || title.isBlank() ? "새로운 프롬프트" : title;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void addImage(MakerImage image) {
        this.images.add(image);
        image.setMaker(this);
    }

    public void clearImages() {
        this.images.clear();
    }
}