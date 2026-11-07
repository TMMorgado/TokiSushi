package faturas;

import faturas.Service.CloudflareD1Service;
import faturas.extracao.PdfExtrator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FaturasApplication {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        SpringApplication.run(FaturasApplication.class, args);
    }

    @Bean
    PdfExtrator pdfExtrator(FaturasProperties props, CloudflareD1Service d1) {
        return new PdfExtrator(props.renderDpi(), d1);
    }
}
