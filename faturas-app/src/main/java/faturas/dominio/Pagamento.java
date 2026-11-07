package faturas.dominio;

import java.time.LocalDate;

/** Estado de pagamento e respetivo dia (pode ser nulo se a fatura não o indicar). */
public record Pagamento(String estado, LocalDate dia) {
}
