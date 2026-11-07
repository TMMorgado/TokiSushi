package faturas.web;

import faturas.FaturasProperties;
import faturas.dominio.FaturaLida;
import faturas.dominio.ResultadoPdf;
import faturas.excel.ExcelExporter;
import faturas.extracao.PdfExtrator;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
public class UploadController {

    private static final String SESSAO_LOTE = "lote";
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PdfExtrator extrator;
    private final FaturasProperties props;

    public UploadController(PdfExtrator extrator, FaturasProperties props) {
        this.extrator = extrator;
        this.props = props;
    }

    // ---------- passo 1: carregar ----------

    @GetMapping("/")
    public String inicio(Model model, HttpSession sessao) {
        model.addAttribute("existentes", lote(sessao).cartoes().size());
        return "index";
    }

    /** Lê os PDFs e JUNTA-os ao que já estava carregado. Depois redireciona (refrescar a página é seguro). */
    @PostMapping("/processar")
    public String processar(@RequestParam("ficheiros") List<MultipartFile> ficheiros,
                            HttpSession sessao, RedirectAttributes flash) {
        Lote lote = lote(sessao);
        List<String> ignoradas = new ArrayList<>();
        int novas = 0;

        for (MultipartFile ficheiro : ficheiros) {
            if (ficheiro.isEmpty()) continue;
            String nome = ficheiro.getOriginalFilename() == null ? "ficheiro.pdf" : ficheiro.getOriginalFilename();

            ResultadoPdf r;
            try {
                r = extrator.processar(nome, ficheiro.getBytes());
            } catch (IOException e) {
                r = ResultadoPdf.falhada(nome, "Não foi possível receber o ficheiro.");
            }

            // Já carregada? (compara com TUDO o que está no lote, também de carregamentos anteriores)
            if (r.lida()) {
                Optional<Cartao> igual = lote.comFatura(r.fatura().chave());
                if (igual.isPresent()) {
                    ignoradas.add(nome + ": a fatura " + r.fatura().numero()
                            + " já estava carregada (ficheiro " + igual.get().pdf().ficheiro() + ").");
                    continue;
                }
            } else if (lote.naoLidaComNome(nome).isPresent()) {
                ignoradas.add(nome + ": este ficheiro já foi carregado e continua por rever.");
                continue;
            }

            List<String> avisos = new ArrayList<>(r.avisos());
            String fornecedor = "—";
            if (r.lida()) {
                String nif = r.fatura().nifEmitente();
                Optional<String> conhecido = props.fornecedor(nif);
                fornecedor = conhecido.orElse("NIF " + nif);
                if (conhecido.isEmpty()) {
                    avisos.add("Fornecedor desconhecido (NIF " + nif + "): falta dar-lhe um nome.");
                }
            }
            lote.adicionar(new Cartao(UUID.randomUUID().toString(), r, fornecedor, avisos));
            novas++;
        }

        flash.addFlashAttribute("novas", novas);
        flash.addFlashAttribute("ignoradas", ignoradas);
        return "redirect:/faturas";
    }

    // ---------- passo 2: rever ----------

    @GetMapping("/faturas")
    public String faturas(Model model, HttpSession sessao) {
        List<Cartao> cartoes = new ArrayList<>(lote(sessao).cartoes());
        if (cartoes.isEmpty()) return "redirect:/";

        // As que precisam de atenção aparecem primeiro (a ordem das restantes mantém-se)
        cartoes.sort(Comparator.comparing(Cartao::precisaRevisao).reversed());

        List<FaturaLida> lidas = lidas(cartoes);
        model.addAttribute("cartoes", cartoes);
        model.addAttribute("total", cartoes.size());
        model.addAttribute("porRever", cartoes.stream().filter(Cartao::precisaRevisao).count());
        model.addAttribute("temExcel", !lidas.isEmpty());
        model.addAttribute("lidasTotal", lidas.size());
        model.addAttribute("totalArtigos", lidas.stream().mapToInt(l -> l.pdf().linhas().size()).sum());
        model.addAttribute("totalValor", lidas.stream().map(l -> l.pdf().fatura().valorComSinal())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return "resultado";
    }

    @PostMapping("/faturas/{id}/remover")
    public String remover(@PathVariable String id, HttpSession sessao) {
        lote(sessao).remover(id);
        return "redirect:/faturas";
    }

    @PostMapping("/recomecar")
    public String recomecar(HttpSession sessao) {
        lote(sessao).limpar();
        return "redirect:/";
    }

    // ---------- passo 3: descarregar ----------

    @GetMapping("/excel/faturas")
    public ResponseEntity<byte[]> excelFaturas(HttpSession sessao) throws IOException {
        List<FaturaLida> lidas = lidas(lote(sessao).cartoes());
        return lidas.isEmpty() ? voltarAoInicio() : excel("faturas.xlsx", ExcelExporter.faturas(lidas));
    }

    @GetMapping("/excel/artigos")
    public ResponseEntity<byte[]> excelArtigos(HttpSession sessao) throws IOException {
        List<FaturaLida> lidas = lidas(lote(sessao).cartoes());
        return lidas.isEmpty() ? voltarAoInicio() : excel("artigos.xlsx", ExcelExporter.artigos(lidas));
    }

    // ---------- auxiliares ----------

    private static Lote lote(HttpSession sessao) {
        Object o = sessao.getAttribute(SESSAO_LOTE);
        if (o instanceof Lote l) return l;
        Lote novo = new Lote();
        sessao.setAttribute(SESSAO_LOTE, novo);
        return novo;
    }

    /** Só as faturas lidas entram nos Excel. */
    private static List<FaturaLida> lidas(List<Cartao> cartoes) {
        return cartoes.stream()
                .filter(c -> c.pdf().lida())
                .map(c -> new FaturaLida(c.fornecedor(), c.pdf()))
                .toList();
    }

    private static ResponseEntity<byte[]> excel(String nome, byte[] conteudo) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .contentType(XLSX)
                .body(conteudo);
    }

    private static ResponseEntity<byte[]> voltarAoInicio() {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, "/").build();
    }
}
