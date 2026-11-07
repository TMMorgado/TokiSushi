package faturas.dominio;

import java.math.BigDecimal;

/** Uma linha de artigo de uma fatura (valor sem IVA). */
public record Linha(String codigo, String descricao, BigDecimal qtd, String unidade, BigDecimal valor) {
}
