package ru.exp.chetapi;

import com.google.gson.*;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CrptApi {
    private static final String CREATE_DOCUMENT_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    private final OkHttpClient httpClient = new OkHttpClient();
    private final Semaphore rateLimiter;
    private final Gson gson = new Gson();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.rateLimiter = new Semaphore(requestLimit);

        // запускаем периодическое восстановление разрешений
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(
                this.rateLimiter::release,
                0,
                timeUnit.toMillis(1),
                TimeUnit.MILLISECONDS
        );
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class Document {

        private String description;

        private String participantInn;

        private String docId;

        private String docStatus;

        private String docType;

        private String importRequest;

        private String ownerInn;

        private String producerInn;

        private String productionDate;

        private String productionType;

        private String regDate;

        private String regNumber;

        private Products products;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class Products {

        private CertificateType certificateDocument;

        private String certificateDocumentDate;

        private String certificateDocumentNumber;

        private String productionDate;

        private String tnvedCode;

        private String uitCode;

        private String uituCode;
    }

    public enum CertificateType {
        CONFORMITY_CERTIFICATE, CONFORMITY_DECLARATION
    }

    public String createDocument(Document document, String signature) throws InterruptedException, IOException {
        // разрешение перед выполнением запроса
        rateLimiter.acquire();

        try {
            String jsonDocument = gson.toJson(document);

            // создаем HTTP запрос
            RequestBody body = RequestBody.create(jsonDocument, MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(CREATE_DOCUMENT_URL)
                    .post(body)
                    .addHeader("Signature", signature)
                    .build();

            // выполняем запрос и возвращаем результат
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }
                return response.body().string();
            }
        } finally {
            // Разрешение только после выполнения запроса
        }
    }

    public static void main(String[] args) throws Exception {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);

        // Вызов метода, в котором у нас есть объект документа и подпись для него
        String result = api.createDocument(new Document(), "signature");
        System.out.println(result);
    }
}
