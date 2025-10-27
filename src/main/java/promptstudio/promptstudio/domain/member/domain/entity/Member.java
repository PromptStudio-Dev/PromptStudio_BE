package promptstudio.promptstudio.domain.member.domain.entity;

import promptstudio.promptstudio.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String name;

    @Column(length = 2048)
    private String profileImageUrl;

    @Column
    private String introduction;

    @Builder
    public Member(String name, String profileImageUrl, String introduction) {
        this.name = name;
        this.profileImageUrl = profileImageUrl;
        this.introduction = introduction;
    }

}