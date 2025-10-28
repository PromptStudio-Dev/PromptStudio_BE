package promptstudio.promptstudio.domain.member.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import promptstudio.promptstudio.domain.member.domain.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
