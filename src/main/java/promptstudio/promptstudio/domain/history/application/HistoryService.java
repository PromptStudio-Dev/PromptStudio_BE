package promptstudio.promptstudio.domain.history.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import promptstudio.promptstudio.domain.history.dto.GptRunResult;
import promptstudio.promptstudio.domain.history.dto.HistoryDetailResponse;
import promptstudio.promptstudio.domain.history.dto.HistoryResponse;
import promptstudio.promptstudio.domain.history.dto.HistoryRunResponse;

public interface HistoryService {

    HistoryRunResponse createHistory(Long makerId, GptRunResult gptRunResult);

    Page<HistoryResponse> getHistoryList(Long makerId, Pageable pageable);

    HistoryDetailResponse restoreHistory(Long makerId, Long historyId);
}