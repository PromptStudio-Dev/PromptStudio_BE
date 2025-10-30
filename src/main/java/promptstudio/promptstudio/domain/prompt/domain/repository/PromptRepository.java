package promptstudio.promptstudio.domain.prompt.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import promptstudio.promptstudio.domain.prompt.domain.entity.Prompt;
import promptstudio.promptstudio.domain.prompt.dto.PromptCardNewsResponse;

import java.util.List;

public interface PromptRepository extends JpaRepository<Prompt, Long> {
    @Query("""
        select new promptstudio.promptstudio.domain.prompt.dto.PromptCardNewsResponse(
            p.id,
            p.member.id,
            p.category,
            p.aiEnvironment,
            p.title,
            p.introduction,
            p.imageUrl,
            case when max(case when l2.id is not null then 1 else 0 end) = 1
                 then true else false end,
            count(l.id)
        )
        from Prompt p
        left join Likes l
            on l.prompt.id = p.id
        left join Likes l2
            on l2.prompt.id = p.id
           and l2.member.id = :memberId
        where p.visible = true
        group by p.id,
                 p.member.id,
                 p.category,
                 p.aiEnvironment,
                 p.title,
                 p.introduction,
                 p.imageUrl,
                 p.createdAt
        order by count(l.id) desc, p.createdAt desc
    """)
    List<PromptCardNewsResponse> findAllOrderByLikeCountDesc(@Param("memberId") Long memberId);

    @Query("""
    select new promptstudio.promptstudio.domain.prompt.dto.PromptCardNewsResponse(
        p.id,
        p.member.id,
        p.category,
        p.aiEnvironment,
        p.title,
        p.introduction,
        p.imageUrl,
        case when max(case when l2.id is not null then 1 else 0 end) = 1
             then true else false end,
        count(lThisWeek.id)
    )
    from Prompt p
    left join Likes lThisWeek
        on lThisWeek.prompt.id = p.id
       and lThisWeek.createdAt >= :since
    left join Likes l2
        on l2.prompt.id = p.id
       and l2.member.id = :memberId
    where p.visible = true
    group by p.id,
             p.member.id,
             p.category,
             p.aiEnvironment,
             p.title,
             p.introduction,
             p.imageUrl,
             p.createdAt
    order by count(lThisWeek.id) desc, p.createdAt desc
""")
    Page<PromptCardNewsResponse> findWeeklyTopPrompts(
            @Param("memberId") Long memberId,
            @Param("since") java.time.LocalDateTime since,
            Pageable pageable
    );
}
