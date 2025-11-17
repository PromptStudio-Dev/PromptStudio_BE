package promptstudio.promptstudio.domain.prompt.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import promptstudio.promptstudio.domain.likes.domain.repository.LikesRepository;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.member.domain.repository.MemberRepository;
import promptstudio.promptstudio.domain.prompt.domain.entity.Prompt;
import promptstudio.promptstudio.domain.prompt.domain.repository.PromptRepository;
import promptstudio.promptstudio.domain.prompt.dto.PromptCardNewsResponse;
import promptstudio.promptstudio.domain.prompt.dto.PromptCopyResponse;
import promptstudio.promptstudio.domain.prompt.dto.PromptCreateRequest;
import promptstudio.promptstudio.domain.prompt.dto.PromptResponse;
import promptstudio.promptstudio.domain.promptplaceholder.domain.entity.PromptPlaceholder;
import promptstudio.promptstudio.domain.promptplaceholder.domain.repository.PromptPlaceholderRepository;
import promptstudio.promptstudio.domain.viewrecord.domain.entity.ViewRecord;
import promptstudio.promptstudio.domain.viewrecord.domain.repository.ViewRecordRepository;
import promptstudio.promptstudio.global.exception.http.NotFoundException;
import promptstudio.promptstudio.global.s3.service.S3StorageService;

