package faturas.excel;

import faturas.dominio.Fatura;
import faturas.dominio.FaturaLida;
import faturas.dominio.Linha;
import faturas.dominio.Pagamento;
import faturas.dominio.ResultadoPdf;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelExporterTest {

    private static FaturaLida exemplo(String tipo) {
        Fatura f = new Fatura("502030712", "519521510", tipo, LocalDate.of(2026, 11, 3), "FAC 1/1",
                new BigDecimal("30.50"), new BigDecimal("538.83"), true);
        Linha linha = new Linha("2880817192000", "MC SALMAO", new BigDecimal("19.2"), "KG", new BigDecimal("162.24"));
        ResultadoPdf r = new ResultadoPdf("x.pdf", f, new Pagamento("PAGO", LocalDate.of(2026, 11, 3)),
                List.of(linha), List.of());
        return new FaturaLida("Makro", r);
    }

    private static Sheet abrir(byte[] bytes, XSSFWorkbook[] guarda) throws IOException {
        guarda[0] = new XSSFWorkbook(new ByteArrayInputStream(bytes));
        return guarda[0].getSheetAt(0);
    }

    @Test
    void excelDeFaturas() throws IOException {
        XSSFWorkbook[] wb = new XSSFWorkbook[1];
        try {
            Sheet s = abrir(ExcelExporter.faturas(List.of(exemplo("FT"))), wb);
            assertThat(s.getRow(0).getCell(0).getStringCellValue()).isEqualTo("DATA EMISSAO");
            assertThat(s.getLastRowNum()).isEqualTo(1);
            assertThat(s.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Makro");
            assertThat(s.getRow(1).getCell(2).getStringCellValue()).isEqualTo("FAC 1/1");
            assertThat(s.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(538.83);
            assertThat(s.getRow(1).getCell(4).getStringCellValue()).isEqualTo("PAGO");
        } finally {
            wb[0].close();
        }
    }

    @Test
    void notaDeCreditoFicaNegativa() throws IOException {
        XSSFWorkbook[] wb = new XSSFWorkbook[1];
        try {
            Sheet s = abrir(ExcelExporter.faturas(List.of(exemplo("NC"))), wb);
            assertThat(s.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(-538.83);
        } finally {
            wb[0].close();
        }
    }

    @Test
    void excelDeArtigos() throws IOException {
        XSSFWorkbook[] wb = new XSSFWorkbook[1];
        try {
            Sheet s = abrir(ExcelExporter.artigos(List.of(exemplo("FT"))), wb);
            assertThat(s.getRow(0).getCell(0).getStringCellValue()).isEqualTo("CATEGORIA");
            assertThat(s.getRow(1).getCell(0).getStringCellValue()).isEqualTo("POR CLASSIFICAR");
            assertThat(s.getRow(1).getCell(2).getStringCellValue()).isEqualTo("MC SALMAO");
            assertThat(s.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Makro");
            assertThat(s.getRow(1).getCell(6).getStringCellValue()).isEqualTo("2880817192000");
            assertThat(s.getRow(1).getCell(9).getNumericCellValue()).isEqualTo(162.24);
        } finally {
            wb[0].close();
        }
    }
}
