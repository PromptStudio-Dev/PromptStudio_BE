package promptstudio.promptstudio.domain.likes.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import promptstudio.promptstudio.domain.likes.domain.entity.Likes;
import promptstudio.promptstudio.domain.likes.domain.repository.LikesRepository;
import promptstudio.promptstudio.domain.likes.dto.LikesToggleResponse;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.member.domain.repository.MemberRepository;
import promptstudio.promptstudio.domain.prompt.domain.entity.Prompt;
import promptstudio.promptstudio.domain.prompt.domain.repository.PromptRepository;
import promptstudio.promptstudio.global.exception.http.NotFoundException;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class LikesServiceImpl implements LikesService {

    private final MemberRepository memberRepository;
    private final PromptRepository promptRepository;
    private final LikesRepository likesRepository;

    @Override
    public LikesToggleResponse toggleLikes(Long memberId, Long promptId) {
        Member member = memberRepository.findById(memberId).orElseThrow(
                () -> new NotFoundException("유저가 존재하지 않습니다."));
        Prompt prompt = promptRepository.findById(promptId).orElseThrow(
                () -> new NotFoundException("프롬프트가 존재하지 않습니다."));

        if (likesRepository.existsByMemberIdAndPromptId(memberId, promptId)) {
            likesRepository.deleteByMemberIdAndPromptId(memberId, promptId);;
        } else {
            try {
                likesRepository.save(Likes.builder()
                        .member(member)
                        .prompt(prompt)
                        .build());
            } catch (DataIntegrityViolationException e) {
                log.warn("Duplicate like insert ignored. memberId={}, promptId={}", memberId, promptId, e);
            }
        }

        LikesToggleResponse response = new LikesToggleResponse();
        response.setLiked(likesRepository.existsByMemberIdAndPromptId(memberId, promptId));
        response.setLikeCount(likesRepository.countByPromptId(promptId));
        return response;
    }
}
