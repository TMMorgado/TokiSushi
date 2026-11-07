package faturas.excel;

import faturas.dominio.Fatura;
import faturas.dominio.FaturaLida;
import faturas.dominio.Linha;
import faturas.dominio.Pagamento;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ComparisonOperator;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PatternFormatting;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFConditionalFormattingRule;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFFontFormatting;
import org.apache.poi.xssf.usermodel.XSSFPatternFormatting;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFSheetConditionalFormatting;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTable;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableStyleInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Gera os dois ficheiros Excel (faturas e artigos) a partir das faturas lidas.
 * O aspeto copia os templates "Fatura dos Fornecedores" (faturas) e "Template Preços Fornecedores" (artigos):
 * cores, tipos de letra, tabela com filtros e cores condicionais no estado.
 */
public final class ExcelExporter {

    public static final String POR_CLASSIFICAR = "POR CLASSIFICAR";
    public static final String[] CATEGORIAS = {"Produto chinês", "Produto português", "Outros"};
    public static final String[] CATEGORIAS2 = {"Sushi", "Bar", "Cozinha", "Outros"};

    // Textos do estado, iguais aos do template
    public static final String PAGO = "Pago";
    public static final String NAO_PAGO = "Não pago";
    public static final String NOTA_CREDITO = "Nota Crédito";

    private static final String[] CAB_FATURAS =
            {"Data", "Fornecedor", "Nº Fatura", "Valor", "Estado", "Dia do Pagamento"};
    // Mesma ordem em que as células são escritas em artigos()
    private static final String[] CAB_ARTIGOS =
            {"CATEGORIA", "CATEGORIA2", "ARTIGO", "LOJA", "NRFATURA", "DATA", "CODIGO", "QTD", "UNIDADE", "VALOR"};

    // Cores (RGB)
    private static final int AZUL_TITULO = 0x156082;      // faixa "Faturação | mês"
    private static final int AZUL_CLARO = 0xCAEEFB;       // cabeçalho da tabela e linha de total
    private static final int AZUL_ESCURO = 0x1F4E78;      // cabeçalho do Excel de artigos
    private static final int BRANCO = 0xFFFFFF;
    private static final int PRETO = 0x000000;

    private static final String FORMATO_DATA = "dd.mm.yyyy";
    private static final String FORMATO_DINHEIRO = "#,##0.00;[Red]-#,##0.00";   // negativos (notas de crédito) a vermelho
    private static final int ULTIMA_LINHA_EXCEL = 1_048_575;                    // índice (0-based) da última linha

    private ExcelExporter() {}

