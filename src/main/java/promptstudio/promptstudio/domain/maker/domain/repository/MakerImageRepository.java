package promptstudio.promptstudio.domain.maker.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import promptstudio.promptstudio.domain.maker.domain.entity.MakerImage;

public interface MakerImageRepository extends JpaRepository<MakerImage, Long> {
}