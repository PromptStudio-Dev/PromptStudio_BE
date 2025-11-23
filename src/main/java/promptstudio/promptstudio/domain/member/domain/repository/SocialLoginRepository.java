package promptstudio.promptstudio.domain.member.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import promptstudio.promptstudio.domain.member.domain.entity.SocialLogin;
import promptstudio.promptstudio.domain.member.domain.entity.SocialProvider;

import java.util.Optional;

public interface SocialLoginRepository extends JpaRepository<SocialLogin, Long> {
    Optional<SocialLogin> findByProviderAndProviderId(SocialProvider provider, String providerId);
}
