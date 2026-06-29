package seda_project.control_alt_defeat.gamebox.settings;

public record WindowResolution(int width, int height) {

    public WindowResolution {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
    }

    public String label() {
        return width + " x " + height;
    }

    @Override
    public String toString() {
        return label();
    }
}
