package promptstudio.promptstudio.global.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FeedbackRateLimiter {

    private static final int MAX_REQUESTS_PER_DAY = 30;  // 하루 최대 요청 수

    private final Cache<Long, AtomicInteger> requestCounts = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)  // 24시간 후 초기화
            .build();

    public void checkLimit(Long memberId) {
        AtomicInteger count = requestCounts.get(memberId, k -> new AtomicInteger(0));

        int currentCount = count.incrementAndGet();

        if (currentCount > MAX_REQUESTS_PER_DAY) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "피드백은 하루 30회까지 요청할 수 있어요. 내일 다시 시도해주세요!"
            );
        }
    }
}