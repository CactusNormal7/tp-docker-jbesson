package fr.ynov.td2;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class Td2Application {

    @Value("${MESSAGE:Hello Docker}")
    private String message;

    @Value("${APP_VERSION:dev}")
    private String version;

    public static void main(String[] args) {
        SpringApplication.run(Td2Application.class, args);
    }

    @GetMapping("/")
    public Map<String, String> hello() throws UnknownHostException {
        return Map.of("message", message, "version", version,
                "hostname", InetAddress.getLocalHost().getHostName());
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
