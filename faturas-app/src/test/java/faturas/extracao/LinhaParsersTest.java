package faturas.extracao;

import faturas.dominio.Linha;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LinhaParsersTest {

    private static final String RECHEIO = "502897279";

    @Test
    void recheioComUnidadesColadasAoNumero() {
        List<Linha> l = LinhaParsers.ler(RECHEIO, "46551 ABACATE HASS MATURADO RCH 2CXP 3,838 KG 7,675 KG 3,79 29,09 6% 4,02");
        assertThat(l).hasSize(1);
        assertThat(l.get(0).codigo()).isEqualTo("46551");
        assertThat(l.get(0).descricao()).isEqualTo("ABACATE HASS MATURADO RCH");
        assertThat(l.get(0).qtd()).isEqualByComparingTo("7.675");
        assertThat(l.get(0).unidade()).isEqualTo("KG");
        assertThat(l.get(0).valor()).isEqualByComparingTo("29.09");
    }

    @Test
    void recheioComUnidadesSeparadas() {
        List<Linha> l = LinhaParsers.ler(RECHEIO, "46551 ABACATE HASS MATURADO RCH 2 CXP 4,000 KG 8,000 KG 3,79 30,32 6% 4,02");
        assertThat(l).hasSize(1);
        assertThat(l.get(0).valor()).isEqualByComparingTo("30.32");
    }

    @Test
    void recheioDescricaoComNumerosNoFim() {
        List<Linha> l = LinhaParsers.ler(RECHEIO, "752027 SACO LIXO MCHEF AT 130LT 10 UN 1CX 16UN 16UN 2,19 35,04 23% 2,69");
        assertThat(l).hasSize(1);
        assertThat(l.get(0).descricao()).isEqualTo("SACO LIXO MCHEF AT 130LT 10 UN");
        assertThat(l.get(0).qtd()).isEqualByComparingTo("16");
        assertThat(l.get(0).valor()).isEqualByComparingTo("35.04");
    }

    @Test
    void makro() {
        List<Linha> l = LinhaParsers.ler("502030712",
                "2880817192000 MC SALMAO (SAL) NORU SEM AMANH KG 8,45 19,200 162,24 1 162,24 4 S");
        assertThat(l).hasSize(1);
        assertThat(l.get(0).codigo()).isEqualTo("2880817192000");
        assertThat(l.get(0).qtd()).isEqualByComparingTo("19.2");
        assertThat(l.get(0).valor()).isEqualByComparingTo("162.24");
    }

    @Test
    void docapeixeNumaSoLinha() {
        String texto = "Refª. Artigo Designação Qtd. Preço Imposto Total Ilíq.\n"
                + "L148 Salmão 6/7 40,300 kg. 9,60 6,00% 386,88\n"
                + "Os artigos facturados/serviços prestados";
        List<Linha> l = LinhaParsers.ler("516526782", texto);
        assertThat(l).hasSize(1);
        assertThat(l.get(0).codigo()).isEqualTo("L148");
        assertThat(l.get(0).qtd()).isEqualByComparingTo("40.3");
        assertThat(l.get(0).valor()).isEqualByComparingTo("386.88");
    }

    @Test
    void docapeixeComLinhaPartida() {
        String texto = "Refª. Artigo Designação Qtd. Preço Imposto Total Ilíq.\n"
                + "L148 Salmão 6/7 kg. 386,88\n"
                + "40,300 9,60 6,00%\n"
                + "Os artigos facturados/serviços prestados";
        List<Linha> l = LinhaParsers.ler("516526782", texto);
        assertThat(l).hasSize(1);
        assertThat(l.get(0).qtd()).isEqualByComparingTo("40.3");
        assertThat(l.get(0).valor()).isEqualByComparingTo("386.88");
    }

    @Test
    void fornecedorSemParserDevolveVazio() {
        assertThat(LinhaParsers.suporta("999999999")).isFalse();
        assertThat(LinhaParsers.ler("999999999", "qualquer texto")).isEmpty();
    }
}
