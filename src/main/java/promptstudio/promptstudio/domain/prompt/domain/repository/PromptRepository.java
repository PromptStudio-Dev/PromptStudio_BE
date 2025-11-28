package promptstudio.promptstudio.domain.prompt.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import promptstudio.promptstudio.domain.prompt.domain.entity.Prompt;
import promptstudio.promptstudio.domain.prompt.dto.PromptCardNewsResponse;

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
        and (:category = '전체' or p.category = :category)
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
    List<PromptCardNewsResponse> findAllOrderByLikeCountDescWithCategory(
            @Param("memberId") Long memberId,
            @Param("category") String category
    );

    @Query("""
    select new promptstudio.promptstudio.domain.prompt.dto.PromptCardNewsResponse(
        p.id,
        p.member.id,
        p.category,
        p.aiEnvironment,
        p.title,
        p.introduction,
        p.imageUrl,
        false,
        count(l.id)
    )
    from Prompt p
    left join Likes l
        on l.prompt.id = p.id
    where p.visible = true
      and (:category = '전체' or p.category = :category)
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
    List<PromptCardNewsResponse> findAllOrderByLikeCountDescGuestWithCategory(
            @Param("category") String category
    );


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
          and (:category = '전체' or p.category = :category)
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
    Page<PromptCardNewsResponse> findWeeklyTopPromptsWithCategory(
            @Param("memberId") Long memberId,
            @Param("since") LocalDateTime since,
            @Param("category") String category,
            Pageable pageable
    );

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
          and (:category = '전체' or p.category = :category)
        group by p.id,
                 p.member.id,
                 p.category,
                 p.aiEnvironment,
                 p.title,
                 p.introduction,
                 p.imageUrl,
                 p.createdAt
        order by p.createdAt desc
    """)
    List<PromptCardNewsResponse> findAllOrderByCreatedAtDescWithCategory(
            @Param("memberId") Long memberId,
            @Param("category") String category
    );

    @Query("""
        select new promptstudio.promptstudio.domain.prompt.dto.PromptCardNewsResponse(
            p.id,
            p.member.id,
            p.category,
            p.aiEnvironment,
            p.title,
            p.introduction,
            p.imageUrl,
            false,
            count(l.id)
        )
        from Prompt p
        left join Likes l
            on l.prompt.id = p.id
        where p.visible = true
          and (:category = '전체' or p.category = :category)
        group by p.id,
                 p.member.id,
                 p.category,
                 p.aiEnvironment,
                 p.title,
                 p.introduction,
                 p.imageUrl,
                 p.createdAt
        order by p.createdAt desc
    """)
    List<PromptCardNewsResponse> findAllOrderByCreatedAtDescGuestWithCategory(
            @Param("category") String category
    );


    @Query("""
        select new promptstudio.promptstudio.domain.prompt.dto.PromptCardNewsResponse(
            p.id,
            p.member.id,
            p.category,
            p.aiEnvironment,
            p.title,
            p.introduction,
            p.imageUrl,
            false,
            count(lThisWeek.id)
        )
        from Prompt p
        left join Likes lThisWeek
            on lThisWeek.prompt.id = p.id
           and lThisWeek.createdAt >= :since
        where p.visible = true
          and (:category = '전체' or p.category = :category)
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
    Page<PromptCardNewsResponse> findWeeklyTopPromptsGuestWithCategory(
            @Param("since") LocalDateTime since,
            @Param("category") String category,
            Pageable pageable
    );

    @Query("""
        select new promptstudio.promptstudio.domain.prompt.dto.PromptCardNewsResponse(
            p.id,
            p.member.id,
            p.category,
            p.aiEnvironment,
            p.title,
            p.introduction,
            p.imageUrl,
            true,
            count(lAll.id)
        )
        from Likes lMine
        join lMine.prompt p
        left join Likes lAll
            on lAll.prompt.id = p.id
        where lMine.member.id = :memberId
          and p.visible = true
          and (:category = '전체' or p.category = :category)
        group by p.id,
                 p.member.id,
                 p.category,
                 p.aiEnvironment,
                 p.title,
                 p.introduction,
                 p.imageUrl,
                 lMine.createdAt,
                 p.createdAt
        order by lMine.createdAt desc
    """)
    List<PromptCardNewsResponse> findLikedPromptsByMemberId(
            @Param("memberId") Long memberId,
            @Param("category") String category
    );

    @Query("""
        select new promptstudio.promptstudio.domain.prompt.dto.PromptCardNewsResponse(
            p.id,
            p.member.id,
            p.category,
            p.aiEnvironment,
            p.title,
            p.introduction,
            p.imageUrl,
            case when max(case when lMine.id is not null then 1 else 0 end) = 1
                 then true else false end,
            count(lAll.id)
        )
        from Prompt p
        left join Likes lAll
            on lAll.prompt.id = p.id
        left join Likes lMine
            on lMine.prompt.id = p.id
           and lMine.member.id = :memberId
        where p.member.id = :memberId
          and (:visible is null or p.visible = :visible)
          and (:category = '전체' or p.category = :category)
        group by p.id,
                 p.member.id,
                 p.category,
                 p.aiEnvironment,
                 p.title,
                 p.introduction,
                 p.imageUrl,
                 p.createdAt
        order by p.createdAt desc
    """)
    List<PromptCardNewsResponse> findMyPromptsWithCategory(
            @Param("memberId") Long memberId,
            @Param("category") String category,
            @Param("visible") Boolean visible
    );



    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query("""
        UPDATE Prompt p
        SET p.viewCount = p.viewCount + 1
        WHERE p.id = :promptId
    """)
    void increaseViewCount(@Param("promptId") Long promptId);

    @Query("""
    select new promptstudio.promptstudio.domain.prompt.dto.PromptCardNewsResponse(
        p.id,
        p.member.id,
        p.category,
        p.aiEnvironment,
        p.title,
        p.introduction,
        p.imageUrl,
        case when max(case when lMine.id is not null then 1 else 0 end) = 1
             then true else false end,
        count(lAll.id)
    )
    from Prompt p
    left join Likes lAll
        on lAll.prompt.id = p.id
    left join Likes lMine
        on lMine.prompt.id = p.id
       and lMine.member.id = :memberId
    where p.id in :promptIds
      and p.visible = true
      and (:category = '전체' or p.category = :category)
    group by p.id,
             p.member.id,
             p.category,
             p.aiEnvironment,
             p.title,
             p.introduction,
             p.imageUrl,
             p.createdAt
    """)
    List<PromptCardNewsResponse> findPromptsByIdsWithCategory(
            @Param("promptIds") List<Long> promptIds,
            @Param("memberId") Long memberId,
            @Param("category") String category
    );

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
    from ViewRecord vr
    join vr.prompt p
    left join Likes l
        on l.prompt.id = p.id
    left join Likes l2
        on l2.prompt.id = p.id
       and l2.member.id = :memberId
    where vr.member.id = :memberId
      and p.visible = true
    group by p.id,
             p.member.id,
             p.category,
             p.aiEnvironment,
             p.title,
             p.introduction,
             p.imageUrl,
             vr.updatedAt
    order by max(vr.updatedAt) desc
    """)
    List<PromptCardNewsResponse> findRecentViewedCards(
            @Param("memberId") Long memberId,
            Pageable pageable
    );


}
