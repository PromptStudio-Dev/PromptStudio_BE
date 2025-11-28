package promptstudio.promptstudio.domain.member.application;

import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.member.domain.entity.SocialProvider;
import promptstudio.promptstudio.domain.member.dto.MemberIntroductionUpdateRequest;
import promptstudio.promptstudio.domain.member.dto.MemberIntroductionUpdateResponse;
import promptstudio.promptstudio.domain.member.dto.MemberResponse;
import promptstudio.promptstudio.global.google.dto.GoogleUserInfo;

public interface MemberService {
    Member findOrCreateMember(SocialProvider provider, GoogleUserInfo info);
    MemberResponse getMember(Long memberId);
    MemberIntroductionUpdateResponse updateIntroduction(Long memberId, MemberIntroductionUpdateRequest request);
}
