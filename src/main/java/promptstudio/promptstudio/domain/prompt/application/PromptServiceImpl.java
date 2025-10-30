package promptstudio.promptstudio.domain.prompt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.member.domain.repository.MemberRepository;
import promptstudio.promptstudio.domain.prompt.domain.entity.Prompt;
import promptstudio.promptstudio.domain.prompt.domain.repository.PromptRepository;
import promptstudio.promptstudio.domain.prompt.dto.PromptCreateRequest;
import promptstudio.promptstudio.global.exception.http.NotFoundException;
import promptstudio.promptstudio.global.s3.service.S3StorageService;

@Service
@RequiredArgsConstructor
@Transactional
public class PromptServiceImpl implements PromptService {

    private final S3StorageService s3StorageService;
    private final PromptRepository promptRepository;
    private final MemberRepository memberRepository;

    @Override
    public Long createPrompt(Long memberId, PromptCreateRequest request, MultipartFile file) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("멤버가 존재하지 않습니다."));

        String imageUrl = null;

        if (file != null && !file.isEmpty()) {
            imageUrl = s3StorageService.uploadImage(
                    file, String.format("prompt/%d", memberId));
        }
        Prompt prompt = Prompt.builder().
                member(member).
                title(request.getTitle()).
                introduction(request.getIntroduction()).
                content(request.getContent()).
                visible(request.isVisible()).
                imageUrl(imageUrl).
                result(request.getResult()).
                imageRequired(request.isImageRequired()).
                aiEnvironment(request.getAiEnvironment()).
                copyCount(0).
                viewCount(0).
                build();

        Prompt saved = promptRepository.save(prompt);
        return saved.getId();
    }
}
