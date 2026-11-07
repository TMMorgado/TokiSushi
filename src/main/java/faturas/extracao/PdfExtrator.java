package faturas.extracao;

import faturas.dominio.Fatura;
import faturas.dominio.Linha;
import faturas.dominio.Pagamento;
import faturas.dominio.ResultadoPdf;
import faturas.Service.CloudflareD1Service; // <-- Certifica-te de que o pacote do serviço corresponde ao teu (service em minúsculas)
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Lê um PDF: QR AT (cabeçalho), pagamento e linhas de artigos. Tudo em memória, nada é guardado. */
public class PdfExtrator {

    private static final Pattern COPIA = Pattern.compile("\\b(DUPLICADO|TRIPLICADO)\\b");
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.05");
    private static final int PREVIEW_DPI = 110;
    private static final int PREVIEW_MAX_PAGINAS = 4;

    private final int dpi;
    private final CloudflareD1Service d1Service;

    public PdfExtrator(int dpi, CloudflareD1Service d1Service) {
        this.dpi = dpi;
        this.d1Service = d1Service;
    }

    public ResultadoPdf processar(String ficheiro, byte[] conteudo) {
        List<String> textos = new ArrayList<>();
        List<String> previews = new ArrayList<>();
        Map<String, Fatura> encontradas = new LinkedHashMap<>();

        try (PDDocument doc = Loader.loadPDF(conteudo)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            PDFRenderer renderer = new PDFRenderer(doc);

            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String texto = stripper.getText(doc);
                if (COPIA.matcher(texto).find()) continue;
                textos.add(texto);

                BufferedImage imagem = renderer.renderImageWithDPI(i, dpi);
                for (String qr : QrReader.ler(imagem)) {
                    QrAtParser.parse(qr).ifPresent(f -> encontradas.putIfAbsent(f.chave(), f));
                }

                if (previews.size() < PREVIEW_MAX_PAGINAS) {
                    previews.add(Previews.jpegDataUri(renderer.renderImageWithDPI(i, PREVIEW_DPI), 0.8f));
                }
            }
        } catch (IOException | RuntimeException e) {
            return ResultadoPdf.falhada(ficheiro, "Não foi possível ler o PDF: " + e.getMessage(), previews);
        }

        if (encontradas.isEmpty()) {
            return ResultadoPdf.falhada(ficheiro, "Nenhum QR code AT foi lido: processar manualmente.", previews);
        }
        if (encontradas.size() > 1) {
            return ResultadoPdf.falhada(ficheiro,
                    "O PDF contém " + encontradas.size() + " faturas diferentes: processar manualmente.", previews);
        }

        Fatura fatura = encontradas.values().iterator().next();
        List<String> avisos = new ArrayList<>();
        if (!fatura.totaisCoerentes()) {
            avisos.add("As bases e o IVA do QR não batem com o total da fatura.");
        }

        String todo = String.join("\n", textos);
        Pagamento pagamento = PagamentoParser.detetar(todo, fatura);

        List<Linha> linhas = List.of();

        if (LinhaParsers.suporta(fatura.nifEmitente())) {
            List<Linha> lidas = new ArrayList<>();
            for (String t : textos) lidas.addAll(LinhaParsers.ler(fatura.nifEmitente(), t));
            linhas = lidas;
            if (linhas.isEmpty()) {
                avisos.add("Nenhum artigo foi lido.");
            } else {
                BigDecimal soma = linhas.stream().map(Linha::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (soma.subtract(fatura.base().abs()).abs().compareTo(TOLERANCIA) > 0) {
                    avisos.add("A soma dos artigos (" + soma.toPlainString() + ") difere da base da fatura ("
                            + fatura.base().toPlainString() + "): podem faltar linhas.");
                }
            }
        } else {
            avisos.add("Ainda não há leitor de artigos para este fornecedor (NIF " + fatura.nifEmitente() + ").");
        }

        // Criamos o resultado final
        ResultadoPdf resultado = new ResultadoPdf(ficheiro, fatura, pagamento, linhas, avisos, previews);

        // 👉 Sincronizar com a Cloudflare D1 automaticamente após processar com sucesso
        try {
            guardarNaCloudflare(fatura, pagamento, linhas);
        } catch (Exception e) {
            avisos.add("Erro ao sincronizar com a Cloudflare D1: " + e.getMessage());
        }

        return resultado;
    }

    private void guardarNaCloudflare(Fatura fatura, Pagamento pagamento, List<Linha> linhas) {
        String mesAno = fatura.data() != null ? fatura.data().toString().substring(0, 7) : "2026-11";
        String fornecedorNome = "Fornecedor NIF " + fatura.nifEmitente();

        // 1. Inserir ou atualizar a fatura principal
        String sqlResumo = """
            INSERT INTO faturas_resumo (id, mes_ano, data_emissao, fornecedor, numero_fatura, valor, estado, dia_pagamento)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
            valor = excluded.valor,
            estado = excluded.estado,
            dia_pagamento = excluded.dia_pagamento
        """;

        List<Object> paramsResumo = List.of(
                fatura.chave(),
                mesAno,
                fatura.data() != null ? fatura.data().toString() : "",
                fornecedorNome,
                fatura.numero(),
                fatura.valorComSinal().doubleValue(),
                estadoTexto(pagamento),
                pagamento.dia() != null ? pagamento.dia().toString() : ""
        );

        d1Service.executarSql(sqlResumo, paramsResumo);

        // 2. Inserir os artigos/linhas
        for (Linha linha : linhas) {
            String artigoId = fatura.chave() + "_" + linha.codigo() + "_" + linha.descricao();

            String sqlArtigo = """
                INSERT INTO faturas_artigos (id, mes_ano, categoria, categoria2, artigo, loja, nr_fatura, data_emissao, codigo, qtd, unidade, valor)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                qtd = excluded.qtd,
                valor = excluded.valor
            """;

            List<Object> paramsArtigo = List.of(
                    artigoId,
                    mesAno,
                    "POR CLASSIFICAR",
                    "POR CLASSIFICAR",
                    linha.descricao(),
                    fornecedorNome,
                    fatura.numero(),
                    fatura.data() != null ? fatura.data().toString() : "",
                    linha.codigo(),
                    linha.qtd().doubleValue(),
                    linha.unidade(),
                    linha.valor().doubleValue()
            );

            d1Service.executarSql(sqlArtigo, paramsArtigo);
        }
    }

    private String estadoTexto(Pagamento p) {
        return switch (p.estado()) {
            case "PAGO" -> "Pago";
            case "NOTA DE CRÉDITO" -> "Nota Crédito";
            default -> "Não pago";
        };
    }
}