package faturas.extracao;

import faturas.dominio.ResultadoPdf;
import faturas.dominio.Linha;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Testes de ponta a ponta com faturas reais (ficheiros em src/test/resources/faturas). */
class PdfExtratorTest {

    private final PdfExtrator extrator = new PdfExtrator(300);

    private ResultadoPdf ler(String nome) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/faturas/" + nome)) {
            assertThat(in).as("ficheiro de teste " + nome).isNotNull();
            return extrator.processar(nome, in.readAllBytes());
        }
    }

    private static BigDecimal soma(ResultadoPdf r) {
        return r.linhas().stream().map(Linha::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void makro() throws IOException {
        ResultadoPdf r = ler("makro.pdf");
        assertThat(r.avisos()).isEmpty();
        assertThat(r.fatura().nifEmitente()).isEqualTo("502030712");
        assertThat(r.fatura().numero()).isEqualTo("FAC 02205202601/011724");
        assertThat(r.fatura().data()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(r.fatura().total()).isEqualByComparingTo("538.83");
        assertThat(r.linhas()).hasSize(4);
        assertThat(r.previews()).hasSize(2);   // fatura + recibo de pagamento
        assertThat(r.previews().get(0)).startsWith("data:image/jpeg;base64,");
        assertThat(soma(r)).isEqualByComparingTo("508.33");
        assertThat(r.pagamento().estado()).isEqualTo("PAGO");
        assertThat(r.pagamento().dia()).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void docapeixeIgnoraDuplicadoETriplicado() throws IOException {
        ResultadoPdf r = ler("docapeixe.pdf");
        assertThat(r.avisos()).isEmpty();
        assertThat(r.fatura().nifEmitente()).isEqualTo("516526782");
        assertThat(r.fatura().numero()).isEqualTo("FT 2026A1/26788");
        assertThat(r.fatura().total()).isEqualByComparingTo("410.09");
        assertThat(r.linhas()).hasSize(1);   // 3 cópias no PDF, 1 só artigo
        assertThat(r.previews()).hasSize(1); // só o original, sem duplicado/triplicado
        assertThat(soma(r)).isEqualByComparingTo("386.88");
        assertThat(r.pagamento().estado()).isEqualTo("POR PAGAR");
        assertThat(r.pagamento().dia()).isNull();
    }

    @Test
    void recheioDeOutubroComLinhasEmDuasPaginas() throws IOException {
        ResultadoPdf r = ler("recheio-2026-10-22.pdf");
        assertThat(r.avisos()).isEmpty();
        assertThat(r.fatura().numero()).isEqualTo("ZCF2 BMD1/6111569577");
        assertThat(r.fatura().total()).isEqualByComparingTo("1006.55");
        assertThat(r.linhas()).hasSize(15);
        assertThat(soma(r)).isEqualByComparingTo("902.43");
        assertThat(r.pagamento().estado()).isEqualTo("POR PAGAR");
        assertThat(r.pagamento().dia()).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void recheioDeNovembro() throws IOException {
        ResultadoPdf r = ler("recheio-2026-11-03.pdf");
        assertThat(r.avisos()).isEmpty();
        assertThat(r.fatura().numero()).isEqualTo("ZCF2 BMD1/6111578420");
        assertThat(r.fatura().total()).isEqualByComparingTo("1300.99");
        assertThat(r.linhas()).hasSize(18);
        assertThat(soma(r)).isEqualByComparingTo("1182.90");
        assertThat(r.pagamento().dia()).isEqualTo(LocalDate.of(2026, 12, 9));
    }

    @Test
    void pdfInvalidoNaoRebenta() {
        ResultadoPdf r = extrator.processar("lixo.pdf", "isto não é um pdf".getBytes());
        assertThat(r.lida()).isFalse();
        assertThat(r.avisos()).isNotEmpty();
    }
}
