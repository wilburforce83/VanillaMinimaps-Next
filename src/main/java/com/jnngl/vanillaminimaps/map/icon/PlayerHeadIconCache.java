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

package com.jnngl.vanillaminimaps.map.icon;

import com.jnngl.vanillaminimaps.VanillaMinimaps;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class PlayerHeadIconCache {

  private static final int HEAD_SOURCE_SIZE = 8;
  private static final int HEAD_X = 8;
  private static final int HEAD_Y = 8;
  private static final int HAT_X = 40;
  private static final int HAT_Y = 8;
  private static final int ICON_SIZE = 8;

  private final VanillaMinimaps plugin;
  private final Executor asyncExecutor;
  private final Map<UUID, CachedIcon> cache = new ConcurrentHashMap<>();
  private final Map<UUID, CompletableFuture<@Nullable MinimapIcon>> inflight = new ConcurrentHashMap<>();

  public PlayerHeadIconCache(VanillaMinimaps plugin) {
    this.plugin = plugin;
    this.asyncExecutor = command -> Bukkit.getScheduler().runTaskAsynchronously(plugin, command);
  }

  public @Nullable MinimapIcon get(UUID uuid) {
    CachedIcon cached = cache.get(uuid);
    return cached != null ? cached.icon() : null;
  }

  public CompletableFuture<@Nullable MinimapIcon> refresh(Player player) {
    UUID uuid = player.getUniqueId();
    CompletableFuture<@Nullable MinimapIcon> existing = inflight.get(uuid);
    if (existing != null) {
      return existing;
    }

    CompletableFuture<@Nullable MinimapIcon> future = player.getPlayerProfile()
        .update()
        .thenApplyAsync(profile -> loadIcon(uuid, profile), asyncExecutor)
        .whenComplete((icon, error) -> inflight.remove(uuid));

    inflight.put(uuid, future);
    return future;
  }

  public void remove(UUID uuid) {
    inflight.remove(uuid);
    cache.remove(uuid);
  }

  private @Nullable MinimapIcon loadIcon(UUID uuid, PlayerProfile profile) {
    PlayerTextures textures = profile.getTextures();
    URL skinUrl = textures != null ? textures.getSkin() : null;
    if (skinUrl == null) {
      return null;
    }

    String skinUrlValue = skinUrl.toString();
    CachedIcon cached = cache.get(uuid);
    if (cached != null && skinUrlValue.equals(cached.skinUrl())) {
      return cached.icon();
    }

    try {
      BufferedImage skin = ImageIO.read(skinUrl);
      if (skin == null || skin.getWidth() < HEAD_X + HEAD_SOURCE_SIZE || skin.getHeight() < HEAD_Y + HEAD_SOURCE_SIZE) {
        return null;
      }

      BufferedImage head = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
      Graphics2D graphics = head.createGraphics();
      graphics.drawImage(skin.getSubimage(HEAD_X, HEAD_Y, HEAD_SOURCE_SIZE, HEAD_SOURCE_SIZE),
          0, 0, ICON_SIZE, ICON_SIZE, null);
      if (skin.getWidth() >= HAT_X + HEAD_SOURCE_SIZE && skin.getHeight() >= HAT_Y + HEAD_SOURCE_SIZE) {
        graphics.drawImage(skin.getSubimage(HAT_X, HAT_Y, HEAD_SOURCE_SIZE, HEAD_SOURCE_SIZE),
            0, 0, ICON_SIZE, ICON_SIZE, null);
      }
      graphics.dispose();

      MinimapIcon icon = MinimapIcon.fromBufferedImage("player_head_" + uuid, head);
      cache.put(uuid, new CachedIcon(icon, skinUrlValue));
      return icon;
    } catch (IOException e) {
      plugin.getLogger().warning("Failed to load skin for " + uuid + ": " + e.getMessage());
      return null;
    }
  }

  private record CachedIcon(MinimapIcon icon, String skinUrl) {}
}
