package faturas.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Dados de cabeçalho de uma fatura, lidos do QR code AT. */
public record Fatura(
        String nifEmitente,
        String nifAdquirente,
        String tipo,                 // FT, FR, FS, NC, ND...
        LocalDate data,
        String numero,
        BigDecimal totalIva,
        BigDecimal total,
        boolean totaisCoerentes) {   // bases + IVA == total

    public boolean ehNotaCredito() {
        return "NC".equals(tipo);
    }

    /** Total sem IVA. */
    public BigDecimal base() {
        return total.subtract(totalIva);
    }

    /** Valor a lançar: positivo nas faturas, negativo nas notas de crédito. */
    public BigDecimal valorComSinal() {
        return ehNotaCredito() ? total.abs().negate() : total;
    }

    /** Identifica a fatura de forma única (para detetar duplicados). */
    public String chave() {
        return nifEmitente + "|" + numero;
    }
}
