package faturas.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Faturas carregadas nesta sessão de trabalho. Vai acumulando entre carregamentos
 * ("Carregar mais" não perde o que já lá estava) e impede a mesma fatura de entrar duas vezes.
 */
public final class Lote {

    private final List<Cartao> cartoes = new ArrayList<>();

    public synchronized List<Cartao> cartoes() {
        return List.copyOf(cartoes);
    }

    public synchronized void adicionar(Cartao cartao) {
        cartoes.add(cartao);
    }

    public synchronized boolean remover(String id) {
        return cartoes.removeIf(c -> c.id().equals(id));
    }

    public synchronized void limpar() {
        cartoes.clear();
    }

    /** Cartão já carregado com a mesma fatura (mesmo emitente e número). */
    public synchronized Optional<Cartao> comFatura(String chave) {
        return cartoes.stream()
                .filter(c -> c.pdf().lida() && c.pdf().fatura().chave().equals(chave))
                .findFirst();
    }

    /** PDF que não foi possível ler, já carregado com o mesmo nome. */
    public synchronized Optional<Cartao> naoLidaComNome(String nome) {
        return cartoes.stream()
                .filter(c -> !c.pdf().lida() && c.pdf().ficheiro().equals(nome))
                .findFirst();
    }
}
