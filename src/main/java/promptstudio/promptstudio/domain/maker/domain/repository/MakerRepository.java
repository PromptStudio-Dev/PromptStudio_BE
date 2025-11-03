package promptstudio.promptstudio.domain.maker.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import promptstudio.promptstudio.domain.maker.domain.entity.Maker;

public interface MakerRepository extends JpaRepository<Maker, Long> {
}