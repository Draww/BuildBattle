/*
 * BuildBattle - Ultimate building competition minigame
 * Copyright (C) 2019  Plajer's Lair - maintained by Plajer and Tigerpanzer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pl.plajer.buildbattle.arena.managers.plots;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.citizensnpcs.api.CitizensAPI;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.WeatherType;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import pl.plajer.buildbattle.Main;
import pl.plajer.buildbattle.api.event.plot.BBPlotResetEvent;
import pl.plajer.buildbattle.arena.impl.BaseArena;
import pl.plajer.buildbattle.user.User;
import pl.plajer.buildbattle.utils.Cuboid;
import pl.plajer.buildbattle.utils.Utils;
import pl.plajerlair.core.services.exception.ReportedException;
import pl.plajerlair.core.utils.XMaterial;

/**
 * Created by Tom on 17/08/2015.
 */
public class Plot {

  private BaseArena arena;
  private Main plugin = JavaPlugin.getPlugin(Main.class);
  private Cuboid cuboid;
  private int points;
  private List<Player> owners = new ArrayList<>();
  private Map<Location, Particle> particles = new HashMap<>();
  private Time time = Time.WORLD_TIME;
  private Biome plotDefaultBiome;
  private WeatherType weatherType = WeatherType.CLEAR;
  private int entities = 0;

  public Plot(BaseArena arena, Biome biome) {
    this.arena = arena;
    plotDefaultBiome = biome;
  }

  public int getEntities() {
    return entities;
  }

  public void addEntity() {
    entities++;
  }

  public void removeEntity() {
    if (entities == 0) return;
    entities--;
  }

  public Map<Location, Particle> getParticles() {
    return particles;
  }

  public void addParticle(Location location, Particle effect) {
    particles.put(location, effect);
  }

  public Biome getPlotDefaultBiome() {
    return plotDefaultBiome;
  }

  public WeatherType getWeatherType() {
    return weatherType;
  }

  public void setWeatherType(WeatherType weatherType) {
    this.weatherType = weatherType;
  }

  public Time getTime() {
    return time;
  }

  public void setTime(Time time) {
    this.time = time;
  }

  public Cuboid getCuboid() {
    return cuboid;
  }

  public void setCuboid(Cuboid cuboid) {
    this.cuboid = cuboid;
  }

  public List<Player> getOwners() {
    return owners;
  }

  public void setOwners(List<Player> players) {
    this.owners = players;
  }

  public void addOwner(Player player) {
    this.owners.add(player);
  }

  public void fullyResetPlot() {
    try {
      resetPlot();
      if (owners != null && !owners.isEmpty()) {
        for (Player p : owners) {
          User user = plugin.getUserManager().getUser(p);
          user.setCurrentPlot(null);
          this.setOwners(new ArrayList<>());
          this.setPoints(0);
        }
      }
      getParticles().clear();
    } catch (Exception ex) {
      new ReportedException(JavaPlugin.getPlugin(Main.class), ex);
    }
  }

  public void resetPlot() {
    try {
      for (Block block : cuboid.blockList()) {
        if (block.getType() != Material.AIR) {
          block.setType(Material.AIR);
        }
      }
      getParticles().clear();
      for (Player p : owners) {
        p.resetPlayerWeather();
        setWeatherType(p.getPlayerWeather());
        p.resetPlayerTime();
      }
      for (Entity entity : cuboid.getCenter().getWorld().getEntities()) {
        if (cuboid.isInWithMarge(entity.getLocation(), 3)) {
          if (JavaPlugin.getPlugin(Main.class).getServer().getPluginManager().isPluginEnabled("Citizens")) {
            if (CitizensAPI.getNPCRegistry().isNPC(entity)) continue;
          }
          if (entity.getType() != EntityType.PLAYER) {
            entity.remove();
          }
        }
      }
      for (Block block : getCuboid().blockList()) {
        block.setBiome(plotDefaultBiome);
      }
      for (Chunk chunk : getCuboid().chunkList()) {
        for (Player p : Bukkit.getOnlinePlayers()) {
          Utils.sendPacket(p, Utils.getNMSClass("PacketPlayOutMapChunk").getConstructor(Utils.getNMSClass("Chunk"), int.class)
              .newInstance(chunk.getClass().getMethod("getHandle").invoke(chunk), 65535));
        }
      }
      changeFloor(XMaterial.fromString(plugin.getConfig().getString("Default-Floor-Material-Name", "LOG").toUpperCase()).parseMaterial());
      cuboid.getCenter().getWorld().setBiome(cuboid.getMinPoint().getBlockX(), cuboid.getMaxPoint().getBlockZ(), plotDefaultBiome);
      BBPlotResetEvent event = new BBPlotResetEvent(arena, this);
      Bukkit.getServer().getPluginManager().callEvent(event);
    } catch (Exception ex) {
      new ReportedException(JavaPlugin.getPlugin(Main.class), ex);
    }
  }

