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

package pl.plajer.buildbattle.arena.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import pl.plajer.buildbattle.ConfigPreferences;
import pl.plajer.buildbattle.Main;
import pl.plajer.buildbattle.api.StatsStorage;
import pl.plajer.buildbattle.api.event.game.BBGameEndEvent;
import pl.plajer.buildbattle.api.event.game.BBGameStartEvent;
import pl.plajer.buildbattle.arena.ArenaState;
import pl.plajer.buildbattle.arena.ArenaUtils;
import pl.plajer.buildbattle.arena.managers.GuessTheBuildScoreboardManager;
import pl.plajer.buildbattle.arena.managers.plots.Plot;
import pl.plajer.buildbattle.arena.options.ArenaOption;
import pl.plajer.buildbattle.menus.themevoter.GTBTheme;
import pl.plajer.buildbattle.user.User;
import pl.plajer.buildbattle.utils.Utils;
import pl.plajerlair.core.utils.InventoryUtils;
import pl.plajerlair.core.utils.ItemBuilder;
import pl.plajerlair.core.utils.MinigameUtils;

/**
 * @author Plajer
 * <p>
 * Created at 11.01.2019
 */
public class GuessTheBuildArena extends BaseArena {

  private int round = 1;
  private GTBTheme currentTheme;
  private boolean themeSet;
  private Player currentBuilder;
  //todo cleanable in leave events
  private List<Player> whoGuessed = new ArrayList<>();
  private Map<Player, Integer> playersPoints = new HashMap<>();
  private GuessTheBuildScoreboardManager scoreboardManager;

  public GuessTheBuildArena(String id, Main plugin) {
    super(id, plugin);
    scoreboardManager = new GuessTheBuildScoreboardManager(this);
  }

