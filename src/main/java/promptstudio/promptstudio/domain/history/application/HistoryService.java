package promptstudio.promptstudio.domain.history.application;

import promptstudio.promptstudio.domain.history.dto.GptRunResult;
import promptstudio.promptstudio.domain.history.dto.HistoryDetailResponse;
import promptstudio.promptstudio.domain.history.dto.HistoryResponse;
import promptstudio.promptstudio.domain.history.dto.HistoryRunResponse;
import promptstudio.promptstudio.domain.history.dto.ImageDownloadData;

import java.util.List;

public interface HistoryService {

    HistoryRunResponse createHistory(Long makerId, GptRunResult gptRunResult);

    List<HistoryResponse> getHistoryList(Long makerId);

    HistoryDetailResponse restoreHistory(Long makerId, Long historyId);

    ImageDownloadData downloadImage(Long makerId, Long historyId);
}