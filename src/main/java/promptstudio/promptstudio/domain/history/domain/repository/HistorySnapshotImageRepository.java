package promptstudio.promptstudio.domain.history.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import promptstudio.promptstudio.domain.history.domain.entity.HistorySnapshotImage;

public interface HistorySnapshotImageRepository extends JpaRepository<HistorySnapshotImage, Long> {

}