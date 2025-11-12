package promptstudio.promptstudio.domain.history.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import promptstudio.promptstudio.domain.maker.domain.entity.Maker;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class History {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maker_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Maker maker;

    @Column(length = 255)
    private String snapshotTitle;

    @Column(columnDefinition = "TEXT")
    private String snapshotContent;

    @OneToMany(mappedBy = "history", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HistorySnapshotImage> snapshotImages = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultType resultType;

    @Column(columnDefinition = "TEXT")
    private String resultText;

    @Column(length = 2048)
    private String resultImageUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public History(Maker maker, String snapshotTitle, String snapshotContent,
                   ResultType resultType, String resultText, String resultImageUrl) {
        this.maker = maker;
        this.snapshotTitle = snapshotTitle;
        this.snapshotContent = snapshotContent;
        this.resultType = resultType;
        this.resultText = resultText;
        this.resultImageUrl = resultImageUrl;
    }

    public void addSnapshotImage(HistorySnapshotImage image) {
        this.snapshotImages.add(image);
        image.setHistory(this);
    }
}