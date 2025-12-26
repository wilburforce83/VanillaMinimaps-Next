/*
 *  Copyright (C) 2024  JNNGL
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.jnngl.vanillaminimaps.command;

import com.jnngl.vanillaminimaps.VanillaMinimaps;
import com.jnngl.vanillaminimaps.clientside.SteerableLockedView;
import com.jnngl.vanillaminimaps.config.Config;
import com.jnngl.vanillaminimaps.map.Minimap;
import com.jnngl.vanillaminimaps.map.MinimapLayer;
import com.jnngl.vanillaminimaps.map.MinimapScreenPosition;
import com.jnngl.vanillaminimaps.map.SecondaryMinimapLayer;
import com.jnngl.vanillaminimaps.map.fullscreen.FullscreenMinimap;
import com.jnngl.vanillaminimaps.map.icon.MinimapIcon;
import com.jnngl.vanillaminimaps.map.marker.MarkerMinimapLayer;
import com.jnngl.vanillaminimaps.map.renderer.MinimapIconRenderer;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class MinimapBukkitCommand implements TabExecutor {

  private final VanillaMinimaps plugin;

  public MinimapBukkitCommand(VanillaMinimaps plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    List<String> tokens = parseArguments(sender, args);
    if (tokens == null) {
      return true;
    }

    if (tokens.isEmpty()) {
      sendUsage(sender);
      return true;
    }

    String sub = tokens.get(0).toLowerCase(Locale.ENGLISH);
    switch (sub) {
      case "enable" -> handleEnable(sender, tokens);
      case "disable" -> handleDisable(sender, tokens);
      case "position" -> handlePosition(sender, tokens);
      case "marker" -> handleMarker(sender, tokens);
      case "fullscreen" -> handleFullscreen(sender, tokens);
      default -> sendUsage(sender);
    }

    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 0) {
      return Collections.emptyList();
    }

    String first = args[0].toLowerCase(Locale.ENGLISH);
    if (args.length == 1) {
      return matchPrefix(List.of("enable", "disable", "position", "marker", "fullscreen"), first);
    }

    switch (first) {
      case "position" -> {
        if (args.length == 2) {
          return matchPrefix(List.of("left", "right"), args[1]);
        }
      }
      case "marker" -> {
        if (args.length == 2) {
          return matchPrefix(List.of("add", "set", "remove"), args[1]);
        }

        String action = args[1].toLowerCase(Locale.ENGLISH);
        if ("add".equals(action)) {
          if (args.length == 4) {
            return iconSuggestions(args[3]);
          }
        } else if ("set".equals(action)) {
          if (args.length == 3) {
            return markerSuggestions(sender, args[2]);
          }
          if (args.length == 4) {
            return matchPrefix(List.of("icon", "name"), args[3]);
          }
          if (args.length == 5 && "icon".equalsIgnoreCase(args[3])) {
            return iconSuggestions(args[4]);
          }
        } else if ("remove".equals(action)) {
          if (args.length == 3) {
            return markerSuggestions(sender, args[2]);
          }
        }
      }
      default -> {
      }
    }

    return Collections.emptyList();
  }

  private void handleEnable(CommandSender sender, List<String> tokens) {
    if (tokens.size() > 2) {
      sendUsage(sender, "/minimap enable [targets]");
      return;
    }

    Collection<Player> targets = resolveTargets(sender, tokens.size() == 2 ? tokens.get(1) : null);
    if (targets == null) {
      return;
    }

    for (Player player : targets) {
      try {
        plugin.playerDataStorage().enableMinimap(player);
        plugin.playerDataStorage().restore(plugin, player);
      } catch (Throwable e) {
        e.printStackTrace();
        sendFailure(sender, player.getName() + ": Unable to load player data, see console for error");
      }
    }
  }

  private void handleDisable(CommandSender sender, List<String> tokens) {
    if (tokens.size() > 2) {
      sendUsage(sender, "/minimap disable [targets]");
      return;
    }

    Collection<Player> targets = resolveTargets(sender, tokens.size() == 2 ? tokens.get(1) : null);
    if (targets == null) {
      return;
    }

    for (Player player : targets) {
      plugin.minimapListener().disableMinimap(player);
      try {
        plugin.playerDataStorage().disableMinimap(player);
      } catch (SQLException e) {
        e.printStackTrace();
        sendFailure(sender, player.getName() + ": Unable to save player data, see console for error");
      }
    }
  }

  private void handlePosition(CommandSender sender, List<String> tokens) {
    if (tokens.size() != 2) {
      sendUsage(sender, "/minimap position <left|right>");
      return;
    }

    if (!(sender instanceof Player player)) {
      sendFailure(sender, "This command can only be used by players");
      return;
    }

    MinimapScreenPosition position;
    try {
      position = MinimapScreenPosition.valueOf(tokens.get(1).toUpperCase(Locale.ENGLISH));
    } catch (IllegalArgumentException e) {
      sendFailure(sender, "Invalid position, use left or right");
      return;
    }

    Minimap minimap = plugin.getPlayerMinimap(player);
    if (minimap == null) {
      sendFailure(sender, "Minimap is disabled");
      return;
    }

    minimap.screenPosition(position);
    minimap.update(plugin);

    if (!save(minimap)) {
      sendFailure(sender, "Unable to save player data, see console for error");
    }
  }

  private void handleMarker(CommandSender sender, List<String> tokens) {
    if (tokens.size() < 2) {
      sendUsage(sender);
      return;
    }

    String action = tokens.get(1).toLowerCase(Locale.ENGLISH);
    switch (action) {
      case "add" -> handleMarkerAdd(sender, tokens);
      case "set" -> handleMarkerSet(sender, tokens);
      case "remove" -> handleMarkerRemove(sender, tokens);
      default -> sendUsage(sender);
    }
  }

  private void handleMarkerAdd(CommandSender sender, List<String> tokens) {
    if (tokens.size() < 4 || tokens.size() > 5) {
      sendUsage(sender, "/minimap marker add <name> <icon> [targets]");
      return;
    }

    String markerName = tokens.get(2);
    String iconName = tokens.get(3);
    String targetArg = tokens.size() == 5 ? tokens.get(4) : null;

    MinimapIcon icon = minimapIcon(sender, iconName);
    if (icon == null) {
      return;
    }

    Collection<Player> targets = resolveTargets(sender, targetArg);
    if (targets == null) {
      return;
    }

    for (Player player : targets) {
      Minimap minimap = plugin.getPlayerMinimap(player);
      if (minimap == null) {
        sendFailure(sender, player.getName() + ": Minimap is disabled");
        continue;
      }

      if ("player".equals(markerName) || "death_point".equals(markerName)) {
        sendFailure(sender, player.getName() + ": This marker name is unavailable");
        continue;
      }

      if (minimap.secondaryLayers().containsKey(markerName)) {
        sendFailure(sender, player.getName() + ": Marker with this name already exists");
        continue;
      }

      int markers = (int) minimap.secondaryLayers().entrySet().stream()
              .filter(entry -> !"player".equals(entry.getKey())
                      && !"death_point".equals(entry.getKey())
                      && entry.getValue() instanceof MarkerMinimapLayer)
              .count();

      if (markers >= Config.instance().markers.customMarkers.limit) {
        sendFailure(sender, player.getName() + ": You cannot place more than " +
                Config.instance().markers.customMarkers.limit + " markers.");
        continue;
      }

      float depth = 0.05F + minimap.secondaryLayers().size() * 0.01F;
      MinimapLayer iconBaseLayer = plugin.clientsideMinimapFactory().createMinimapLayer(player.getWorld(), null);
      SecondaryMinimapLayer iconLayer = new MarkerMinimapLayer(iconBaseLayer, new MinimapIconRenderer(icon), true,
              Config.instance().markers.customMarkers.stickToBorder, player.getWorld(), (int) player.getX(), (int) player.getZ(), depth);
      minimap.secondaryLayers().put(markerName, iconLayer);

      plugin.packetSender().spawnLayer(player, iconBaseLayer);
      minimap.update(plugin);

      if (!save(minimap)) {
        sendFailure(sender, player.getName() + ": Unable to save player data, see console for error");
      }
    }
  }

  private void handleMarkerSet(CommandSender sender, List<String> tokens) {
    if (tokens.size() < 5 || tokens.size() > 6) {
      sendUsage(sender, "/minimap marker set <name> icon <icon> [targets]");
      sendUsage(sender, "/minimap marker set <name> name <new_name> [targets]");
      return;
    }

    String markerName = tokens.get(2);
    String mode = tokens.get(3).toLowerCase(Locale.ENGLISH);
    String value = tokens.get(4);
    String targetArg = tokens.size() == 6 ? tokens.get(5) : null;

    Collection<Player> targets = resolveTargets(sender, targetArg);
    if (targets == null) {
      return;
    }

    switch (mode) {
      case "icon" -> {
        MinimapIcon icon = minimapIcon(sender, value);
        if (icon == null) {
          return;
        }
        for (Player player : targets) {
          if (modifyMarker(sender, player, markerName, (minimap, marker) -> {
            marker.setRenderer(new MinimapIconRenderer(icon));
          })) {
            // no-op
          }
        }
      }
      case "name" -> {
        for (Player player : targets) {
          if (modifyMarker(sender, player, markerName, (minimap, marker) -> {
            if (minimap.secondaryLayers().remove(markerName, marker)) {
              minimap.secondaryLayers().put(value, marker);
            }
          })) {
            // no-op
          }
        }
      }
      default -> sendUsage(sender, "/minimap marker set <name> icon <icon> [targets]");
    }
  }

  private void handleMarkerRemove(CommandSender sender, List<String> tokens) {
    if (tokens.size() < 3 || tokens.size() > 4) {
      sendUsage(sender, "/minimap marker remove <name> [targets]");
      return;
    }

    String markerName = tokens.get(2);
    String targetArg = tokens.size() == 4 ? tokens.get(3) : null;

    Collection<Player> targets = resolveTargets(sender, targetArg);
    if (targets == null) {
      return;
    }

    for (Player player : targets) {
      Minimap minimap = plugin.getPlayerMinimap(player);
      if (minimap == null) {
        sendFailure(sender, player.getName() + ": Minimap is disabled");
        continue;
      }

      if ("player".equals(markerName) || "death_point".equals(markerName)) {
        sendFailure(sender, player.getName() + ": This marker cannot be removed");
        continue;
      }

      SecondaryMinimapLayer marker = minimap.secondaryLayers().remove(markerName);
      if (marker == null) {
        sendFailure(sender, player.getName() + ": There is no such marker");
        continue;
      }

      plugin.packetSender().despawnLayer(minimap.holder(), marker.getBaseLayer());
      minimap.update(plugin);

      if (!save(minimap)) {
        sendFailure(sender, player.getName() + ": Unable to save player data, see console for error");
      }
    }
  }

  private void handleFullscreen(CommandSender sender, List<String> tokens) {
    if (tokens.size() > 2) {
      sendUsage(sender, "/minimap fullscreen [targets]");
      return;
    }

    Collection<Player> targets = resolveTargets(sender, tokens.size() == 2 ? tokens.get(1) : null);
    if (targets == null) {
      return;
    }

    for (Player player : targets) {
      Minimap minimap = plugin.getPlayerMinimap(player);
      if (minimap == null) {
        sendFailure(sender, player.getName() + ": Minimap is disabled");
        continue;
      }

      if (plugin.getFullscreenMinimap(player) != null) {
        plugin.minimapListener().closeFullscreen(player);
        continue;
      }

      FullscreenMinimap fullscreenMinimap = FullscreenMinimap.create(plugin, minimap);
      SteerableLockedView view = plugin.minimapListener().openFullscreen(fullscreenMinimap);

      if (view != null) {
        view.onSneak(v -> plugin.minimapListener().closeFullscreen(player));
      }
    }
  }

  private MinimapIcon minimapIcon(CommandSender sender, String iconName) {
    if (plugin.iconProvider().specialIconKeys().contains(iconName)) {
      sendFailure(sender, "Invalid icon.");
      return null;
    }

    MinimapIcon icon = plugin.iconProvider().getIcon(iconName);
    if (icon == null) {
      sendFailure(sender, "Invalid icon.");
      return null;
    }

    return icon;
  }

  private boolean modifyMarker(CommandSender sender,
                               Player player,
                               String markerName,
                               MarkerConsumer consumer) {
    Minimap minimap = plugin.getPlayerMinimap(player);
    if (minimap == null) {
      sendFailure(sender, player.getName() + ": Minimap is disabled");
      return false;
    }

    if ("player".equals(markerName) || "death_point".equals(markerName)) {
      sendFailure(sender, player.getName() + ": This marker cannot be modified");
      return false;
    }

    SecondaryMinimapLayer marker = minimap.secondaryLayers().get(markerName);
    if (marker == null) {
      sendFailure(sender, player.getName() + ": There is no such marker");
      return false;
    }

    consumer.accept(minimap, marker);
    minimap.update(plugin);

    if (!save(minimap)) {
      sendFailure(sender, player.getName() + ": Unable to save player data, see console for error");
      return false;
    }

    return true;
  }

  @FunctionalInterface
  private interface MarkerConsumer {
    void accept(Minimap minimap, SecondaryMinimapLayer marker);
  }

  private boolean save(Minimap minimap) {
    try {
      plugin.playerDataStorage().save(minimap);
      return true;
    } catch (Throwable e) {
      e.printStackTrace();
      return false;
    }
  }

  private List<String> parseArguments(CommandSender sender, String[] args) {
    if (args.length == 0) {
      return Collections.emptyList();
    }

    StringReader reader = new StringReader(String.join(" ", args));
    List<String> tokens = new ArrayList<>();

    try {
      while (reader.canRead()) {
        reader.skipWhitespace();
        if (!reader.canRead()) {
          break;
        }
        tokens.add(reader.readString());
      }
    } catch (CommandSyntaxException e) {
      sendFailure(sender, e.getMessage());
      return null;
    }

    return tokens;
  }

  private Collection<Player> resolveTargets(CommandSender sender, String targetArg) {
    if (targetArg == null) {
      if (sender instanceof Player player) {
        return List.of(player);
      }
      sendFailure(sender, "This command can only be used by players");
      return null;
    }

    if (!canTargetOthers(sender)) {
      sendFailure(sender, "You don't have permission to target other players");
      return null;
    }

    List<Entity> entities;
    try {
      entities = Bukkit.selectEntities(sender, targetArg);
    } catch (IllegalArgumentException e) {
      sendFailure(sender, e.getMessage());
      return null;
    }

    List<Player> players = entities.stream()
            .filter(entity -> entity instanceof Player)
            .map(entity -> (Player) entity)
            .collect(Collectors.toList());

    if (players.isEmpty()) {
      sendFailure(sender, "No matching players");
      return null;
    }

    return players;
  }

  private boolean canTargetOthers(CommandSender sender) {
    return sender instanceof ConsoleCommandSender || sender.isOp();
  }

  private void sendUsage(CommandSender sender) {
    sendUsage(sender, "/minimap <enable|disable|position|marker|fullscreen>");
  }

  private void sendUsage(CommandSender sender, String usage) {
    sender.sendMessage(ChatColor.RED + "Usage: " + usage);
  }

  private void sendFailure(CommandSender sender, String message) {
    sender.sendMessage(ChatColor.RED + message);
  }

  private List<String> matchPrefix(List<String> candidates, String prefix) {
    String lowered = prefix.toLowerCase(Locale.ENGLISH);
    return candidates.stream()
            .filter(candidate -> candidate.toLowerCase(Locale.ENGLISH).startsWith(lowered))
            .collect(Collectors.toList());
  }

  private List<String> iconSuggestions(String prefix) {
    Set<String> keys = plugin.iconProvider().genericIconKeys();
    String lowered = prefix.toLowerCase(Locale.ENGLISH);
    return keys.stream()
            .filter(key -> key.toLowerCase(Locale.ENGLISH).startsWith(lowered))
            .sorted()
            .collect(Collectors.toList());
  }

  private List<String> markerSuggestions(CommandSender sender, String prefix) {
    if (!(sender instanceof Player player)) {
      return Collections.emptyList();
    }

    Minimap minimap = plugin.getPlayerMinimap(player);
    if (minimap == null) {
      return Collections.emptyList();
    }

    String lowered = prefix.toLowerCase(Locale.ENGLISH);
    return minimap.secondaryLayers().keySet().stream()
            .filter(name -> !"player".equals(name) && !"death_point".equals(name))
            .filter(name -> name.toLowerCase(Locale.ENGLISH).startsWith(lowered))
            .sorted()
            .collect(Collectors.toList());
  }
}
