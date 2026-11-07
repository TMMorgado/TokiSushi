package faturas.extracao;

import faturas.dominio.Fatura;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class QrAtParserTest {

    @Test
    void makro() {
        Fatura f = QrAtParser.parse("A:502030712*B:519521510*C:PT*D:FT*E:N*F:20261103*G:FAC 02205202601/011724"
                + "*H:J6XTCNHH-011724*I1:PT*I3:508.33*I4:30.50*N:30.50*O:538.83*Q:rFMm*R:1708").orElseThrow();
        assertThat(f.nifEmitente()).isEqualTo("502030712");
        assertThat(f.nifAdquirente()).isEqualTo("519521510");
        assertThat(f.numero()).isEqualTo("FAC 02205202601/011724");
        assertThat(f.data()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(f.total()).isEqualByComparingTo("538.83");
        assertThat(f.totalIva()).isEqualByComparingTo("30.50");
        assertThat(f.base()).isEqualByComparingTo("508.33");
        assertThat(f.totaisCoerentes()).isTrue();
    }

    @Test
    void recheioComTresTaxas() {
        Fatura f = QrAtParser.parse("A:502897279*B:519521510*C:PT*D:FT*E:N*F:20261103*G:ZCF2 BMD1/6111578420"
                + "*H:JFZ8CKB5-6111578420*I1:PT*I3:641.22*I4:38.47*I5:449.70*I6:58.46*I7:91.98*I8:21.16"
                + "*N:118.09*O:1300.99*Q:75yw*R:631").orElseThrow();
        assertThat(f.total()).isEqualByComparingTo("1300.99");
        assertThat(f.totaisCoerentes()).isTrue();
    }

    @Test
    void notaDeCreditoFicaNegativa() {
        Fatura f = QrAtParser.parse("A:502030712*B:519521510*C:PT*D:NC*E:N*F:20261105*G:NC 1/1*H:XX-1"
                + "*I1:PT*I3:10.00*I4:0.60*N:0.60*O:10.60").orElseThrow();
        assertThat(f.ehNotaCredito()).isTrue();
        assertThat(f.valorComSinal()).isEqualByComparingTo(new BigDecimal("-10.60"));
    }

    @Test
    void totaisIncoerentesSaoDetetados() {
        Fatura f = QrAtParser.parse("A:502030712*B:519521510*D:FT*F:20261103*G:FT 1/1*I1:PT*I3:100.00*I4:6.00*N:6.00*O:150.00")
                .orElseThrow();
        assertThat(f.totaisCoerentes()).isFalse();
    }

    @Test
    void ignoraCodigosQueNaoSaoFaturas() {
        // Data Matrix que vem na fatura da Makro
        assertThat(QrAtParser.parse("{\"0\":\"6207700200945792011763\",\"1\":0,\"2\":2}")).isEmpty();
        assertThat(QrAtParser.parse("https://exemplo.pt")).isEmpty();
        assertThat(QrAtParser.parse(null)).isEmpty();
    }
}
