package faturas.dominio;

/** Uma fatura lida com sucesso, já com o nome do fornecedor (usada para gerar os Excel). */
public record FaturaLida(String fornecedor, ResultadoPdf pdf) {
}
