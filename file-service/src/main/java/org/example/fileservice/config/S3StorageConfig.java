package org.example.fileservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3StorageConfig {

    @Bean
    @ConfigurationProperties(prefix = "storage.s3")
    public S3Properties s3Properties() {
        return new S3Properties();
    }

    @Bean
    public S3Client s3Client(S3Properties props) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey());

        return S3Client.builder()
                .endpointOverride(URI.create(props.getInternalEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true) // Bắt buộc cho S3 Mock / MinIO / LocalStack
                        .build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(S3Properties props) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey());

        // Sử dụng publicEndpoint để client ngoài trình duyệt kết nối trực tiếp
        String endpoint = props.getPublicEndpoint() != null ? props.getPublicEndpoint() : props.getInternalEndpoint();

        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Data
    public static class S3Properties {
        private String accessKey = "internhub_app_client";
        private String secretKey = "InternHubAppSecret2026Secure";
        private String bucketName = "internhub-documents";
        private String region = "us-east-1";
        private String internalEndpoint = "http://localhost:9090";
        private String publicEndpoint = "http://localhost:9090";
    }
}
