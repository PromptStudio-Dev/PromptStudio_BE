package promptstudio.promptstudio.domain.history.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import promptstudio.promptstudio.domain.history.domain.entity.History;

import java.util.List;
import java.util.Optional;

public interface HistoryRepository extends JpaRepository<History, Long> {

    @Query("SELECT h FROM History h " +
            "LEFT JOIN FETCH h.snapshotImages " +
            "WHERE h.maker.id = :makerId " +
            "ORDER BY h.createdAt DESC")
    List<History> findAllByMakerIdWithImages(@Param("makerId") Long makerId);

    @Query("SELECT h FROM History h " +
            "LEFT JOIN FETCH h.snapshotImages " +
            "WHERE h.id = :historyId")
    Optional<History> findByIdWithImages(@Param("historyId") Long historyId);

    Page<History> findByMakerIdOrderByCreatedAtDesc(Long makerId, Pageable pageable);
}