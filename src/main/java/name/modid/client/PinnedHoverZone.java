package name.modid.client;

public record PinnedHoverZone(int x1, int y1, int x2, int y2, String kind, String id, Object payload) {
    public PinnedHoverZone(int x1, int y1, int x2, int y2, String kind, String id) {
        this(x1, y1, x2, y2, kind, id, null);
    }

    public boolean contains(double x, double y) {
        return x >= this.x1 && x < this.x2 && y >= this.y1 && y < this.y2;
    }
}
