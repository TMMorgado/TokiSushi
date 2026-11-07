package faturas.extracao;

import faturas.dominio.Linha;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Leitura das linhas de artigos. Um parser por fornecedor, identificado pelo NIF do emitente. */
public final class LinhaParsers {

    private LinhaParsers() {}

    private static final Map<String, Function<String, List<Linha>>> POR_NIF = Map.of(
            "502030712", LinhaParsers::makro,
            "502897279", LinhaParsers::recheio,
            "516526782", LinhaParsers::docapeixe);

    public static boolean suporta(String nif) {
        return POR_NIF.containsKey(nif);
    }

    public static List<Linha> ler(String nif, String texto) {
        Function<String, List<Linha>> parser = POR_NIF.get(nif);
        return parser == null ? List.of() : parser.apply(texto);
    }

    private static final String NUM = "\\d[\\d\\s.]*";

    // MAKRO: codigo desc PACK PR_UNIT_KG UNIT_KG PRECO_UV QUANT VAL_TOTAL IVA ...
    private static final Pattern MAKRO = Pattern.compile(
            "^(\\d{8,14})\\s+(.+?)\\s+([A-Z]{2,3})\\s+[\\d.,]+\\s+([\\d.,]+)\\s+[\\d.,]+\\s+\\d+\\s+([\\d.,]+)\\s+\\d\\b.*$");

    // RECHEIO: cod desc VOL QT/VOL QT.TOTAL PR.UNIT VLR_MERC IVA% PR.UN c/IVA
    // (a extração pode colar a unidade ao número: "2CXP", "15UN" -> \s*)
    private static final Pattern RECHEIO = Pattern.compile(
            "^(\\d{4,7})\\s+(.+?)\\s+\\d+\\s*[A-Z]{2,3}\\s+[\\d.,]+\\s*[A-Z]{2,3}\\s+([\\d.,]+)\\s*([A-Z]{2,3})\\s+[\\d.,]+\\s+([\\d.,]+)\\s+\\d+%\\s+[\\d.,]+$");

    // DOCAPEIXE: normalmente numa só linha (A); se vier partida, juntam-se B (artigo) e QTD
    private static final Pattern DOCA_A = Pattern.compile(
            "^(\\S+)\\s+(.+?)\\s+(" + NUM + ",\\d{3})\\s+(kg|un|cx|lt)\\.?\\s+[\\d.,]+\\s+\\d+,\\d{2}%\\s+(" + NUM + ",\\d{2})$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCA_B = Pattern.compile(
            "^(\\S+)\\s+(.+?)\\s+(kg|un|cx|lt)\\.?\\s+(" + NUM + ",\\d{2})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCA_QTD = Pattern.compile(
            "^(" + NUM + ",\\d{3})\\s+[\\d.,]+\\s+\\d+,\\d{2}%$");

    static BigDecimal num(String s) {
        return new BigDecimal(s.replaceAll("[\\s\\u00a0.]", "").replace(',', '.'));
    }

    private static List<String> linhas(String texto) {
        return texto.replace('\u00a0', ' ').lines().map(String::strip).toList();
    }

    static List<Linha> makro(String texto) {
        List<Linha> out = new ArrayList<>();
        for (String l : linhas(texto)) {
            Matcher m = MAKRO.matcher(l);
            if (m.matches()) {
                out.add(new Linha(m.group(1), m.group(2), num(m.group(4)), m.group(3), num(m.group(5))));
            }
        }
        return out;
    }

    static List<Linha> recheio(String texto) {
        List<Linha> out = new ArrayList<>();
        for (String l : linhas(texto)) {
            Matcher m = RECHEIO.matcher(l);
            if (m.matches()) {
                out.add(new Linha(m.group(1), m.group(2), num(m.group(3)), m.group(4), num(m.group(5))));
            }
        }
        return out;
    }

    static List<Linha> docapeixe(String texto) {
        List<Linha> out = new ArrayList<>();
        List<String[]> itens = new ArrayList<>();
        List<BigDecimal> qtds = new ArrayList<>();
        boolean dentro = false;
        for (String l : linhas(texto)) {
            if (l.contains("Designação")) { dentro = true; continue; }
            if (l.startsWith("Os artigos")) break;
            if (!dentro) continue;

            Matcher a = DOCA_A.matcher(l);
            if (a.matches()) {
                out.add(new Linha(a.group(1), a.group(2), num(a.group(3)), a.group(4).toUpperCase(), num(a.group(5))));
                continue;
            }
            Matcher b = DOCA_B.matcher(l);
            if (b.matches()) {
                itens.add(new String[]{b.group(1), b.group(2), b.group(3).toUpperCase(), b.group(4)});
                continue;
            }
            Matcher q = DOCA_QTD.matcher(l);
            if (q.matches()) qtds.add(num(q.group(1)));
        }
        for (int i = 0; i < Math.min(itens.size(), qtds.size()); i++) {
            String[] it = itens.get(i);
            out.add(new Linha(it[0], it[1], qtds.get(i), it[2], num(it[3])));
        }
        return out;
    }
}
