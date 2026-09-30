package greenebolt.autotrade;

public record GlintColor(int red, int green, int blue) {
    public static GlintColor parse(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{6}")) return null;
        try {
            int rgb = Integer.parseInt(value, 16);
            return new GlintColor((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public String hex() { return String.format("%02X%02X%02X", red, green, blue); }
}
