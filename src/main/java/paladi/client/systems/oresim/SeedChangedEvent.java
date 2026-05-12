package paladi.client.systems.oresim;

public class SeedChangedEvent {
    private static final SeedChangedEvent INSTANCE = new SeedChangedEvent();
    public long seed;

    public static SeedChangedEvent get(long seed) {
        INSTANCE.seed = seed;
        return INSTANCE;
    }
}
