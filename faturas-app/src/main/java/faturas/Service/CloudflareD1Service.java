package faturas.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CloudflareD1Service {

    @Value("${cloudflare.api.token}")
    private String apiToken;

    @Value("${cloudflare.account.id}")
    private String accountId;

    @Value("${cloudflare.d1.database.id}")
    private String databaseId;

    private final RestTemplate restTemplate = new RestTemplate();

    public void executarSql(String sql, List<Object> params) {
        String url = "https://api.cloudflare.com/client/v4/accounts/" + accountId +
                "/d1/database/" + databaseId + "/query";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken);

        Map<String, Object> body = new HashMap<>();
        body.put("sql", sql);
        body.put("params", params);

        // Declarado sem os tipos em bico de papagaio <> para evitar o conflito de compilação
        HttpEntity entity = new HttpEntity(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Erro ao comunicar com a Cloudflare D1: " + response.getBody());
            }
        } catch (Exception e) {
            throw new RuntimeException("Falha na execução da query D1: " + e.getMessage(), e);
        }
    }
}