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
import promptstudio.promptstudio.global.s3.service.S3StorageService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoryServiceImpl implements HistoryService {

    private final HistoryRepository historyRepository;
    private final MakerRepository makerRepository;
    private final S3StorageService s3StorageService;

    @Override
    @Transactional
    public HistoryRunResponse createHistory(Long makerId, GptRunResult gptRunResult) {
        Maker maker = makerRepository.findByIdWithImages(makerId)
                .orElseThrow(() -> new NotFoundException("메이커를 찾을 수 없습니다."));

        History history = History.builder()
                .maker(maker)
                .snapshotTitle(maker.getTitle())
                .snapshotContent(maker.getContent())
                .resultType(gptRunResult.getResultType())
                .resultText(gptRunResult.getResultText())
                .resultImageUrl(gptRunResult.getResultImageUrl())
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

    @Override
    @Transactional(readOnly = true)
    public Page<HistoryResponse> getHistoryList(Long makerId, Pageable pageable) {
        if (!makerRepository.existsById(makerId)) {
            throw new NotFoundException("메이커를 찾을 수 없습니다.");
        }

        Page<History> historyPage = historyRepository.findByMakerIdOrderByCreatedAtDesc(makerId, pageable);

        return historyPage.map(HistoryResponse::from);
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

        for (MakerImage image : maker.getImages()) {
            s3StorageService.deleteImage(image.getImageUrl());
        }

        maker.clearImages();

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

        return HistoryDetailResponse.from(history);
    }
}