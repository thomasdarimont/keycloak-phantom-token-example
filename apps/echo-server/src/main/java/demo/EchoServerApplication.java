package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@SpringBootApplication
public class EchoServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EchoServerApplication.class, args);
    }
}

@RestController
@RequestMapping("/api")
class ApiController {

    @RequestMapping("/echo")
    ResponseEntity<?> echo(RequestEntity<String> request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("method", request.getMethod().name());
        response.put("path", request.getUrl().getPath());
        if (request.getUrl().getQuery() != null) {
            response.put("query", request.getUrl().getQuery());
        }
        response.put("headers", request.getHeaders().toSingleValueMap());
        if (request.getBody() != null) {
            response.put("body", request.getBody());
        }

        String auth = request.getHeaders().getFirst("Authorization");
        if (auth != null && auth.toLowerCase().startsWith("bearer ")) {
            String token = auth.substring(7);
            Map<String, Object> jwt = decodeJwtPayload(token);
            if (jwt != null) {
                response.put("jwt_payload", jwt);
            }
        }

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> decodeJwtPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            return new ObjectMapper().readValue(decoded, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
