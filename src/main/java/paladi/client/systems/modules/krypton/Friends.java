package paladi.client.systems.modules.krypton;

import paladi.client.settings.*;
import paladi.client.systems.friends.Friend;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;

import java.util.ArrayList;
import java.util.List;

public class Friends extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showOnline = sgGeneral.add(new BoolSetting.Builder()
        .name("show-online")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> friendList = sgGeneral.add(new StringListSetting.Builder()
        .name("friend-list")
        .description("Danh sách bạn bè. Bấm dấu + để thêm tên bạn bè không muốn đánh.")
        .defaultValue(new ArrayList<>())
        .onChanged(list -> {
            // Khi người dùng thêm/xoá tên trên GUI, đồng bộ với hệ thống Friends gốc
            paladi.client.systems.friends.Friends sys = paladi.client.systems.friends.Friends.get();
            
            // Lấy danh sách cũ để xoá
            List<Friend> toRemove = new ArrayList<>();
            sys.forEach(toRemove::add);
            for (Friend f : toRemove) sys.remove(f);
            
            // Thêm danh sách mới vào
            for (String name : list) {
                sys.add(new Friend(name));
            }
        })
        .build()
    );

    public Friends() { 
        super(Categories.CLIENT, "friends", "Friends list manager for Krypton+."); 
    }

    @Override
    public void onActivate() {
        // Mỗi khi bật module lên, tự động cập nhật lại danh sách hiển thị
        List<String> current = new ArrayList<>();
        paladi.client.systems.friends.Friends.get().forEach(f -> current.add(f.name));
        friendList.set(current);
    }
}
