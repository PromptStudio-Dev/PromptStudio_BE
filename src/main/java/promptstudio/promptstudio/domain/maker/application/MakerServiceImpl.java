package promptstudio.promptstudio.domain.maker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import promptstudio.promptstudio.domain.maker.domain.entity.Maker;
import promptstudio.promptstudio.domain.maker.domain.entity.MakerImage;
import promptstudio.promptstudio.domain.maker.domain.repository.MakerRepository;
import promptstudio.promptstudio.domain.maker.dto.*;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.member.domain.repository.MemberRepository;
import promptstudio.promptstudio.global.config.FeedbackRateLimiter;
import promptstudio.promptstudio.global.exception.http.NotFoundException;
import promptstudio.promptstudio.global.s3.service.S3StorageService;
import promptstudio.promptstudio.global.gpt.application.GptService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MakerServiceImpl implements MakerService {

    private final MakerRepository makerRepository;
    private final MemberRepository memberRepository;
    private final S3StorageService s3StorageService;
    private final GptService gptService;
    private final FeedbackRateLimiter feedbackRateLimiter;

    @Override
    @Transactional
    public Long createMaker(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        Maker maker = Maker.builder()
                .member(member)
                .title("")      // 빈 값으로 초기화
                .content("")    // 빈 값으로 초기화
                .build();

        Maker savedMaker = makerRepository.save(maker);
        return savedMaker.getId();
    }

    @Override
    @Transactional
    public MakerUpdateResponse updateMaker(Long makerId, MakerUpdateRequest request, List<MultipartFile> newImages) {
        Maker maker = makerRepository.findById(makerId)
                .orElseThrow(() -> new NotFoundException("메이커를 찾을 수 없습니다."));

        maker.updateTitle(request.getTitle());
        maker.updateContent(request.getContent());

        List<MakerImage> currentImages = maker.getImages();
        List<String> keepImageUrls = request.getExistingImageUrls() != null
                ? request.getExistingImageUrls()
                : new ArrayList<>();

        // 삭제할 이미지 찾아서 S3에서 삭제
        for (MakerImage image : currentImages) {
            if (!keepImageUrls.contains(image.getImageUrl())) {
                s3StorageService.deleteImage(image.getImageUrl());
            }
        }

        // DB에서 모든 이미지 제거
        maker.clearImages();

        // 유지할 이미지 다시 추가 (순서대로)
        int orderIndex = 0;
        for (String imageUrl : keepImageUrls) {
            MakerImage makerImage = MakerImage.builder()
                    .imageUrl(imageUrl)
                    .orderIndex(orderIndex++)
                    .build();
            maker.addImage(makerImage);
        }

        // 새 이미지 업로드 및 추가
        if (newImages != null && !newImages.isEmpty()) {
            for (MultipartFile file : newImages) {
                if (!file.isEmpty()) {
                    String imageUrl = s3StorageService.uploadImage(file, "maker");
                    MakerImage makerImage = MakerImage.builder()
                            .imageUrl(imageUrl)
                            .orderIndex(orderIndex++)
                            .build();
                    maker.addImage(makerImage);
                }
            }
        }

        return MakerUpdateResponse.builder()
                .makerId(maker.getId())
                .title(maker.getTitle())
                .updatedAt(maker.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public MakerDetailResponse getMakerDetail(Long makerId) {
        Maker maker = makerRepository.findByIdWithImages(makerId)
                .orElseThrow(() -> new NotFoundException("메이커를 찾을 수 없습니다."));

        return MakerDetailResponse.from(maker);
    }

    @Override
    @Transactional(readOnly = true)
    public TextUpgradeResponse upgradeText(TextUpgradeRequest request) {

        String searchQuery = gptService.generateSearchQuery(
                request.getFullText(),
                request.getSelectedText(),
                request.getDirection()
        );

        log.info("searchQuery: {}", searchQuery);

        // 벡터 검색 (topK + threshold 필터링)
        List<Document> docs = gptService.retrieve(
                searchQuery,
                8,
                0.5
        );

        // threshold 필터링 후 결과 0개인 경우
        if (docs.isEmpty()) {
            String upgradedText = gptService.upgradeText(
                    request.getFullText(),
                    request.getSelectedText(),
                    request.getDirection()
            );

            return TextUpgradeResponse.builder()
                    .originalText(request.getSelectedText())
                    .upgradedText(upgradedText)
                    .direction(request.getDirection())
                    .build();
        }

        // 컨텍스트 구성
        int perDocLimit = 3000;   // 문서 하나당 최대 글자 수
        int totalLimit  = 12000;  // 전체 컨텍스트 최대 글자 수

        StringBuilder sb = new StringBuilder();
        int used = 0;

        for (Document d : docs) {
            String text = d.getText();
            if (text == null || text.isBlank()) continue;

            // 문서별 최대 길이
            if (text.length() > perDocLimit) {
                text = text.substring(0, perDocLimit);
            }

            String chunk = "----\n" + text + "\n";

            // totalLimit 초과 시 중단
            if (used + chunk.length() > totalLimit) break;

            sb.append(chunk);
            used += chunk.length();
        }

        String context = sb.toString().trim();


        // 업그레이드 텍스트 생성
        String upgradedText = gptService.upgradeTextWithContext(
                request.getFullText(),
                request.getSelectedText(),
                request.getDirection(),
                context
        );

        // RAG 활용 프롬프트 ID
        List<Long> promptIds = docs.stream()
                .map(d -> d.getMetadata().get("promptId"))
                .filter(Objects::nonNull)
                .map(id -> {
                    try {
                        return Long.parseLong(id.toString());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid promptId format: {}", id);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        log.info("retrievedPromptIds: {}", promptIds);

        return TextUpgradeResponse.builder()
                .originalText(request.getSelectedText())
                .upgradedText(upgradedText)
                .direction(request.getDirection())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TextUpgradeResponse reupgradeText(TextReupgradeRequest request) {

        // 검색 쿼리 생성
        String searchQuery = gptService.generateSearchQuery(
                request.getFullText(),
                request.getSelectedText(),
                request.getDirection()
        );

        log.info("searchQuery: {}", searchQuery);

        // 벡터 검색 (topK + threshold)
        List<Document> docs = gptService.retrieve(
                searchQuery,
                8,
                0.5
        );

        // threshold 필터링 후 결과 0개인 경우
        if (docs.isEmpty()) {
            String upgradedText = gptService.reupgradeText(
                    request.getFullText(),
                    request.getSelectedText(),
                    request.getPrevDirection(),
                    request.getPrevResult(),
                    request.getDirection()
            );

            return TextUpgradeResponse.builder()
                    .originalText(request.getSelectedText())
                    .upgradedText(upgradedText)
                    .direction(request.getDirection())
                    .build();
        }

        // 컨텍스트 구성
        int perDocLimit = 3000;   // 문서 하나당 최대 글자 수
        int totalLimit  = 12000;  // 전체 컨텍스트 최대 글자 수

        StringBuilder sb = new StringBuilder();
        int used = 0;

        for (Document d : docs) {
            String text = d.getText();
            if (text == null || text.isBlank()) continue;

            if (text.length() > perDocLimit) {
                text = text.substring(0, perDocLimit);
            }

            String chunk = "----\n" + text + "\n";

            if (used + chunk.length() > totalLimit) break;

            sb.append(chunk);
            used += chunk.length();
        }

        String context = sb.toString().trim();

        // 업그레이드 텍스트 생성
        String upgradedText = gptService.reupgradeTextWithContext(
                request.getFullText(),
                request.getSelectedText(),
                request.getPrevDirection(),
                request.getPrevResult(),
                request.getDirection(),
                context
        );

        // RAG 활용 프롬프트 ID
        List<Long> promptIds = docs.stream()
                .map(d -> d.getMetadata().get("promptId"))
                .filter(Objects::nonNull)
                .map(id -> {
                    try {
                        return Long.parseLong(id.toString());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid promptId format: {}", id);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        log.info("retrievedPromptIds: {}", promptIds);

        return TextUpgradeResponse.builder()
                .originalText(request.getSelectedText())
                .upgradedText(upgradedText)
                .direction(request.getDirection())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PromptFeedbackResponse getPromptFeedback(Long makerId) {
        // Rate Limit 체크
        feedbackRateLimiter.checkLimit(makerId);

        Maker maker = makerRepository.findById(makerId)
                .orElseThrow(() -> new NotFoundException("메이커를 찾을 수 없습니다."));

        String feedback = gptService.generatePromptFeedback(maker.getContent());

        return PromptFeedbackResponse.builder()
                .feedback(feedback)
                .build();
    }
}