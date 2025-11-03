package promptstudio.promptstudio.domain.maker.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import promptstudio.promptstudio.domain.maker.domain.entity.Maker;
import promptstudio.promptstudio.domain.maker.domain.repository.MakerRepository;
import promptstudio.promptstudio.domain.maker.dto.MakerCreateRequest;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.member.domain.repository.MemberRepository;
import promptstudio.promptstudio.global.exception.http.NotFoundException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MakerServiceImpl implements MakerService {

    private final MakerRepository makerRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public Long createMaker(Long memberId, MakerCreateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        Maker maker = Maker.builder()
                .member(member)
                .title(request.getTitle())
                .content(request.getContent())
                .build();

        Maker savedMaker = makerRepository.save(maker);
        return savedMaker.getId();
    }
}