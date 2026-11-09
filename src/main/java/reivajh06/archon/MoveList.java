package reivajh06.archon;

final class MoveList {
    final int[] moves = new int[256];
    final int[] scores = new int[256];
    int size;
    void add(int move) { moves[size++] = move; }
    int pick(int index) {
        int best = index;
        for (int i = index + 1; i < size; i++) if (scores[i] > scores[best]) best = i;
        int m = moves[best]; moves[best] = moves[index]; moves[index] = m;
        int s = scores[best]; scores[best] = scores[index]; scores[index] = s;
        return m;
    }
}
