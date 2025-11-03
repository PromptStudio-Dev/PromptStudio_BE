package promptstudio.promptstudio.global.s3.service;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Template s3Template;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${spring.cloud.aws.region.static}")
    private String region;

    @Value("${app.s3.public-read:false}")
    private boolean publicRead;

    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp");

    public String uploadImage(MultipartFile file, String keyPrefix) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다.");
        }

        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains(".")) ?
                original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 확장자만 허용됩니다. (jpg, jpeg, png, webp)");
        }

        String key = "%s/%s/%s.%s".formatted(
                keyPrefix, LocalDate.now(), UUID.randomUUID(), ext);

        try {
            ObjectMetadata metadata = ObjectMetadata.builder()
                    .contentType(file.getContentType())
                    .build();

            S3Resource uploaded = s3Template.upload(bucket, key, file.getInputStream(), metadata);

            if (publicRead) {
                return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
            } else {
                return s3Template.createSignedGetURL(bucket, key, Duration.ofMinutes(15)).toString();
            }

        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드 실패", e);
        }
    }

    public String uploadBytes(byte[] bytes, String key, String contentType, String cacheControl) {
        try (var in = new java.io.ByteArrayInputStream(bytes)) {
            ObjectMetadata md = ObjectMetadata.builder()
                    .contentType(contentType)
                    .cacheControl(cacheControl)
                    .build();

            s3Template.upload(bucket, key, in, md);

            if (publicRead) {
                return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
            } else {
                return s3Template.createSignedGetURL(bucket, key, Duration.ofMinutes(15)).toString();
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "S3 업로드 실패", e);
        }
    }

    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        try {
            // URL에서 key 추출
            String key = extractKeyFromUrl(imageUrl);
            s3Template.deleteObject(bucket, key);
        } catch (Exception e) {
            // 삭제 실패해도 계속 진행 (로그만 남김)
            System.err.println("S3 파일 삭제 실패: " + imageUrl + ", " + e.getMessage());
        }
    }

    private String extractKeyFromUrl(String url) {
        if (url.contains(bucket)) {
            // https://bucket.s3.region.amazonaws.com/key 형태
            int keyStartIndex = url.indexOf(bucket) + bucket.length() + 1;
            return url.substring(keyStartIndex);
        }
        // 이미 key만 있는 경우
        return url;
    }
}