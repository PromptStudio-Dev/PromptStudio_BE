package promptstudio.promptstudio.domain.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.member.domain.entity.SocialLogin;
import promptstudio.promptstudio.domain.member.domain.entity.SocialProvider;
import promptstudio.promptstudio.domain.member.domain.repository.MemberRepository;
import promptstudio.promptstudio.domain.member.domain.repository.SocialLoginRepository;
import promptstudio.promptstudio.global.google.dto.GoogleUserInfo;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberServiceImpl implements MemberService {

    private final SocialLoginRepository socialLoginRepository;
    private final MemberRepository memberRepository;

    @Override
    public Member findOrCreateMember(SocialProvider provider, GoogleUserInfo info) {
        return socialLoginRepository
                .findByProviderAndProviderId(provider, info.getProviderId())
                .map(SocialLogin::getMember)
                .orElseGet(() -> {
                    Member member = memberRepository.save(
                            Member.builder()
                                    .name(info.getName() != null ? info.getName() : "unknown")
                                    .email(info.getEmail())
                                    .profileImageUrl(info.getPicture())
                                    .introduction(null)
                                    .build()
                    );

                    socialLoginRepository.save(
                            SocialLogin.builder()
                                    .provider(provider)
                                    .providerId(info.getProviderId())
                                    .member(member)
                                    .build()
                    );

                    return member;
                });
    }
}
