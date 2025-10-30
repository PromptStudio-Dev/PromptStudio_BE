package promptstudio.promptstudio.domain.prompt.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.member.domain.repository.MemberRepository;
import promptstudio.promptstudio.domain.prompt.domain.entity.Prompt;
import promptstudio.promptstudio.domain.prompt.domain.repository.PromptRepository;
import promptstudio.promptstudio.domain.prompt.dto.PromptCardNewsResponse;
import promptstudio.promptstudio.domain.prompt.dto.PromptCreateRequest;
import promptstudio.promptstudio.domain.promptplaceholder.domain.entity.PromptPlaceholder;
import promptstudio.promptstudio.domain.promptplaceholder.domain.repository.PromptPlaceholderRepository;
import promptstudio.promptstudio.global.exception.http.NotFoundException;
import promptstudio.promptstudio.global.s3.service.S3StorageService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PromptServiceImpl implements PromptService {

    private final S3StorageService s3StorageService;
    private final PromptRepository promptRepository;
    private final MemberRepository memberRepository;
    private final PromptIndexService promptIndexService;
    private final PromptPlaceholderRepository promptPlaceholderRepository;

    @Override
    public Long createPrompt(Long memberId, PromptCreateRequest request, MultipartFile file) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("멤버가 존재하지 않습니다."));

        String imageUrl = null;
        if (file != null && !file.isEmpty()) {
            imageUrl = s3StorageService.uploadImage(
                    file,
                    String.format("prompt/%d", memberId)
            );
        }

        //프롬프트 저장
        Prompt prompt = Prompt.builder()
                .member(member)
                .title(request.getTitle())
                .introduction(request.getIntroduction())
                .content(request.getContent())
                .category(request.getCategory())
                .visible(request.isVisible())
                .imageUrl(imageUrl)
                .result(request.getResult())
                .imageRequired(request.isImageRequired())
                .aiEnvironment(request.getAiEnvironment())
                .copyCount(0)
                .viewCount(0)
                .build();

        Prompt saved = promptRepository.save(prompt);

        //placeholder 추출
        Set<String> placeholders = extractPlaceholders(request.getContent());
        for (String fieldName : placeholders) {
            PromptPlaceholder placeholderEntity = PromptPlaceholder.builder()
                    .prompt(saved)
                    .fieldName(fieldName)
                    .build();

            promptPlaceholderRepository.save(placeholderEntity);
        }

        //vectorDB embedding
        if (saved.isVisible()) {
            promptIndexService.indexPrompt(saved);
        }

        return saved.getId();
    }

    @Override
    public List<PromptCardNewsResponse> getAllPrompts(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new NotFoundException("멤버가 존재하지 않습니다.");
        }
        return promptRepository.findAllOrderByLikeCountDesc(memberId);
    }

    //placeholder 추출
    private Set<String> extractPlaceholders(String content) {
        if (content == null) return Set.of();

        Set<String> result = new HashSet<>();

        Pattern p = Pattern.compile("\\[([^\\]]+)]");
        Matcher m = p.matcher(content);

        while (m.find()) {
            String fieldName = m.group(1).trim(); // "name", "date" 등
            if (!fieldName.isEmpty()) {
                result.add(fieldName);
            }
        }

        return result;
    }

}
