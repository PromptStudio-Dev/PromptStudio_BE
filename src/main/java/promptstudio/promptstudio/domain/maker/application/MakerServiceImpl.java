package promptstudio.promptstudio.domain.maker.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import promptstudio.promptstudio.domain.maker.domain.entity.Maker;
import promptstudio.promptstudio.domain.maker.domain.entity.MakerImage;
import promptstudio.promptstudio.domain.maker.domain.repository.MakerRepository;
import promptstudio.promptstudio.domain.maker.dto.*;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.member.domain.repository.MemberRepository;
import promptstudio.promptstudio.global.exception.http.NotFoundException;
import promptstudio.promptstudio.global.s3.service.S3StorageService;
import promptstudio.promptstudio.global.gpt.application.GptService;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MakerServiceImpl implements MakerService {

    private final MakerRepository makerRepository;
    private final MemberRepository memberRepository;
    private final S3StorageService s3StorageService;

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

    @Override
    @Transactional
    public MakerUpdateResponse updateMaker(Long makerId, MakerUpdateRequest request, List<MultipartFile> newImages) {
        System.out.println("=== DEBUG ===");
        System.out.println("Title: " + request.getTitle());
        System.out.println("Content: " + request.getContent());
        System.out.println("Content is null? " + (request.getContent() == null));

        if (request.getContent() != null) {
            System.out.println("Content length: " + request.getContent().length());
            System.out.println("Content preview: " + request.getContent().substring(0, Math.min(50, request.getContent().length())));
        }

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

    private final GptService gptService; // 생성자 주입 필요

    @Override
    @Transactional(readOnly = true)
    public TextUpgradeResponse upgradeText(Long makerId, TextUpgradeRequest request) {
        // 1. Maker 조회하여 fullContext 가져오기
        Maker maker = makerRepository.findById(makerId)
                .orElseThrow(() -> new NotFoundException("메이커를 찾을 수 없습니다."));

        String fullContext = maker.getContent();

        // 2. GPT 서비스 호출
        String upgradedText = gptService.upgradeText(
                request.getSelectedText(),
                request.getDirection(),
                fullContext
        );

        // 3. 응답 생성
        return TextUpgradeResponse.builder()
                .originalText(request.getSelectedText())
                .upgradedText(upgradedText)
                .direction(request.getDirection())
                .build();
    }
}