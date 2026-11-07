package faturas;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;
import java.util.Optional;

/** Configuração em application.properties (prefixo "faturas"). */
@ConfigurationProperties(prefix = "faturas")
public record FaturasProperties(
        @DefaultValue("300") int renderDpi,
        @DefaultValue Map<String, String> fornecedores) {   // NIF -> nome

    public Optional<String> fornecedor(String nif) {
        return Optional.ofNullable(fornecedores.get(nif));
    }
}
