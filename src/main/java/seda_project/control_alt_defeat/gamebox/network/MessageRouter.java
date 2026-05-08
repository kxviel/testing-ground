package seda_project.control_alt_defeat.gamebox.network;

import java.util.function.Consumer;

// Mutable message callback used while switching from the main menu to the game board.
public class MessageRouter implements Consumer<String> {

    private volatile Consumer<String> delegate = msg -> {
    };
    private volatile Runnable disconnectDelegate = () -> {
    };

    /**
     * Sets the active message destination.
     *
     * @param delegate consumer to receive future messages, or null for no-op
     */
    public void setDelegate(Consumer<String> delegate) {
        this.delegate = delegate == null ? msg -> {
        } : delegate;
    }

    /**
     * Sets the active disconnect handler.
     *
     * @param disconnectDelegate handler to run on disconnect, or null for no-op
     */
    public void setDisconnectDelegate(Runnable disconnectDelegate) {
        this.disconnectDelegate = disconnectDelegate == null ? () -> {
        } : disconnectDelegate;
    }

    /**
     * Forwards one received message to the current delegate.
     *
     * @param msg raw protocol message
     */
    @Override
    public void accept(String msg) {
        delegate.accept(msg);
    }

    /**
     * Runs the current disconnect delegate.
     */
    public void onDisconnect() {
        disconnectDelegate.run();
    }

    /**
     * @return this router as a {@link Consumer} for socket callbacks
     */
    public Consumer<String> asConsumer() {
        return this;
    }

    /**
     * @return this router as a {@link Runnable} for disconnect callbacks
     */
    public Runnable asDisconnectRunnable() {
        return this::onDisconnect;
    }
}