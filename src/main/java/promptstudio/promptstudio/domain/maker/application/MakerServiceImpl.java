package promptstudio.promptstudio.domain.maker.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import promptstudio.promptstudio.domain.maker.domain.entity.Maker;
import promptstudio.promptstudio.domain.maker.domain.entity.MakerImage;
import promptstudio.promptstudio.domain.maker.domain.repository.MakerRepository;
import promptstudio.promptstudio.domain.maker.dto.MakerCreateRequest;
import promptstudio.promptstudio.domain.maker.dto.MakerUpdateRequest;
import promptstudio.promptstudio.domain.maker.dto.MakerUpdateResponse;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.member.domain.repository.MemberRepository;
import promptstudio.promptstudio.global.exception.http.NotFoundException;
import promptstudio.promptstudio.global.s3.service.S3StorageService;

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
}