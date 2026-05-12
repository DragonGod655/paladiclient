package paladi.client.systems.modules;

import net.minecraft.item.Items;

public class Categories {
    // Danh mục KRYPTON+ mới
    public static Category COMBAT;
    public static Category MISC;
    public static Category DONUT;
    public static Category BASEFINDING;
    public static Category RENDER;
    public static Category CLIENT;

    // Giữ lại tên cũ để tương thích (Tránh lỗi build)
    public static Category Combat;
    public static Category Player;
    public static Category Movement;
    public static Category Render;
    public static Category World;
    public static Category Misc;

    public static void init() {
        // Khởi tạo các danh mục mới với biểu tượng tương ứng
        COMBAT = new Category("COMBAT", Items.NETHERITE_SWORD.getDefaultStack());
        MISC = new Category("MISC", Items.LAVA_BUCKET.getDefaultStack());
        DONUT = new Category("DONUT", Items.GOLDEN_APPLE.getDefaultStack());
        BASEFINDING = new Category("BASEFINDING", Items.ENDER_EYE.getDefaultStack());
        RENDER = new Category("RENDER", Items.ENDER_CHEST.getDefaultStack());
        CLIENT = new Category("CLIENT", Items.COMMAND_BLOCK.getDefaultStack());

        // Trỏ các tên cũ vào danh mục mới
        Combat = COMBAT;
        Player = MISC;
        Movement = MISC;
        Render = RENDER;
        World = BASEFINDING;
        Misc = MISC;
    }
}
