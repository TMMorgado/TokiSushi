package faturas.extracao;

import faturas.dominio.Fatura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Interpreta o texto do QR code das faturas portuguesas (AT): campos "X:valor" separados por "*".
 *
 * Campos usados: A nif emitente, B nif adquirente, D tipo, F data (AAAAMMDD), G número,
 * N total de IVA, O total. Por espaço fiscal (I continente, J Açores, K Madeira):
 *   x1 espaço fiscal (ex.: "PT"), x2 base isenta, x3 base reduzida, x4 IVA reduzida,
 *   x5 base intermédia, x6 IVA intermédia, x7 base normal, x8 IVA normal.
 */
public final class QrAtParser {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.05");
    private static final String[] REGIOES = {"I", "J", "K"};
    private static final String[] SUFIXOS_BASE = {"2", "3", "5", "7"};

    private QrAtParser() {}

    /** Vazio se o texto não for o QR de uma fatura (ex.: o Data Matrix da Makro). */
    public static Optional<Fatura> parse(String texto) {
        if (texto == null || !texto.startsWith("A:")) return Optional.empty();

        Map<String, String> c = new HashMap<>();
        for (String parte : texto.split("\\*")) {
            int i = parte.indexOf(':');
            if (i > 0) c.put(parte.substring(0, i), parte.substring(i + 1));
        }

        try {
            if (c.get("A") == null || c.get("F") == null || c.get("G") == null || c.get("O") == null) {
                return Optional.empty();
            }
            LocalDate data = LocalDate.parse(c.get("F"), DateTimeFormatter.BASIC_ISO_DATE);
            BigDecimal total = new BigDecimal(c.get("O"));
            BigDecimal iva = valor(c, "N");

            BigDecimal bases = BigDecimal.ZERO;
            for (String regiao : REGIOES) {
                for (String sufixo : SUFIXOS_BASE) {
                    bases = bases.add(valor(c, regiao + sufixo));
                }
            }
            boolean coerente = bases.add(iva).subtract(total).abs().compareTo(TOLERANCIA) <= 0;

            return Optional.of(new Fatura(c.get("A"), c.getOrDefault("B", ""), c.getOrDefault("D", ""),
                    data, c.get("G"), iva, total, coerente));
        } catch (DateTimeParseException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static BigDecimal valor(Map<String, String> campos, String chave) {
        String v = campos.get(chave);
        return v == null || v.isBlank() ? BigDecimal.ZERO : new BigDecimal(v);
    }
}
