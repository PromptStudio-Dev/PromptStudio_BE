package promptstudio.promptstudio.domain.likes.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import promptstudio.promptstudio.domain.likes.domain.entity.Likes;

public interface LikesRepository extends JpaRepository<Likes, Long> {

    @Query("""
        SELECT COUNT(l)
        FROM Likes l
        WHERE l.prompt.id = :promptId
    """)
    long countByPromptId(@Param("promptId") Long promptId);

    @Query("""
        SELECT COUNT(l) > 0
        FROM Likes l
        WHERE l.prompt.id = :promptId
          AND l.member.id = :memberId
    """)
    boolean existsByPromptIdAndMemberId(@Param("promptId") Long promptId,
                                        @Param("memberId") Long memberId);
}
