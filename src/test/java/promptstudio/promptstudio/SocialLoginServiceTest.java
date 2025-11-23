package promptstudio.promptstudio;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import promptstudio.promptstudio.domain.member.application.MemberService;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.member.domain.entity.SocialLogin;
import promptstudio.promptstudio.domain.member.domain.entity.SocialProvider;
import promptstudio.promptstudio.domain.member.domain.repository.MemberRepository;
import promptstudio.promptstudio.domain.member.domain.repository.SocialLoginRepository;
import promptstudio.promptstudio.global.auth.dto.GoogleLoginRequest;
import promptstudio.promptstudio.global.google.dto.GoogleUserInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class SocialLoginServiceTest {

    @Autowired
    MemberService socialLoginService;
    @Autowired
    SocialLoginRepository socialLoginRepository;
    @Autowired
    MemberRepository memberRepository;

    @Test
    void 신규유저면_member와_sociallogin을_생성한다() {
        long beforeMember = memberRepository.count();
        long beforeSocial = socialLoginRepository.count();

        GoogleUserInfo info = GoogleUserInfo.builder()
                .providerId("google-sub-123")
                .email("test@gmail.com")
                .name("테스트유저")
                .picture("http://img")
                .build();

        Member member = socialLoginService.findOrCreateMember(SocialProvider.GOOGLE, info);

        assertNotNull(member.getId());
        assertEquals(beforeMember + 1, memberRepository.count());
        assertEquals(beforeSocial + 1, socialLoginRepository.count());
    }


    @Test
    void 기존유저면_새로_생성하지_않고_같은_member를_반환한다() {
        long beforeMember = memberRepository.count();
        long beforeSocial = socialLoginRepository.count();

        String providerId = "google-sub-123"; // 신규유저 테스트랑 동일 값 써도 되고, unique로 해도 됨

        // given: 1차 호출 -> 신규유저 생성
        GoogleUserInfo firstInfo = GoogleUserInfo.builder()
                .providerId(providerId)
                .email("test@gmail.com")
                .name("테스트유저")
                .picture("http://img")
                .build();

        Member firstMember =
                socialLoginService.findOrCreateMember(SocialProvider.GOOGLE, firstInfo);

        assertNotNull(firstMember.getId());
        assertEquals(beforeMember + 1, memberRepository.count());
        assertEquals(beforeSocial + 1, socialLoginRepository.count());

        // when: 같은 providerId로 2차 호출 (기존 유저 시나리오)
        GoogleUserInfo secondInfo = GoogleUserInfo.builder()
                .providerId(providerId) // ⭐ 핵심: 동일 providerId
                .email("test@gmail.com")
                .name("테스트유저")
                .picture("http://img")
                .build();

        Member secondMember =
                socialLoginService.findOrCreateMember(SocialProvider.GOOGLE, secondInfo);

        // then: count 추가 증가 없어야 함
        assertEquals(beforeMember + 1, memberRepository.count());
        assertEquals(beforeSocial + 1, socialLoginRepository.count());

        // 그리고 같은 member를 반환해야 함
        assertEquals(firstMember.getId(), secondMember.getId());
    }

}

