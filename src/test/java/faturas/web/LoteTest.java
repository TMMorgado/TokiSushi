package faturas.web;

import faturas.dominio.Fatura;
import faturas.dominio.ResultadoPdf;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoteTest {

    private static Cartao lida(String id, String nif, String numero, String ficheiro) {
        Fatura f = new Fatura(nif, "519521510", "FT", LocalDate.of(2026, 11, 3), numero,
                new BigDecimal("1.00"), new BigDecimal("10.00"), true);
        return new Cartao(id, new ResultadoPdf(ficheiro, f, null, List.of(), List.of()), "Forn", List.of());
    }

    private static Cartao naoLida(String id, String ficheiro) {
        return new Cartao(id, ResultadoPdf.falhada(ficheiro, "sem QR"), "—", List.of("sem QR"));
    }

    @Test
    void acumulaEntreCarregamentos() {
        Lote lote = new Lote();
        lote.adicionar(lida("1", "502030712", "FT 1", "a.pdf"));
        lote.adicionar(lida("2", "502897279", "FT 2", "b.pdf"));
        assertThat(lote.cartoes()).hasSize(2);   // o segundo carregamento não apaga o primeiro
    }

    @Test
    void detetaFaturaRepetida() {
        Lote lote = new Lote();
        lote.adicionar(lida("1", "502030712", "FT 1", "a.pdf"));
        assertThat(lote.comFatura("502030712|FT 1")).isPresent();
        assertThat(lote.comFatura("502030712|FT 1").get().pdf().ficheiro()).isEqualTo("a.pdf");
    }

    @Test
    void mesmoNumeroDeOutroFornecedorNaoEhRepetida() {
        Lote lote = new Lote();
        lote.adicionar(lida("1", "502030712", "FT 1", "a.pdf"));
        assertThat(lote.comFatura("502897279|FT 1")).isEmpty();
    }

    @Test
    void detetaPdfPorLerRepetido() {
        Lote lote = new Lote();
        lote.adicionar(naoLida("1", "scan.pdf"));
        assertThat(lote.naoLidaComNome("scan.pdf")).isPresent();
        assertThat(lote.naoLidaComNome("outro.pdf")).isEmpty();
    }

    @Test
    void removerPermiteVoltarACarregar() {
        Lote lote = new Lote();
        lote.adicionar(lida("1", "502030712", "FT 1", "a.pdf"));
        assertThat(lote.remover("1")).isTrue();
        assertThat(lote.remover("1")).isFalse();
        assertThat(lote.comFatura("502030712|FT 1")).isEmpty();
    }

    @Test
    void limparEsvaziaTudo() {
        Lote lote = new Lote();
        lote.adicionar(lida("1", "502030712", "FT 1", "a.pdf"));
        lote.limpar();
        assertThat(lote.cartoes()).isEmpty();
    }
}
