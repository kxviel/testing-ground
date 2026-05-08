package seda_project.control_alt_defeat.gamebox.model.memory;

// Represents one card on the memory board.
public class Card {
    private final int id;
    private final String symbol;
    private boolean faceUp;
    private boolean matched;

    /**
     * Creates a face-down, unmatched card.
     *
     * @param id     zero-based board position
     * @param symbol symbol shown when the card is face up
     */
    public Card(int id, String symbol) {
        this.id = id;
        this.symbol = symbol;
        this.faceUp = false;
        this.matched = false;
    }

    /**
     * @return zero-based board position
     */
    public int getId() {
        return id;
    }

    /**
     * @return card symbol used for matching
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * @return true when the card is currently visible
     */
    public boolean isFaceUp() {
        return faceUp;
    }

    /**
     * @return true when the card has been matched and removed from play
     */
    public boolean isMatched() {
        return matched;
    }

    /**
     * Sets whether the card is visible.
     *
     * @param faceUp true to reveal the card
     */
    public void setFaceUp(boolean faceUp) {
        this.faceUp = faceUp;
    }

    /**
     * Sets whether the card has been matched.
     *
     * @param matched true when the card belongs to a completed match
     */
    public void setMatched(boolean matched) {
        this.matched = matched;
        if (matched)
            this.faceUp = true;
    }

    /**
     * @return true if the player may select this card
     */
    public boolean isSelectable() {
        return !matched && !faceUp;
    }

    @Override
    public String toString() {
        return "Card{id=" + id + ", sym=" + symbol + ", up=" + faceUp + ", matched=" + matched + "}";
    }
}