  public int getPoints() {
    return points;
  }

  public void setPoints(int points) {
    this.points = points;
  }

  private void changeFloor(Material material) {
    try {
      double y;
      if (cuboid.getMinPoint().getY() > cuboid.getMaxPoint().getY()) {
        y = cuboid.getMaxPoint().getY();
      } else {
        y = cuboid.getMinPoint().getY();
      }
      Location min = cuboid.getMinPoint();
      Location max = cuboid.getMaxPoint();
      for (int x = min.getBlockX(); x <= max.getBlockX(); x = x + 1) {
        for (int z = min.getBlockZ(); z <= max.getBlockZ(); z = z + 1) {
          Location tmpblock = new Location(cuboid.getMaxPoint().getWorld(), x, y, z);
          tmpblock.getBlock().setType(material);
        }
      }
    } catch (Exception ex) {
      new ReportedException(JavaPlugin.getPlugin(Main.class), ex);
    }
  }

  public void changeFloor(Material material, byte data) {
    try {
      if (material == Material.WATER_BUCKET) material = Material.WATER;
      if (material == Material.LAVA_BUCKET) material = Material.LAVA;
      double y;
      if (cuboid.getMinPoint().getY() > cuboid.getMaxPoint().getY()) {
        y = cuboid.getMaxPoint().getY();
      } else {
        y = cuboid.getMinPoint().getY();
      }
      Location min = cuboid.getMinPoint();
      Location max = cuboid.getMaxPoint();
      for (int x = min.getBlockX(); x <= max.getBlockX(); x = x + 1) {
        for (int z = min.getBlockZ(); z <= max.getBlockZ(); z = z + 1) {
          Location tmpblock = new Location(cuboid.getMaxPoint().getWorld(), x, y, z);
          tmpblock.getBlock().setType(material);
          if (plugin.is1_11_R1() || plugin.is1_12_R1()) {
            tmpblock.getBlock().setData(data);
          }
        }
      }
    } catch (Exception ex) {
      new ReportedException(JavaPlugin.getPlugin(Main.class), ex);
    }
  }

  public Material getFloorMaterial() {
    try {
      Location location;
      if (cuboid.getMinPoint().getY() > cuboid.getMaxPoint().getY()) {
        location = cuboid.getMaxPoint().clone();
      } else {
        location = cuboid.getMinPoint().clone();
      }
      Material material = location.getBlock().getType();
      if (material == Material.WATER || ((plugin.is1_11_R1() || plugin.is1_12_R1()) && material == Material.valueOf("STATIONARY_WATER"))) {
        return Material.WATER_BUCKET;
      }
      if (material == Material.LAVA || ((plugin.is1_11_R1() || plugin.is1_12_R1()) && material == Material.valueOf("STATIONARY_LAVA"))) {
        return Material.LAVA_BUCKET;
      }
      if (material == Material.AIR || material == null) return Material.REDSTONE_BLOCK;
      return material;
    } catch (Exception ex) {
      new ReportedException(JavaPlugin.getPlugin(Main.class), ex);
      return Material.REDSTONE_BLOCK;
    }
  }

  public Location getTeleportLocation() {
    try {
      Location tploc = cuboid.getCenter();
      while (tploc.getBlock().getType() != Material.AIR || tploc.add(0, 1, 0).getBlock().getType() != Material.AIR) tploc = tploc.add(0, 1, 0);
      boolean enclosed = false;
      int counter = 0;
      Location location = tploc.clone();
      while (counter != 10) {
        if (!(location.getBlock().getType() == Material.BARRIER || location.getBlock().getType() == Material.AIR)) {
          enclosed = true;
          tploc = location;
          counter = 9;
        }
        location.add(0, 1, 0);
        counter++;
      }
      if (enclosed) {
        while (tploc.getBlock().getType() != Material.AIR || tploc.add(0, 1, 0).getBlock().getType() != Material.AIR) {
          tploc = tploc.add(0, 1, 0);
        }
      }
      return tploc;
    } catch (Exception ex) {
      new ReportedException(JavaPlugin.getPlugin(Main.class), ex);
      return new Location(Bukkit.getWorld("world"), 0, 0, 0);
    }
  }

  /**
   * Enum that represents current plot time
   */
  public enum Time {
    WORLD_TIME(-1), DAY(1000), SUNSET(12000), SUNRISE(23000), NIGHT(13000);

    private long ticks;

    Time(long ticks) {
      this.ticks = ticks;
    }

    public static long format(Time time, long currTime) {
      if (time == Time.WORLD_TIME) {
        return currTime;
      }
      return time.getTicks();
    }

    public long getTicks() {
      return ticks;
    }
  }

}