import java.time.LocalDateTime;
import java.util.*;
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
    private final LikesRepository likesRepository;
    private final VectorStore vectorStore;
    private final ViewRecordRepository viewRecordRepository;

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
    @Transactional(readOnly = true)
    public List<PromptCardNewsResponse> getAllPrompts(Long memberId, String category) {

        if (memberId == null) {
            return promptRepository.findAllOrderByLikeCountDescGuestWithCategory(category);
        }

        if (!memberRepository.existsById(memberId)) {
            throw new NotFoundException("멤버가 존재하지 않습니다.");
        }

        return promptRepository.findAllOrderByLikeCountDescWithCategory(memberId, category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptCardNewsResponse> getHotPrompts(Long memberId, String category) {

        LocalDateTime since = LocalDateTime.now().minusDays(7);

        PageRequest top3PageRequest = PageRequest.of(0, 3);

        if (memberId == null) {
            Page<PromptCardNewsResponse> pageResult =
                    promptRepository.findWeeklyTopPromptsGuestWithCategory(
                            since,
                            category,
                            top3PageRequest
                    );

            return pageResult.getContent();
        }

        if (!memberRepository.existsById(memberId)) {
            throw new NotFoundException("멤버가 존재하지 않습니다.");
        }

        Page<PromptCardNewsResponse> pageResult =
                promptRepository.findWeeklyTopPromptsWithCategory(memberId, since, category, top3PageRequest);

        return pageResult.getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptCardNewsResponse> getLikedPrompts(Long memberId, String category) {

        if (!memberRepository.existsById(memberId)) {
            throw new NotFoundException("멤버가 존재하지 않습니다.");
        }

        return promptRepository.findLikedPromptsByMemberId(memberId, category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptCardNewsResponse> getMyPrompts(Long memberId, String category) {

        if (!memberRepository.existsById(memberId)) {
            throw new NotFoundException("멤버가 존재하지 않습니다.");
        }

        return promptRepository.findMyPromptsWithCategory(memberId, category);
    }

    @Override
    public PromptResponse getPromptDetail(Long memberId, Long promptId) {

        Prompt prompt = promptRepository.findById(promptId).orElseThrow(
                () -> new NotFoundException("프롬프트가 존재하지 않습니다.")
        );

        if (memberId != null) {
            Member member = memberRepository.findById(memberId).orElseThrow(
                    () -> new NotFoundException("멤버가 존재하지 않습니다."));

            int updated = viewRecordRepository.touchIfExists(memberId, promptId);

            if (updated == 0) {
                ViewRecord record = ViewRecord.builder()
                        .member(member)
                        .prompt(prompt)
                        .build();
                viewRecordRepository.save(record);
            }

        }

        promptRepository.increaseViewCount(promptId);

        long likeCount = likesRepository.countByPromptId(promptId);

        if (memberId == null) {
            return toPromptResponse(prompt, likeCount, false);
        }

        boolean liked = likesRepository.existsByPromptIdAndMemberId(promptId, memberId);

        return toPromptResponse(prompt, likeCount, liked);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptCardNewsResponse> searchPrompts(Long memberId, String category, String query) {

        if (memberId != null && !memberRepository.existsById(memberId)) {
            throw new NotFoundException("멤버가 존재하지 않습니다.");
        }

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(30);

        if (category != null && !"전체".equals(category)) {
            builder.filterExpression("category == '" + category + "'");
        }

        SearchRequest request = builder.build();

        List<Document> docs = vectorStore.similaritySearch(request);

        List<Long> rankedIds = docs.stream()
                .map(doc -> toLong(doc.getMetadata().get("promptId")))
                .filter(id -> id != null)
                .distinct()
                .toList();

        if (rankedIds.isEmpty()) {
            return List.of();
        }

        List<PromptCardNewsResponse> rawCards =
                promptRepository.findPromptsByIdsWithCategory(rankedIds, memberId, category);

        Map<Long, PromptCardNewsResponse> cardById = new java.util.HashMap<>();
        for (PromptCardNewsResponse card : rawCards) {
            cardById.put(card.getPromptId(), card);
        }

        return rankedIds.stream()
                .map(cardById::get)
                .filter(card -> card != null)
                .toList();
    }

    @Override
    public List<PromptCardNewsResponse> getViewedPrompts(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new NotFoundException("멤버가 존재하지 않습니다.");
        }

        Pageable top10 = PageRequest.of(0, 10);

        return promptRepository.findRecentViewedCards(memberId, top10);
    }

    @Override
    public PromptCopyResponse copyPrompt(Long promptId) {
        Prompt prompt = promptRepository.findById(promptId).orElseThrow(
                () -> new NotFoundException("프롬프트가 존재하지 않습니다.")
        );

        prompt.updateCopyCount();

        PromptCopyResponse response = new PromptCopyResponse();

        response.setPromptId(prompt.getId());
        response.setCopyCount(prompt.getCopyCount());
        response.setContent(prompt.getContent());

        return response;
    }

    private Long toLong(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Long l) return l;
        if (raw instanceof Integer i) return i.longValue();
        if (raw instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Unexpected ID type: " + raw.getClass());
    }


    private PromptResponse toPromptResponse(Prompt prompt, long likeCount, boolean liked) {
        PromptResponse dto = new PromptResponse();

        dto.setMemberId(prompt.getMember().getId());
        dto.setPromptId(prompt.getId());
        dto.setName(prompt.getMember().getName());
        dto.setTitle(prompt.getTitle());
        dto.setIntroduction(prompt.getIntroduction());
        dto.setAiEnvironment(prompt.getAiEnvironment());
        dto.setCategory(prompt.getCategory());
        dto.setContent(prompt.getContent());
        dto.setImageUrl(prompt.getImageUrl());
        dto.setResult(prompt.getResult());
        dto.setLikeCount(likeCount);
        dto.setLiked(liked);
        dto.setCopyCount(prompt.getCopyCount());
        dto.setViewCount(prompt.getViewCount() + 1);
        dto.setCreatedAt(prompt.getCreatedAt());

        return dto;
    }

    //placeholder 추출
    private Set<String> extractPlaceholders(String content) {
        if (content == null) return Set.of();

        Set<String> result = new LinkedHashSet<>();

        Pattern p = Pattern.compile("\\[([^\\]]+)]");
        Matcher m = p.matcher(content);

        while (m.find()) {
            String fieldName = m.group(1).trim();
            if (!fieldName.isEmpty()) {
                result.add(fieldName);
            }
        }

        return result;
    }

}
