package promptstudio.promptstudio.domain.maker.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import promptstudio.promptstudio.domain.maker.domain.entity.Maker;

import java.util.Optional;

public interface MakerRepository extends JpaRepository<Maker, Long> {

    @Query("SELECT m FROM Maker m LEFT JOIN FETCH m.images WHERE m.id = :makerId")
    Optional<Maker> findByIdWithImages(@Param("makerId") Long makerId);

    // History가 있는 Maker 조회 (페이징)
    @Query("SELECT DISTINCT m FROM Maker m " +
            "WHERE m.member.id = :memberId " +
            "AND EXISTS (SELECT h FROM History h WHERE h.maker = m) " +
            "ORDER BY m.updatedAt DESC")
    Page<Maker> findByMemberIdWithHistory(@Param("memberId") Long memberId, Pageable pageable);

    // History가 없는 Maker 조회 (페이징)
    @Query("SELECT m FROM Maker m " +
            "WHERE m.member.id = :memberId " +
            "AND NOT EXISTS (SELECT h FROM History h WHERE h.maker = m) " +
            "ORDER BY m.updatedAt DESC")
    Page<Maker> findByMemberIdWithoutHistory(@Param("memberId") Long memberId, Pageable pageable);
}