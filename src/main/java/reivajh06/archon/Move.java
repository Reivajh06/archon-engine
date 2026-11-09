package reivajh06.archon;

/** Packed moves: source, destination, promotion type, and special flags. a1 is bit zero. */
public final class Move {
    public static final int CAPTURE = 1 << 15, EN_PASSANT = 1 << 16, CASTLE = 1 << 17;
    private Move() {}
    public static int of(int from, int to) { return from | (to << 6); }
    public static int from(int m) { return m & 63; }
    public static int to(int m) { return (m >>> 6) & 63; }
    public static int promotion(int m) { return (m >>> 12) & 7; }
    public static boolean capture(int m) { return (m & CAPTURE) != 0; }
    public static String square(int s) { return "" + (char) ('a' + s % 8) + (char) ('1' + s / 8); }
    public static int square(String s) {
        if (s.length() != 2 || s.charAt(0) < 'a' || s.charAt(0) > 'h' || s.charAt(1) < '1' || s.charAt(1) > '8')
            throw new IllegalArgumentException("Invalid square: " + s);
        return s.charAt(0) - 'a' + 8 * (s.charAt(1) - '1');
    }
    public static String uci(int m) {
        return square(from(m)) + square(to(m)) + (promotion(m) == 0 ? "" : " pnbrqk".charAt(promotion(m) + 1));
    }
}
