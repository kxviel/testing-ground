package seda_project.control_alt_defeat.gamebox.model.memory;

public class Card {
    private final int id;
    private final String symbol;
    private boolean faceUp;
    private boolean matched;

    public Card(int id, String symbol) {
        this.id = id;
        this.symbol = symbol;
        this.faceUp = false;
        this.matched = false;
    }

    public int getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean isFaceUp() {
        return faceUp;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setFaceUp(boolean faceUp) {
        this.faceUp = faceUp;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
        if (matched)
            this.faceUp = true;
    }

    public boolean isSelectable() {
        return !matched && !faceUp;
    }

    @Override
    public String toString() {
        return "Card{id=" + id + ", sym=" + symbol + ", up=" + faceUp + ", matched=" + matched + "}";
    }
}
