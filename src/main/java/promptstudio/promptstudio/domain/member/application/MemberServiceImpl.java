package promptstudio.promptstudio.domain.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.member.domain.entity.SocialLogin;
import promptstudio.promptstudio.domain.member.domain.entity.SocialProvider;
import promptstudio.promptstudio.domain.member.domain.repository.MemberRepository;
import promptstudio.promptstudio.domain.member.domain.repository.SocialLoginRepository;
import promptstudio.promptstudio.domain.member.dto.MemberIntroductionUpdateRequest;
import promptstudio.promptstudio.domain.member.dto.MemberIntroductionUpdateResponse;
import promptstudio.promptstudio.domain.member.dto.MemberResponse;
import promptstudio.promptstudio.global.exception.http.NotFoundException;
import promptstudio.promptstudio.global.google.dto.GoogleUserInfo;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
                                    .profileImageUrl(pickDefaultProfileImage())
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

    @Override
    public MemberResponse getMember(Long memberId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("멤버가 존재하지 않습니다."));

        MemberResponse response = new MemberResponse();

        response.setName(member.getName());
        response.setProfileImageUrl(member.getProfileImageUrl());
        response.setIntroduction(member.getIntroduction());

        return response;
    }

    @Override
    public MemberIntroductionUpdateResponse updateIntroduction(Long memberId, MemberIntroductionUpdateRequest request) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("멤버가 존재하지 않습니다."));

        member.updateIntroduction(request.getIntroduction());

        MemberIntroductionUpdateResponse response = new MemberIntroductionUpdateResponse();

        response.setIntroduction(member.getIntroduction());

        return response;
    }

    private static final List<String> DEFAULT_PROFILE_URLS = List.of(
            "https://promptstudio-bucket.s3.ap-northeast-2.amazonaws.com/profile_image/Fish.png",
            "https://promptstudio-bucket.s3.ap-northeast-2.amazonaws.com/profile_image/Shell.png",
            "https://promptstudio-bucket.s3.ap-northeast-2.amazonaws.com/profile_image/StarfIsh.png"
    );

    private static String pickDefaultProfileImage() {
        return DEFAULT_PROFILE_URLS.get(ThreadLocalRandom.current().nextInt(DEFAULT_PROFILE_URLS.size()));
    }
}
