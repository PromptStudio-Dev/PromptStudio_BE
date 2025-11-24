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

import java.util.Optional;

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
        // 1. 이전 히스토리 조회
        Optional<History> previousHistoryOpt = historyRepository
                .findFirstByMakerIdOrderByCreatedAtDesc(maker.getId());

        if (previousHistoryOpt.isEmpty()) {
            // 첫 번째 히스토리
            return gptService.generateHistoryTitle(
                    maker.getTitle(),
                    maker.getContent(),
                    null,
                    null
            );
        }

        History previousHistory = previousHistoryOpt.get();

        // 2. 내용 변경 확인
        boolean titleChanged = !maker.getTitle().equals(previousHistory.getSnapshotTitle());
        boolean contentChanged = !maker.getContent().equals(previousHistory.getSnapshotContent());

        if (!titleChanged && !contentChanged) {
            // 변경 없음 -> (1), (2) 붙이기
            return appendCounter(previousHistory.getTitle());
        } else {
            // 변경 있음 -> GPT로 변경점 요약
            return gptService.generateHistoryTitle(
                    maker.getTitle(),
                    maker.getContent(),
                    previousHistory.getSnapshotTitle(),
                    previousHistory.getSnapshotContent()
            );
        }
    }

    private String appendCounter(String previousTitle) {
        // "이미지 생성" -> "이미지 생성(1)"
        // "이미지 생성(1)" -> "이미지 생성(2)"

        if (previousTitle.matches(".*\\(\\d+\\)$")) {
            // 이미 카운터가 있는 경우
            int lastParenIndex = previousTitle.lastIndexOf('(');
            String baseTitle = previousTitle.substring(0, lastParenIndex);
            String counterStr = previousTitle.substring(lastParenIndex + 1, previousTitle.length() - 1);
            int counter = Integer.parseInt(counterStr);
            return baseTitle + "(" + (counter + 1) + ")";
        } else {
            // 카운터가 없는 경우
            return previousTitle + "(1)";
        }
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