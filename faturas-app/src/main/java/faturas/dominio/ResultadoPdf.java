package faturas.dominio;

import java.util.List;

/**
 * Resultado da leitura de um PDF. Se {@code fatura} for nula, o PDF não foi lido.
 * {@code previews} são as páginas desenhadas em JPEG (data URI) para mostrar no ecrã.
 */
public record ResultadoPdf(
        String ficheiro,
        Fatura fatura,
        Pagamento pagamento,
        List<Linha> linhas,
        List<String> avisos,
        List<String> previews) {

    public ResultadoPdf(String ficheiro, Fatura fatura, Pagamento pagamento, List<Linha> linhas, List<String> avisos) {
        this(ficheiro, fatura, pagamento, linhas, avisos, List.of());
    }

    public boolean lida() {
        return fatura != null;
    }

    public boolean precisaRevisao() {
        return !lida() || !avisos.isEmpty();
    }

    /** Cópia leve (sem imagens), para guardar na sessão. */
    public ResultadoPdf semPreviews() {
        return new ResultadoPdf(ficheiro, fatura, pagamento, linhas, avisos, List.of());
    }

    public static ResultadoPdf falhada(String ficheiro, String aviso) {
        return falhada(ficheiro, aviso, List.of());
    }

    public static ResultadoPdf falhada(String ficheiro, String aviso, List<String> previews) {
        return new ResultadoPdf(ficheiro, null, null, List.of(), List.of(aviso), previews);
    }
}
