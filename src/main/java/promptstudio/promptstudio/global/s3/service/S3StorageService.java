package promptstudio.promptstudio.global.s3.service;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Template s3Template;
    private final S3Client s3Client;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${spring.cloud.aws.region.static}")
    private String region;

    @Value("${app.s3.public-read:false}")
    private boolean publicRead;

    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic", "heif");

    public String uploadImage(MultipartFile file, String keyPrefix) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다.");
        }

        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains(".")) ?
                original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 확장자만 허용됩니다. (jpg, jpeg, png, webp, gif, bmp, svg, heic, heif)");
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

        String cleanUrl = url;
        int queryIndex = url.indexOf('?');
        if (queryIndex > 0) {
            cleanUrl = url.substring(0, queryIndex);
        }

        String amazonDomain = "amazonaws.com/";
        int keyStartIndex = cleanUrl.indexOf(amazonDomain);
        if (keyStartIndex > 0) {
            String extracted = cleanUrl.substring(keyStartIndex + amazonDomain.length());

            // Path 스타일인 경우 bucket 이름 제거
            if (extracted.startsWith(bucket + "/")) {
                extracted = extracted.substring(bucket.length() + 1);
            }

            System.out.println("URL에서 추출된 key: " + extracted);
            return extracted;
        }

        int lastSlash = cleanUrl.lastIndexOf('/');
        if (lastSlash > 0) {
            return cleanUrl.substring(lastSlash + 1);
        }

        // 그 외의 경우 그대로 반환
        return cleanUrl;
    }

    public String copyImage(String sourceUrl) {
        try {
            System.out.println("=== 이미지 복사 시작 ===");
            System.out.println("원본 URL: " + sourceUrl);

            // 1. S3 URL인지 확인
            if (sourceUrl.contains("amazonaws.com") && sourceUrl.contains(bucket)) {
                // S3 내부 복사 (빠름)
                return copyWithinS3(sourceUrl);
            } else {
                // 외부 URL (DALL-E 등) - 다운로드 후 재업로드
                return downloadAndUpload(sourceUrl);
            }

        } catch (Exception e) {
            System.err.println("=== 이미지 복사 실패 ===");
            System.err.println("에러 메시지: " + e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지 복사 실패: " + e.getMessage(),
                    e
            );
        }
    }

    private String copyWithinS3(String sourceUrl) {
        System.out.println("→ S3 내부 복사 방식");

        String sourceKey = extractKeyFromUrl(sourceUrl);
        System.out.println("추출된 sourceKey: " + sourceKey);

        // 1. 원본 파일 존재 여부 확인
        if (!doesObjectExist(sourceKey)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "원본 이미지가 S3에 존재하지 않습니다: " + sourceKey
            );
        }

        String ext = sourceKey.contains(".")
                ? sourceKey.substring(sourceKey.lastIndexOf('.') + 1).toLowerCase()
                : "jpg";

        String newKey = "history/%s/%s.%s".formatted(
                LocalDate.now(),
                UUID.randomUUID(),
                ext
        );
        System.out.println("새 key: " + newKey);

        // 2. 복사 실행
        CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(sourceKey)
                .destinationBucket(bucket)
                .destinationKey(newKey)
                .build();

        s3Client.copyObject(copyRequest);

        // 3. 복사 후 파일 존재 검증
        if (!doesObjectExist(newKey)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지 복사 후 파일이 존재하지 않습니다: " + newKey
            );
        }

        System.out.println("복사 완료 및 검증 성공: " + newKey);

        if (publicRead) {
            return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, newKey);
        } else {
            return s3Template.createSignedGetURL(bucket, newKey, Duration.ofMinutes(15)).toString();
        }
    }

    // 파일 존재 여부 확인 메서드 추가
    private boolean doesObjectExist(String key) {
        try {
            s3Client.headObject(builder -> builder.bucket(bucket).key(key));
            return true;
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            System.err.println("S3 파일 존재 확인 실패: " + key + " - " + e.getMessage());
            return false;
        }
    }

    private String downloadAndUpload(String externalUrl) {
        try {
            System.out.println("→ 외부 URL 다운로드 후 업로드 방식");

            // 1. 외부 URL에서 다운로드
            byte[] imageBytes = downloadImageFromUrl(externalUrl);
            System.out.println("다운로드 완료: " + imageBytes.length + " bytes");

            // 2. 확장자 추출
            String ext = "png";
            if (externalUrl.contains(".png")) {
                ext = "png";
            } else if (externalUrl.contains(".jpg") || externalUrl.contains(".jpeg")) {
                ext = "jpg";
            }

            // 3. 새 key 생성
            String newKey = "history/%s/%s.%s".formatted(
                    LocalDate.now(),
                    UUID.randomUUID(),
                    ext
            );
            System.out.println("새 key: " + newKey);

            // 4. S3에 업로드
            String contentType = ext.equals("png") ? "image/png" : "image/jpeg";
            String s3Url = uploadBytes(imageBytes, newKey, contentType, "max-age=31536000");

            // 5. 업로드 후 파일 존재 검증
            if (!doesObjectExist(newKey)) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "이미지 업로드 후 파일이 존재하지 않습니다: " + newKey
                );
            }

            System.out.println("S3 업로드 완료 및 검증 성공: " + s3Url);
            return s3Url;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "외부 URL 다운로드 및 업로드 실패: " + e.getMessage(),
                    e
            );
        }
    }


    public byte[] downloadImageFromUrl(String imageUrl) {
        try {
            System.out.println("=== 이미지 다운로드 시작 ===");
            System.out.println("URL: " + imageUrl);

            // 1. S3 URL인지 확인
            if (imageUrl.contains("amazonaws.com")) {
                // S3에서 직접 다운로드 (더 빠름)
                String key = extractKeyFromUrl(imageUrl);
                System.out.println("S3 Key: " + key);

                try {
                    S3Resource resource = s3Template.download(bucket, key);
                    byte[] imageBytes = resource.getContentAsByteArray();

                    System.out.println("다운로드 완료: " + imageBytes.length + " bytes");

                    // 파일 크기 체크 (4MB = 4 * 1024 * 1024)
                    if (imageBytes.length > 4 * 1024 * 1024) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "이미지 파일이 너무 큽니다. (최대 4MB)"
                        );
                    }

                    return imageBytes;

                } catch (IOException e) {
                    throw new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "S3 이미지 다운로드 실패: " + e.getMessage(),
                            e
                    );
                }

            } else {
                // 외부 URL (DALL-E 결과 등)
                System.out.println("외부 URL에서 다운로드");
                return downloadFromExternalUrl(imageUrl);
            }

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지 다운로드 실패: " + e.getMessage(),
                    e
            );
        }
    }

    private byte[] downloadFromExternalUrl(String url) {
        try {
            String cleanUrl = url;
            if (!url.contains("blob.core.windows.net")) {
                cleanUrl = cleanInvalidQueryParams(url);
            }
            System.out.println("사용할 URL: " + cleanUrl);

            // RestTemplate 생성 (timeout 설정)
            RestTemplate restTemplate = new RestTemplate();

            var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(30000);
            factory.setReadTimeout(30000);
            restTemplate.setRequestFactory(factory);

            // HTTP GET 요청
            byte[] imageBytes = restTemplate.getForObject(cleanUrl, byte[].class);

            if (imageBytes == null || imageBytes.length == 0) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "이미지 다운로드 실패: 빈 응답"
                );
            }

            System.out.println("외부 다운로드 완료: " + imageBytes.length + " bytes");

            // 파일 크기 체크 (4MB)
            if (imageBytes.length > 4 * 1024 * 1024) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "이미지 파일이 너무 큽니다. (최대 4MB)"
                );
            }

            return imageBytes;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "외부 URL 다운로드 실패: " + e.getMessage(),
                    e
            );
        }
    }

    private String cleanInvalidQueryParams(String url) {
        try {
            // 쿼리 파라미터가 없으면 그대로 반환
            if (!url.contains("?")) {
                return url;
            }

            String[] parts = url.split("\\?", 2);
            String baseUrl = parts[0];
            String queryString = parts[1];

            // 쿼리 파라미터 분리
            String[] params = queryString.split("&");
            StringBuilder cleanParams = new StringBuilder();

            for (String param : params) {
                // "key=value" 형태만 유지
                if (param.contains("=")) {
                    String[] keyValue = param.split("=", 2);
                    String key = keyValue[0];
                    String value = keyValue.length > 1 ? keyValue[1] : "";

                    // 값이 있는 파라미터만 추가
                    if (!value.isEmpty()) {
                        if (cleanParams.length() > 0) {
                            cleanParams.append("&");
                        }
                        cleanParams.append(key).append("=").append(value);
                    }
                }
            }

            // 정리된 URL 반환
            if (cleanParams.length() > 0) {
                return baseUrl + "?" + cleanParams.toString();
            } else {
                return baseUrl;
            }

        } catch (Exception e) {
            // 파싱 실패 시 원본 반환
            System.err.println("URL 정리 실패, 원본 사용: " + e.getMessage());
            return url;
        }
    }

}