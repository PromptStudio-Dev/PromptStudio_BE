package promptstudio.promptstudio.domain.viewrecord.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import promptstudio.promptstudio.domain.viewrecord.domain.entity.ViewRecord;

import java.util.Optional;

public interface ViewRecordRepository extends JpaRepository<ViewRecord, Long> {

    @Modifying
    @Query("""
    update ViewRecord vr
    set vr.updatedAt = CURRENT_TIMESTAMP
    where vr.member.id = :memberId
      and vr.prompt.id = :promptId
    """)
    int touchIfExists(@Param("memberId") Long memberId,
                      @Param("promptId") Long promptId);

}