    // =====================================================================
    // Excel 1: uma linha por fatura
    // =====================================================================
    public static byte[] faturas(List<FaturaLida> lidas) throws IOException {
        List<FaturaLida> ordenadas = ordenar(lidas);
        String periodo = periodo(ordenadas);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet s = wb.createSheet(periodo.equals(VARIOS_MESES) ? "Faturas" : periodo);

            XSSFFont fTitulo = fonte(wb, "Aptos Narrow", 18, true, BRANCO);
            XSSFFont fCab = fonte(wb, "Aptos Narrow", 18, true, PRETO);
            XSSFFont fCorpo = fonte(wb, "Aptos Narrow", 16, false, PRETO);
            XSSFFont fTotal = fonte(wb, "Aptos Narrow", 16, true, PRETO);

            XSSFCellStyle stTitulo = estilo(wb, fTitulo, AZUL_TITULO, HorizontalAlignment.CENTER, null, false);
            XSSFCellStyle stCab = estilo(wb, fCab, AZUL_CLARO, HorizontalAlignment.CENTER, null, true);
            XSSFCellStyle stCentro = estilo(wb, fCorpo, -1, HorizontalAlignment.CENTER, null, true);
            XSSFCellStyle stTexto = estilo(wb, fCorpo, -1, null, null, true);
            XSSFCellStyle stData = estilo(wb, fCorpo, -1, HorizontalAlignment.CENTER, FORMATO_DATA, true);
            XSSFCellStyle stDinheiro = estilo(wb, fCorpo, -1, null, FORMATO_DINHEIRO, true);
            XSSFCellStyle stTotalTexto = estilo(wb, fTotal, AZUL_CLARO, HorizontalAlignment.CENTER, null, true);
            XSSFCellStyle stTotalDinheiro = estilo(wb, fTotal, AZUL_CLARO, null, FORMATO_DINHEIRO, true);

            // Linha 1: faixa azul "Faturação | mês"
            Row titulo = s.createRow(0);
            titulo.setHeightInPoints(22);
            texto(titulo, 0, "Faturação", stTitulo);
            texto(titulo, 1, periodo, stTitulo);

            // Linha 4: cabeçalho da tabela
            Row cab = s.createRow(3);
            cab.setHeightInPoints(22);
            for (int i = 0; i < CAB_FATURAS.length; i++) texto(cab, i, CAB_FATURAS[i], stCab);

            // Linhas de dados (a partir da linha 5)
            int n = 4;
            for (FaturaLida l : ordenadas) {
                Fatura f = l.pdf().fatura();
                Pagamento p = l.pdf().pagamento();
                Row r = s.createRow(n++);
                data(r, 0, f.data(), stData);
                texto(r, 1, l.fornecedor(), stCentro);
                texto(r, 2, f.numero(), stTexto);
                dinheiro(r, 3, f.valorComSinal(), stDinheiro);
                texto(r, 4, estado(p), stCentro);
                data(r, 5, p.dia(), stData);
            }
            if (n == 4) {   // sem faturas: uma linha vazia, para a tabela ter pelo menos uma linha
                Row r = s.createRow(n++);
                for (int c = 0; c < CAB_FATURAS.length; c++) r.createCell(c).setCellStyle(stTexto);
            }
            int ultima = n - 1;   // índice (0-based) da última linha de dados

            // Linha de total (fora da tabela; SUBTOTAL respeita os filtros)
            Row total = s.createRow(n);
            for (int c = 0; c < CAB_FATURAS.length; c++) total.createCell(c).setCellStyle(stTotalTexto);
            total.getCell(0).setCellValue("Total");
            Cell soma = total.getCell(3);
            soma.setCellStyle(stTotalDinheiro);
            soma.setCellFormula("SUBTOTAL(9,D5:D" + (ultima + 1) + ")");

            tabela(s, "TabelaFaturas", 3, ultima, CAB_FATURAS.length - 1);
            coresEstado(s);

            double[] larguras = {15.2, 21.7, 34, 23.2, 22.2, 25.2};
            for (int i = 0; i < larguras.length; i++) s.setColumnWidth(i, (int) (larguras[i] * 256));
            s.createFreezePane(0, 4);

            wb.setForceFormulaRecalculation(true);
            return bytes(wb);
        }
    }

    // =====================================================================
    // Excel 2: uma linha por artigo. As categorias ficam "POR CLASSIFICAR", com lista de opções.
    // =====================================================================
    public static byte[] artigos(List<FaturaLida> lidas) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet s = wb.createSheet("Artigos");

            XSSFFont fCab = fonte(wb, "Calibri", 18, true, BRANCO);
            XSSFFont fTexto = fonte(wb, "Arial", 16, false, PRETO);
            XSSFFont fNumero = fonte(wb, "Calibri", 16, false, PRETO);

            XSSFCellStyle stCab = estilo(wb, fCab, AZUL_ESCURO, null, null, false);
            XSSFCellStyle stTexto = estilo(wb, fTexto, -1, null, null, false);
            XSSFCellStyle stData = estilo(wb, fTexto, -1, HorizontalAlignment.CENTER, FORMATO_DATA, false);
            XSSFCellStyle stQtd = estilo(wb, fNumero, -1, null, null, false);
            XSSFCellStyle stDinheiro = estilo(wb, fNumero, -1, null, FORMATO_DINHEIRO, false);

            Row cab = s.createRow(0);
            for (int i = 0; i < CAB_ARTIGOS.length; i++) texto(cab, i, CAB_ARTIGOS[i], stCab);

            int n = 1;
            for (FaturaLida l : ordenar(lidas)) {
                Fatura f = l.pdf().fatura();
                for (Linha linha : l.pdf().linhas()) {
                    BigDecimal valor = f.ehNotaCredito() ? linha.valor().abs().negate() : linha.valor();
                    Row r = s.createRow(n++);
                    texto(r, 0, POR_CLASSIFICAR, stTexto);
                    texto(r, 1, POR_CLASSIFICAR, stTexto);
                    texto(r, 2, linha.descricao(), stTexto);
                    texto(r, 3, l.fornecedor(), stTexto);
                    texto(r, 4, f.numero(), stTexto);
                    data(r, 5, f.data(), stData);
                    texto(r, 6, linha.codigo(), stTexto);                       // como texto
                    Cell qtd = r.createCell(7);
                    qtd.setCellValue(linha.qtd().doubleValue());
                    qtd.setCellStyle(stQtd);
                    texto(r, 8, linha.unidade(), stTexto);
                    dinheiro(r, 9, valor, stDinheiro);
                }
            }
            if (n == 1) {   // sem artigos: uma linha vazia, para a tabela ter pelo menos uma linha
                Row r = s.createRow(n++);
                for (int c = 0; c < CAB_ARTIGOS.length; c++) r.createCell(c).setCellStyle(stTexto);
            }

            tabela(s, "TabelaArtigos", 0, n - 1, CAB_ARTIGOS.length - 1);
            lista(s, 0, CATEGORIAS);
            lista(s, 1, CATEGORIAS2);

            double[] larguras = {18, 18, 43.2, 18, 25, 14, 16, 10, 10, 12};
            for (int i = 0; i < larguras.length; i++) s.setColumnWidth(i, (int) (larguras[i] * 256));
            s.createFreezePane(0, 1);

            return bytes(wb);
        }
    }

    // =====================================================================
    // auxiliares
    // =====================================================================

    private static final String VARIOS_MESES = "Vários meses";

    /** "Pago" / "Não pago" / "Nota Crédito" (os textos que o template usa). */
    private static String estado(Pagamento p) {
        return switch (p.estado()) {
            case "PAGO" -> PAGO;
            case "NOTA DE CRÉDITO" -> NOTA_CREDITO;
            default -> NAO_PAGO;   // "POR PAGAR"
        };
    }

    /** "Outubro 2026" se as faturas forem todas do mesmo mês; senão "Vários meses". */
    private static String periodo(List<FaturaLida> ordenadas) {
        if (ordenadas.isEmpty()) return VARIOS_MESES;
        LocalDate ini = ordenadas.get(0).pdf().fatura().data();
        LocalDate fim = ordenadas.get(ordenadas.size() - 1).pdf().fatura().data();
        if (!YearMonth.from(ini).equals(YearMonth.from(fim))) return VARIOS_MESES;
        String mes = ini.getMonth().getDisplayName(TextStyle.FULL, Locale.of("pt", "PT"));
        return Character.toUpperCase(mes.charAt(0)) + mes.substring(1) + " " + ini.getYear();
    }

    private static List<FaturaLida> ordenar(List<FaturaLida> lidas) {
        return lidas.stream()
                .sorted(Comparator.comparing((FaturaLida l) -> l.pdf().fatura().data())
                        .thenComparing(l -> l.pdf().fatura().numero()))
                .toList();
    }

    /** Tabela do Excel (TableStyleMedium2, como nos templates): dá filtros, ordenação e linhas alternadas. */
    private static void tabela(XSSFSheet s, String nome, int linhaCab, int ultimaLinha, int ultimaColuna) {
        AreaReference area = new AreaReference(
                new CellReference(linhaCab, 0), new CellReference(ultimaLinha, ultimaColuna),
                SpreadsheetVersion.EXCEL2007);
        XSSFTable t = s.createTable(area);
        t.setName(nome);
        t.setDisplayName(nome);

        CTTable ct = t.getCTTable();
        if (!ct.isSetAutoFilter()) ct.addNewAutoFilter().setRef(area.formatAsString());
        CTTableStyleInfo estilo = ct.isSetTableStyleInfo() ? ct.getTableStyleInfo() : ct.addNewTableStyleInfo();
        estilo.setName("TableStyleMedium2");
        estilo.setShowFirstColumn(false);
        estilo.setShowLastColumn(false);
        estilo.setShowRowStripes(true);
        estilo.setShowColumnStripes(false);

        t.updateHeaders();   // nomes das colunas = texto das células do cabeçalho
    }

    /** Cores da coluna Estado (E): verde = Pago, vermelho = Não pago, amarelo = Nota Crédito. */
    private static void coresEstado(XSSFSheet s) {
        XSSFSheetConditionalFormatting cf = s.getSheetConditionalFormatting();
        CellRangeAddress[] coluna = {new CellRangeAddress(4, ULTIMA_LINHA_EXCEL, 4, 4)};
        regraCor(cf, coluna, NOTA_CREDITO, 0x9C5700, 0xFFEB9C);
        regraCor(cf, coluna, NAO_PAGO, 0x9C0006, 0xFFC7CE);
        regraCor(cf, coluna, PAGO, 0x006100, 0xC6EFCE);
    }

    /** Regra "célula igual a ..." (igual exato, para "Pago" não apanhar "Não pago"). */
    private static void regraCor(XSSFSheetConditionalFormatting cf, CellRangeAddress[] zona,
                                 String texto, int corLetra, int corFundo) {
        XSSFConditionalFormattingRule regra =
                cf.createConditionalFormattingRule(ComparisonOperator.EQUAL, "\"" + texto + "\"");
        XSSFFontFormatting letra = regra.createFontFormatting();
        letra.setFontColor(rgb(corLetra));
        XSSFPatternFormatting fundo = regra.createPatternFormatting();
        fundo.setFillBackgroundColor(rgb(corFundo));
        fundo.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
        cf.addConditionalFormatting(zona, regra);
    }

    private static XSSFColor rgb(int hex) {
        return new XSSFColor(new byte[]{(byte) (hex >> 16), (byte) (hex >> 8), (byte) hex}, null);
    }

    private static XSSFFont fonte(XSSFWorkbook wb, String nome, int tamanho, boolean negrito, int cor) {
        XSSFFont f = wb.createFont();
        f.setFontName(nome);
        f.setFontHeightInPoints((short) tamanho);
        f.setBold(negrito);
        f.setColor(rgb(cor));
        return f;
    }

    /** Estilo de célula. fundo = -1 para sem cor; formato = null para "Geral". */
    private static XSSFCellStyle estilo(XSSFWorkbook wb, XSSFFont fonte, int fundo, HorizontalAlignment horizontal,
                                        String formato, boolean bordas) {
        XSSFCellStyle st = wb.createCellStyle();
        st.setFont(fonte);
        if (fundo >= 0) {
            st.setFillForegroundColor(rgb(fundo));
            st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        if (horizontal != null) st.setAlignment(horizontal);
        st.setVerticalAlignment(VerticalAlignment.CENTER);
        if (formato != null) st.setDataFormat(wb.createDataFormat().getFormat(formato));
        if (bordas) {
            st.setBorderTop(BorderStyle.THIN);
            st.setBorderBottom(BorderStyle.THIN);
            st.setBorderLeft(BorderStyle.THIN);
            st.setBorderRight(BorderStyle.THIN);
        }
        return st;
    }

    private static void texto(Row r, int coluna, String valor, XSSFCellStyle estilo) {
        Cell c = r.createCell(coluna);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    /** Escreve a data (ou, se for nula, só a célula vazia com o estilo, para manter as bordas). */
    private static void data(Row r, int coluna, LocalDate d, XSSFCellStyle estilo) {
        Cell c = r.createCell(coluna);
        c.setCellStyle(estilo);
        if (d != null) c.setCellValue(Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant()));
    }

    private static void dinheiro(Row r, int coluna, BigDecimal v, XSSFCellStyle estilo) {
        Cell c = r.createCell(coluna);
        c.setCellValue(v.doubleValue());
        c.setCellStyle(estilo);
    }

    /** Lista pendente (dropdown) para uma coluna, da linha 2 à 3000. */
    private static void lista(XSSFSheet s, int coluna, String[] opcoes) {
        DataValidationHelper h = s.getDataValidationHelper();
        DataValidation v = h.createValidation(h.createExplicitListConstraint(opcoes),
                new CellRangeAddressList(1, 3000, coluna, coluna));
        s.addValidationData(v);
    }

    private static byte[] bytes(XSSFWorkbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }
}
