package promptstudio.promptstudio.domain.maker.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MakerImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maker_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Maker maker;

    @Column(length = 2048, nullable = false)
    private String imageUrl;

    @Column(nullable = false)
    private Integer orderIndex;

    @Builder
    public MakerImage(String imageUrl, Integer orderIndex) {
        this.imageUrl = imageUrl;
        this.orderIndex = orderIndex;
    }

    public void setMaker(Maker maker) {
        this.maker = maker;
    }
}