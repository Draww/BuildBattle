package pl.plajer.buildbattle.arena;

import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Tom on 27/07/2014.
 */
public class ArenaRegistry {

    private static List<Arena> arenas = new ArrayList<>();

    public static List<Arena> getArenas() {
        return arenas;
    }

    public static boolean isInArena(Player p) {
        for(Arena arena : arenas) {
            if(arena.getPlayers().contains(p)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static Arena getArena(Player p) {
        if(p == null) return null;
        if(!p.isOnline()) return null;

        for(Arena arena : arenas) {
            for(Player player : arena.getPlayers()) {
                if(player.getUniqueId() == p.getUniqueId()) {
                    return arena;
                }
            }
        }
        return null;
    }

    public static void registerArena(Arena arena) {
        arenas.add(arena);
    }

    public static Arena getArena(String ID) {
        for(Arena arena : arenas) {
            if(arena.getID().equalsIgnoreCase(ID)) {
                return arena;
            }
        }
        return null;
    }

}