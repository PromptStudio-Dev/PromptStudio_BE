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

    private static final int MAX_REQUESTS = 1;  // 분당 최대 요청 수

    private final Cache<Long, AtomicInteger> requestCounts = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    public void checkLimit(Long makerId) {
        AtomicInteger count = requestCounts.get(makerId, k -> new AtomicInteger(0));

        int currentCount = count.incrementAndGet();

        if (currentCount > MAX_REQUESTS) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "피드백은 1분에 1회만 요청할 수 있어요. 잠시 후 다시 시도해주세요!"
            );
        }
    }
}