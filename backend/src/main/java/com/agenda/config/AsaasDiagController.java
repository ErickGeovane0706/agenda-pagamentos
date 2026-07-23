package com.agenda.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TEMPORÁRIO — remover assim que o 401 do Asaas for resolvido.
 *
 * Diz exatamente que chave o container recebeu (sem vazar a chave: só tamanho,
 * 4 primeiros, 4 últimos, e se tem espaço nas pontas) e testa essa chave ao vivo
 * contra o Asaas de produção. Assim descobrimos de uma vez se o 401 é chave
 * truncada, chave errada, ou outra coisa.
 *
 * Gated pelo ASAAS_WEBHOOK_TOKEN, que já existe — sem token válido responde 404.
 */
@RestController
public class AsaasDiagController {

    @Value("${asaas.api-key:}")
    private String apiKey;

    @Value("${asaas.base-url:}")
    private String baseUrl;

    @Value("${asaas.webhook-token:}")
    private String webhookToken;

    @GetMapping("/webhook/asaas-diag")
    public ResponseEntity<Map<String, Object>> diag(@RequestParam(required = false) String token) {
        if (webhookToken == null || webhookToken.isBlank() || !webhookToken.equals(token)) {
            return ResponseEntity.notFound().build();
        }

        var out = new LinkedHashMap<String, Object>();
        var k = apiKey == null ? "" : apiKey;
        out.put("keyLength", k.length());
        out.put("keyFirst4", k.length() >= 4 ? k.substring(0, 4) : k);
        out.put("keyLast4", k.length() >= 4 ? k.substring(k.length() - 4) : "");
        out.put("keyTemEspacoNasPontas", !k.equals(k.trim()));
        out.put("baseUrl", baseUrl);
        out.put("baseUrlTemEspaco", baseUrl != null && !baseUrl.equals(baseUrl.trim()));

        // Teste ao vivo: a chave do container funciona? 200 = sim, 401 = chave ruim.
        try {
            var h = new HttpHeaders();
            h.set("access_token", k.trim());
            var resp = new RestTemplate().exchange(
                (baseUrl == null ? "" : baseUrl.trim()) + "/myAccount",
                HttpMethod.GET, new HttpEntity<>(h), String.class);
            out.put("myAccountStatus", resp.getStatusCode().value());
        } catch (HttpStatusCodeException e) {
            out.put("myAccountStatus", e.getStatusCode().value());
        } catch (Exception e) {
            out.put("myAccountErro", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return ResponseEntity.ok(out);
    }
}
