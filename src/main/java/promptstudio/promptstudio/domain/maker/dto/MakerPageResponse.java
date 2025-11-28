package promptstudio.promptstudio.domain.maker.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MakerPageResponse {
    private List<MakerListResponse> makers;
    private int currentPage;
    private int totalPages;
    private long totalElements;
    private boolean hasNext;
}