package promptstudio.promptstudio.domain.history.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import promptstudio.promptstudio.domain.history.domain.entity.History;
import promptstudio.promptstudio.domain.history.domain.entity.HistorySnapshotImage;
import promptstudio.promptstudio.domain.history.domain.repository.HistoryRepository;
import promptstudio.promptstudio.domain.history.dto.GptRunResult;
import promptstudio.promptstudio.domain.history.dto.HistoryDetailResponse;
import promptstudio.promptstudio.domain.history.dto.HistoryResponse;
import promptstudio.promptstudio.domain.history.dto.HistoryRunResponse;
import promptstudio.promptstudio.domain.maker.domain.entity.Maker;
import promptstudio.promptstudio.domain.maker.domain.entity.MakerImage;
import promptstudio.promptstudio.domain.maker.domain.repository.MakerRepository;
import promptstudio.promptstudio.global.exception.http.NotFoundException;
import promptstudio.promptstudio.global.gpt.application.GptService;
import promptstudio.promptstudio.global.s3.service.S3StorageService;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoryServiceImpl implements HistoryService {

    private final HistoryRepository historyRepository;
    private final MakerRepository makerRepository;
    private final S3StorageService s3StorageService;
    private final GptService gptService;

    @Override
    @Transactional
    public HistoryRunResponse createHistory(Long makerId, GptRunResult gptRunResult) {
        Maker maker = makerRepository.findByIdWithImages(makerId)
                .orElseThrow(() -> new NotFoundException("메이커를 찾을 수 없습니다."));

        String savedResultImageUrl = null;
        if (gptRunResult.getResultImageUrl() != null) {
            savedResultImageUrl = s3StorageService.copyImage(gptRunResult.getResultImageUrl());
        }


        String historyTitle = generateTitle(maker);

        History history = History.builder()
                .maker(maker)
                .title(historyTitle)
                .snapshotTitle(maker.getTitle())
                .snapshotContent(maker.getContent())
                .resultType(gptRunResult.getResultType())
                .resultText(gptRunResult.getResultText())
                .resultImageUrl(savedResultImageUrl)
                .build();

        int orderIndex = 0;
        for (MakerImage makerImage : maker.getImages()) {
            String copiedUrl = s3StorageService.copyImage(makerImage.getImageUrl());

            HistorySnapshotImage snapshotImage = HistorySnapshotImage.builder()
                    .imageUrl(copiedUrl)
                    .orderIndex(orderIndex++)
                    .build();

            history.addSnapshotImage(snapshotImage);
        }

        History savedHistory = historyRepository.save(history);

        return HistoryRunResponse.from(savedHistory);
    }

    private String generateTitle(Maker maker) {
        try {
            return gptService.generateHistoryTitle(
                    maker.getTitle(),
                    maker.getContent(),
                    null,
                    null
            );
        } catch (Exception e) {
            // GPT 실패 시 기본값
            System.err.println("히스토리 제목 생성 실패: " + e.getMessage());
            return "프롬프트 실행";
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistoryResponse> getHistoryList(Long makerId) {
        if (!makerRepository.existsById(makerId)) {
            throw new NotFoundException("메이커를 찾을 수 없습니다.");
        }

        List<History> histories = historyRepository.findByMakerIdOrderByCreatedAtDesc(makerId);

        return histories.stream()
                .map(HistoryResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public HistoryDetailResponse restoreHistory(Long makerId, Long historyId) {
        Maker maker = makerRepository.findByIdWithImages(makerId)
                .orElseThrow(() -> new NotFoundException("메이커를 찾을 수 없습니다."));

        History history = historyRepository.findByIdWithImages(historyId)
                .orElseThrow(() -> new NotFoundException("히스토리를 찾을 수 없습니다."));

        if (!history.getMaker().getId().equals(makerId)) {
            throw new NotFoundException("해당 메이커의 히스토리가 아닙니다.");
        }

        // 1. 해당 Maker의 모든 History snapshot URL 조회
        List<History> allHistories = historyRepository.findAllByMakerIdWithImages(makerId);
        Set<String> allSnapshotUrls = allHistories.stream()
                .flatMap(h -> h.getSnapshotImages().stream())
                .map(HistorySnapshotImage::getImageUrl)
                .collect(Collectors.toSet());

        // 2. 삭제할 이미지 URL (스냅샷 원본 제외)
        List<String> urlsToDelete = maker.getImages().stream()
                .map(MakerImage::getImageUrl)
                .filter(url -> !allSnapshotUrls.contains(url))
                .toList();

        // 3. DB에서 이미지 클리어
        maker.clearImages();

        // 4. 복원 작업
        maker.updateTitle(history.getSnapshotTitle());
        maker.updateContent(history.getSnapshotContent());

        int orderIndex = 0;
        for (HistorySnapshotImage snapshotImage : history.getSnapshotImages()) {
            String copiedUrl = s3StorageService.copyImage(snapshotImage.getImageUrl());

            MakerImage makerImage = MakerImage.builder()
                    .imageUrl(copiedUrl)
                    .orderIndex(orderIndex++)
                    .build();

            maker.addImage(makerImage);
        }
        // 5. 복사 완료 후 안전한 이미지만 삭제
        for (String url : urlsToDelete) {
            s3StorageService.deleteImage(url);
        }
        return HistoryDetailResponse.from(history);
    }
    
    

}