package com.example.employeemanagement.service;

import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
public class FaceRecognitionService {

    @Value("${faceplusplus.api_key}")
    private String apiKey;

    @Value("${faceplusplus.api_secret}")
    private String apiSecret;

    @Value("${faceplusplus.detect_url}")
    private String detectUrl;

    @Value("${faceplusplus.compare_url}")
    private String compareUrl;

    private final OkHttpClient client = new OkHttpClient();

    public String detectFace(String imageBase64) throws IOException {
        RequestBody body = new FormBody.Builder()
            .add("api_key", apiKey)
            .add("api_secret", apiSecret)
            .add("image_base64", imageBase64)
            .build();

        Request request = new Request.Builder()
            .url(detectUrl)
            .post(body)
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            return response.body().string();
        }
    }

    public String compareFaces(String imageBase64_1, String imageBase64_2) throws IOException {
        RequestBody body = new FormBody.Builder()
            .add("api_key", apiKey)
            .add("api_secret", apiSecret)
            .add("image_base64_1", imageBase64_1)
            .add("image_base64_2", imageBase64_2)
            .build();

        Request request = new Request.Builder()
            .url(compareUrl)
            .post(body)
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            return response.body().string();
        }
    }
}
