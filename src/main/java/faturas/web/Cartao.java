package faturas.web;

import faturas.dominio.ResultadoPdf;

import java.util.List;

/** O que se mostra (e guarda na sessão) por cada PDF carregado. */
public record Cartao(String id, ResultadoPdf pdf, String fornecedor, List<String> avisos) {

    public boolean precisaRevisao() {
        return !pdf.lida() || !avisos.isEmpty();
    }
}
