package promptstudio.promptstudio.domain.maker.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import promptstudio.promptstudio.domain.maker.domain.entity.Maker;

import java.util.Optional;

public interface MakerRepository extends JpaRepository<Maker, Long> {

    @Query("SELECT m FROM Maker m LEFT JOIN FETCH m.images WHERE m.id = :makerId")
    Optional<Maker> findByIdWithImages(@Param("makerId") Long makerId);
}