  @Override
  public void run() {
    try {
      //idle task
      if (getPlayers().size() == 0 && getArenaState() == ArenaState.WAITING_FOR_PLAYERS) {
        return;
      }
      scoreboardManager.updateScoreboard();
      if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.BOSSBAR_ENABLED)) {
        updateBossBar();
      }
      switch (getArenaState()) {
        case WAITING_FOR_PLAYERS:
          if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
            getPlugin().getServer().setWhitelist(false);
          }
          getPlotManager().resetPlotsGradually();
          if (getPlayers().size() < getMinimumPlayers()) {
            if (getTimer() <= 0) {
              setTimer(getPlugin().getConfigPreferences().getTimer(ConfigPreferences.TimerType.LOBBY, this));
              getPlugin().getChatManager().broadcast(this, getPlugin().getChatManager().colorMessage("In-Game.Messages.Lobby-Messages.Waiting-For-Players").replace("%MINPLAYERS%", String.valueOf(getMinimumPlayers())));
              return;
            }
          } else {
            getPlugin().getChatManager().broadcast(this, getPlugin().getChatManager().colorMessage("In-Game.Messages.Lobby-Messages.Enough-Players-To-Start"));
            setArenaState(ArenaState.STARTING);
            Bukkit.getPluginManager().callEvent(new BBGameStartEvent(this));
            setTimer(getPlugin().getConfigPreferences().getTimer(ConfigPreferences.TimerType.LOBBY, this));
            ArenaUtils.showPlayers(this);
          }
          setTimer(getTimer() - 1);
          break;
        case STARTING:
          for (Player player : getPlayers()) {
            player.setExp((float) (getTimer() / getPlugin().getConfig().getDouble("Lobby-Starting-Time", 60)));
            player.setLevel(getTimer());
          }
          if (getPlayers().size() < getMinimumPlayers()) {
            getPlugin().getChatManager().broadcast(this, getPlugin().getChatManager().colorMessage("In-Game.Messages.Lobby-Messages.Waiting-For-Players").replace("%MINPLAYERS%", String.valueOf(getMinimumPlayers())));
            setArenaState(ArenaState.WAITING_FOR_PLAYERS);
            Bukkit.getPluginManager().callEvent(new BBGameStartEvent(this));
            setTimer(getPlugin().getConfigPreferences().getTimer(ConfigPreferences.TimerType.LOBBY, this));
            for (Player player : getPlayers()) {
              player.setExp(1);
              player.setLevel(0);
            }
            break;
          }
          if (getTimer() == 0) {
            if (!getPlotManager().isPlotsCleared()) {
              getPlotManager().resetQueuedPlots();
            }
            setArenaState(ArenaState.IN_GAME);
            getPlotManager().distributePlots();
            getPlotManager().teleportToPlots();
            setTimer(getPlugin().getConfigPreferences().getTimer(ConfigPreferences.TimerType.THEME_VOTE, this));
            for (Player player : getPlayers()) {
              player.getInventory().clear();
              player.setGameMode(GameMode.CREATIVE);
              player.setAllowFlight(true);
              player.setFlying(true);
              ArenaUtils.hidePlayersOutsideTheGame(this, player);
              player.getInventory().setItem(8, getPlugin().getOptionsRegistry().getMenuItem());
              //to prevent Multiverse chaning gamemode bug
              Bukkit.getScheduler().runTaskLater(getPlugin(), () -> player.setGameMode(GameMode.CREATIVE), 20);
            }
            break;
          }
          setTimer(getTimer() - 1);
          break;
        case IN_GAME:
          for (Player p : getPlayers()) {
            if (!playersPoints.containsKey(p)) {
              playersPoints.put(p, 0);
            }
            //todo ineffective?
            playersPoints = Utils.sortByValue(playersPoints);
          }
          if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
            if (getMaximumPlayers() <= getPlayers().size()) {
              getPlugin().getServer().setWhitelist(true);
            } else {
              getPlugin().getServer().setWhitelist(false);
            }
          }
          //todo deprecate the themes selector
          if (currentBuilder == null) {
            //todo out of bounds check!
            currentBuilder = getPlayers().get(round - 1);
            Random r = new Random();

            Inventory inv = Bukkit.createInventory(null, 27, getPlugin().getChatManager().colorMessage("Menus.Guess-The-Build-Theme-Selector.Inventory-Name"));
            inv.setItem(11, new ItemBuilder(new ItemStack(Material.PAPER)).name(getPlugin().getChatManager().colorMessage("Menus.Guess-The-Build-Theme-Selector.Theme-Item-Name")
                .replace("%theme%", getPlugin().getConfigPreferences().getThemes("Guess-The-Build_EASY")
                    .get(r.nextInt(getPlugin().getConfigPreferences().getThemes("Guess-The-Build_EASY").size()))))
                .lore(getPlugin().getChatManager().colorMessage("Menus.Guess-The-Build-Theme-Selector.Theme-Item-Lore")
                    .replace("%difficulty%", getPlugin().getChatManager().colorMessage("Menus.Guess-The-Build-Theme-Selector.Difficulties.Easy"))
                    .replace("%points%", String.valueOf(1)).split(";")).build());
            inv.setItem(13, new ItemBuilder(new ItemStack(Material.PAPER)).name(getPlugin().getChatManager().colorMessage("Menus.Guess-The-Build-Theme-Selector.Theme-Item-Name")
                .replace("%theme%", getPlugin().getConfigPreferences().getThemes("Guess-The-Build_MEDIUM")
                    .get(r.nextInt(getPlugin().getConfigPreferences().getThemes("Guess-The-Build_MEDIUM").size()))))
                .lore(getPlugin().getChatManager().colorMessage("Menus.Guess-The-Build-Theme-Selector.Theme-Item-Lore")
                    .replace("%difficulty%", getPlugin().getChatManager().colorMessage("Menus.Guess-The-Build-Theme-Selector.Difficulties.Medium"))
                    .replace("%points%", String.valueOf(2)).split(";")).build());
            inv.setItem(15, new ItemBuilder(new ItemStack(Material.PAPER)).name(getPlugin().getChatManager().colorMessage("Menus.Guess-The-Build-Theme-Selector.Theme-Item-Name")
                .replace("%theme%", getPlugin().getConfigPreferences().getThemes("Guess-The-Build_HARD")
                    .get(r.nextInt(getPlugin().getConfigPreferences().getThemes("Guess-The-Build_HARD").size()))))
                .lore(getPlugin().getChatManager().colorMessage("Menus.Guess-The-Build-Theme-Selector.Theme-Item-Lore")
                    .replace("%difficulty%", getPlugin().getChatManager().colorMessage("Menus.Guess-The-Build-Theme-Selector.Difficulties.Hard"))
                    .replace("%points%", String.valueOf(3)).split(";")).build());
            currentBuilder.openInventory(inv);
            break;
          } else {
            if (!isThemeSet() && getTimer() <= 0) {
              Random r = new Random();
              String type = "EASY";
              switch (r.nextInt(2 + 1)) {
                case 0:
                  break;
                case 1:
                  type = "MEDIUM";
                  break;
                case 2:
                  type = "HARD";
                  break;
              }
              GTBTheme theme = new GTBTheme(getPlugin().getConfigPreferences().getThemes("Guess-The-Build_" + type)
                  .get(r.nextInt(getPlugin().getConfigPreferences().getThemes("Guess-The-Build_" + type).size())), GTBTheme.Difficulty.valueOf(type));
              setCurrentTheme(theme);
              setThemeSet(true);
              currentBuilder.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(getPlugin().getChatManager().colorMessage("In-Game.Guess-The-Build.Theme-Is-Name")
                  .replace("%THEME%", theme.getTheme())));

              String roundMessage = getPlugin().getChatManager().colorMessage("In-Game.Guess-The-Build.Current-Round")
                  .replace("%ROUND%", String.valueOf(round))
                  .replace("%MAXPLAYERS%", String.valueOf(getPlayers().size()));
              for (Player p : getPlayers()) {
                p.sendTitle(getPlugin().getChatManager().colorMessage("In-Game.Guess-The-Build.Start-Guessing-Title"), null, 5, 25, 5);
                p.sendMessage(roundMessage);
              }
              setTimer(getTimer() - 1);
              break;
            }
          }
          if (getTimer() <= 90) {
            if (getTimer() == 90) {
              getPlugin().getChatManager().broadcast(this, getPlugin().getChatManager().colorMessage("In-Game.Guess-The-Build.Theme-Is-Long")
                  .replace("%NUM%", String.valueOf(getCurrentTheme().getTheme().length())));
            }
            //todo add action bar word display
          }
          if (getTimer() <= 0 && isThemeSet()) {
            getPlugin().getChatManager().broadcast(this, getPlugin().getChatManager().colorMessage("In-Game.Guess-The-Build.Theme-Was-Name").replace("%THEME%", getCurrentTheme().getTheme()));
            for (Player p : getPlayers()) {
              p.sendTitle(getPlugin().getChatManager().colorMessage("In-Game.Guess-The-Build.Theme-Was-Title"), getPlugin().getChatManager().colorMessage("In-Game.Guess-The-Build.Theme-Was-Subtitle")
                  .replace("%THEME%", getCurrentTheme().getTheme()), 5, 25, 5);
            }

            currentBuilder = null;
            setThemeSet(false);
            setCurrentTheme(null);
            whoGuessed.clear();
            round++;
            if (round > getPlayers().size()) {
              setTimer(5);
              setArenaState(ArenaState.ENDING);
              Bukkit.getPluginManager().callEvent(new BBGameEndEvent(this));
              break;
            }
            Plot plot = getPlotManager().getPlot(getPlayers().get(0));
            //todo weather and others can be applied in-game but wont affect viewers!
            for (Player p : getPlayers()) {
              p.teleport(plot.getTeleportLocation());
              p.setPlayerWeather(plot.getWeatherType());
              p.setPlayerTime(Plot.Time.format(plot.getTime(), p.getWorld().getTime()), false);
            }

            setTimer(5);
            //todo make variable for isNextRound not this thingy
            Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
              //probably not hardcoded
              setTimer(15);
              if (getArenaState() != ArenaState.IN_GAME || isThemeSet()) {
                return;
              }
              for (Player p : getPlayers()) {
                if (currentBuilder == p) {
                  continue;
                }
                p.sendTitle(null, getPlugin().getChatManager().colorMessage("In-Game.Guess-The-Build.Theme-Being-Selected"), 5, 25, 5);
              }
              for (Player p : getPlayers()) {
                p.sendMessage(getPlugin().getChatManager().colorMessage("In-Game.Guess-The-Build.Current-Round")
                    .replace("%ROUND%", String.valueOf(round))
                    .replace("%MAXPLAYERS%", String.valueOf(getPlayers().size())));
              }
            }, 20 * 5);
            //todo next round info and game state?
            break;
          }
          if (getPlayers().size() < 2) {
            getPlugin().getChatManager().broadcast(this, getPlugin().getChatManager().colorMessage("In-Game.Messages.Game-End-Messages.Only-You-Playing"));
            setArenaState(ArenaState.ENDING);
            Bukkit.getPluginManager().callEvent(new BBGameEndEvent(this));
            setTimer(10);
          }
          if ((getTimer() == (4 * 60) || getTimer() == (3 * 60) || getTimer() == 5 * 60 || getTimer() == 30 || getTimer() == 2 * 60 || getTimer() == 60 || getTimer() == 15)) {
            String message = getPlugin().getChatManager().colorMessage("In-Game.Messages.Time-Left-To-Build").replace("%FORMATTEDTIME%", MinigameUtils.formatIntoMMSS(getTimer()));
            String subtitle = getPlugin().getChatManager().colorMessage("In-Game.Messages.Time-Left-Subtitle").replace("%FORMATTEDTIME%", String.valueOf(getTimer()));
            for (Player p : getPlayers()) {
              p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
              p.sendMessage(getPlugin().getChatManager().getPrefix() + message);
              p.sendTitle(null, subtitle, 5, 30, 5);
            }
          }
          if (getTimer() != 0) {
            if (getOption(ArenaOption.IN_PLOT_CHECKER) == 1) {
              setOptionValue(ArenaOption.IN_PLOT_CHECKER, 0);
              for (Player player : getPlayers()) {
                User user = getPlugin().getUserManager().getUser(player);
                Plot buildPlot = user.getCurrentPlot();
                if (buildPlot != null && !buildPlot.getCuboid().isInWithMarge(player.getLocation(), 5)) {
                  player.teleport(buildPlot.getTeleportLocation());
                  player.sendMessage(getPlugin().getChatManager().getPrefix() + getPlugin().getChatManager().colorMessage("In-Game.Messages.Cant-Fly-Outside-Plot"));
                }
              }
            }
            addOptionValue(ArenaOption.IN_PLOT_CHECKER, 1);
          }
          setTimer(getTimer() - 1);
          break;
        case ENDING:
          if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
            getPlugin().getServer().setWhitelist(false);
          }
          for (Player player : getPlayers()) {
            MinigameUtils.spawnRandomFirework(player.getLocation());
            ArenaUtils.showPlayers(this);
          }
          if (getTimer() <= 0) {
            teleportAllToEndLocation();
            for (Player player : getPlayers()) {
              if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.BOSSBAR_ENABLED)) {
                getGameBar().removePlayer(player);
              }
              player.getInventory().clear();
              getPlugin().getUserManager().getUser(player).removeScoreboard();
              player.setGameMode(GameMode.SURVIVAL);
              player.setFlying(false);
              player.setAllowFlight(false);
              player.getInventory().setArmorContents(null);
              player.sendMessage(getPlugin().getChatManager().getPrefix() + getPlugin().getChatManager().colorMessage("Commands.Teleported-To-The-Lobby"));
              getPlugin().getUserManager().getUser(player).addStat(StatsStorage.StatisticType.GAMES_PLAYED, 1);
              if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.INVENTORY_MANAGER_ENABLED)) {
                InventoryUtils.loadInventory(getPlugin(), player);
              }
              getPlotManager().getPlot(player).fullyResetPlot();
            }
            giveRewards();
            clearPlayers();
            setArenaState(ArenaState.RESTARTING);
            if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
              for (Player player : getPlugin().getServer().getOnlinePlayers()) {
                this.addPlayer(player);
              }
            }
          }
          setTimer(getTimer() - 1);
          break;
        case RESTARTING:
          setOptionValue(ArenaOption.IN_PLOT_CHECKER, 0);
          whoGuessed.clear();
          setTimer(14);
          setArenaState(ArenaState.WAITING_FOR_PLAYERS);
          currentBuilder = null;
          setThemeSet(false);
          setCurrentTheme(null);
          break;
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      //halt
    }
  }

  @Override
  public void giveRewards() {
    //todo
  }

  @Override
  public void updateBossBar() {

  }

  public int getRound() {
    return round;
  }

  public Player getCurrentBuilder() {
    return currentBuilder;
  }

  public GTBTheme getCurrentTheme() {
    return currentTheme;
  }

  public void setCurrentTheme(GTBTheme currentTheme) {
    this.currentTheme = currentTheme;
  }

  public List<Player> getWhoGuessed() {
    return whoGuessed;
  }

  public void addWhoGuessed(Player player) {
    whoGuessed.add(player);

    //decrease game time by guessed theme
    if (getTimer() >= 15) {
      setTimer(getTimer() - 10);
    }
  }

  public boolean isThemeSet() {
    return themeSet;
  }

  public void setThemeSet(boolean themeSet) {
    this.themeSet = themeSet;
  }

  public Map<Player, Integer> getPlayersPoints() {
    return playersPoints;
  }
}
