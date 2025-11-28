package promptstudio.promptstudio.domain.member.domain.entity;

import promptstudio.promptstudio.domain.prompt.dto.PromptUpdateRequest;
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

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(unique = true)
    private String email;

    @Column(length = 2048)
    private String profileImageUrl;

    @Column
    private String introduction;

    public void updateIntroduction(String introduction) {
        this.introduction = introduction;
    }

    @Builder
    public Member(String name,String email, String profileImageUrl, String introduction) {
        this.name = name;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.introduction = introduction;
    }

}