package faturas.extracao;

import faturas.dominio.Fatura;
import faturas.dominio.Pagamento;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deduz o estado de pagamento a partir do texto da fatura. */
public final class PagamentoParser {

    private static final Pattern VENCIMENTO =
            Pattern.compile("Data de Vencimento:\\s*(\\d{2})\\.(\\d{2})\\.(\\d{4})");

    private PagamentoParser() {}

    public static Pagamento detetar(String texto, Fatura fatura) {
        if (fatura.ehNotaCredito()) {
            return new Pagamento("NOTA DE CRÉDITO", null);
        }
        // Makro: o recibo de pagamento (MB/TPA) vem no próprio PDF
        if (texto.contains("MB (Online)") || texto.contains("Processado por SIBS")) {
            return new Pagamento("PAGO", fatura.data());
        }
        Matcher m = VENCIMENTO.matcher(texto);
        LocalDate dia = m.find()
                ? LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)))
                : null;
        return new Pagamento("POR PAGAR", dia);
    }
}
