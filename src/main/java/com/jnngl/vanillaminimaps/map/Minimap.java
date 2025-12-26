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

package com.jnngl.vanillaminimaps.map;

import com.jnngl.vanillaminimaps.clientside.MinimapPacketSender;
import com.jnngl.vanillaminimaps.config.Config;
import com.jnngl.vanillaminimaps.map.icon.MinimapIcon;
import com.jnngl.vanillaminimaps.map.marker.MarkerMinimapLayer;
import com.jnngl.vanillaminimaps.map.renderer.MinimapIconRenderer;
import com.jnngl.vanillaminimaps.map.renderer.MinimapLayerRenderer;
import com.jnngl.vanillaminimaps.map.renderer.encoder.PrimaryMapEncoder;
import com.jnngl.vanillaminimaps.map.renderer.encoder.SecondaryMapEncoder;
import com.jnngl.vanillaminimaps.map.renderer.world.cache.CacheableWorldMinimapRenderer;
import com.jnngl.vanillaminimaps.map.renderer.world.cache.WorldMapCache;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;

@ToString
@EqualsAndHashCode
public final class Minimap {

  private final Player holder;
  private final MinimapLayer primaryLayer;
  private final LinkedHashMap<String, SecondaryMinimapLayer> secondaryLayers;
  private MinimapScreenPosition screenPosition;

  public Minimap(Player holder, MinimapScreenPosition screenPosition, MinimapLayer primaryLayer,
                 LinkedHashMap<String, SecondaryMinimapLayer> secondaryLayers) {
    this.holder = holder;
    this.screenPosition = screenPosition;
    this.primaryLayer = primaryLayer;
    this.secondaryLayers = secondaryLayers;
  }

  public void update(MinimapProvider provider, double playerX, double playerZ, boolean updateViewerKeys) {
    byte[] layer = new byte[128 * 128];
    MinimapLayerRenderer primaryRenderer = primaryLayer.renderer();
    if (primaryRenderer instanceof CacheableWorldMinimapRenderer cacheableRenderer) {
      int scale = MinimapScale.get();
      if (scale == 1) {
        int blockX = (int) Math.floor(playerX);
        int blockZ = (int) Math.floor(playerZ);
        int alignedTrackX = (blockX >> 7) << 7;
        int alignedTrackZ = (blockZ >> 7) << 7;
        Location location = holder.getLocation();
        int alignedX = (location.getBlockX() >> 7) << 7;
        int alignedZ = (location.getBlockZ() >> 7) << 7;
        int offsetX = location.getBlockX() % 128;
        int offsetZ = location.getBlockZ() % 128;
        if (offsetX < 0) {
          offsetX += 128;
        }
        if (offsetZ < 0) {
          offsetZ += 128;
        }
        byte[] data = cacheableRenderer.getWorldMapCache().get(holder.getWorld(), alignedX, alignedZ);
        byte[] dataRight = cacheableRenderer.getWorldMapCache().get(holder.getWorld(), alignedX + 128, alignedZ);
        byte[] dataUpRight = cacheableRenderer.getWorldMapCache().get(holder.getWorld(), alignedX + 128, alignedZ + 128);
        byte[] dataUp = cacheableRenderer.getWorldMapCache().get(holder.getWorld(), alignedX, alignedZ + 128);
        LongList usedChunks = LongList.of(
            WorldMapCache.getKey(holder.getWorld(), alignedTrackX, alignedTrackZ),
            WorldMapCache.getKey(holder.getWorld(), alignedTrackX + 128, alignedTrackZ),
            WorldMapCache.getKey(holder.getWorld(), alignedTrackX + 128, alignedTrackZ + 128),
            WorldMapCache.getKey(holder.getWorld(), alignedTrackX, alignedTrackZ + 128)
        );
        for (int z = 0; z < 128; z++) {
          for (int x = 0; x < 128; x++) {
            int dataX = x - offsetX;
            int dataZ = z - offsetZ;
            byte[] buffer = data;

            if (dataX < 0 && dataZ < 0) {
              dataX += 128;
              dataZ += 128;
              buffer = dataUpRight;
            } else if (dataX < 0) {
              dataX += 128;
              buffer = dataRight;
            } else if (dataZ < 0) {
              dataZ += 128;
              buffer = dataUp;
            }

            if (dataX > 0 && dataZ > 0) {
              layer[(127 - dataZ) * 128 + (127 - dataX)] = buffer[(127 - z) * 128 + (127 - x)];
            }
          }
        }

        if (updateViewerKeys) {
          cacheableRenderer.getWorldMapCache().setViewerChunks(holder.getUniqueId(), usedChunks);
        }
      } else {
        int centerX = ((int) Math.floor(playerX / scale)) * scale;
        int centerZ = ((int) Math.floor(playerZ / scale)) * scale;
        int startX = centerX - 64 * scale;
        int startZ = centerZ - 64 * scale;
        int endX = startX + 128 * scale - 1;
        int endZ = startZ + 128 * scale - 1;
        WorldMapCache<?> cache = cacheableRenderer.getWorldMapCache();
        World world = holder.getWorld();

        for (int z = 0; z < 128; z++) {
          int worldZ = startZ + z * scale;
          int alignedZ = ((worldZ + 64) >> 7) << 7;
          int dataZ = worldZ - (alignedZ - 64);
          for (int x = 0; x < 128; x++) {
            int worldX = startX + x * scale;
            int alignedX = ((worldX + 64) >> 7) << 7;
            int dataX = worldX - (alignedX - 64);
            byte[] buffer = cache.get(world, alignedX, alignedZ);
            int inputIndex = (127 - dataZ) * 128 + (127 - dataX);
            int outputIndex = (127 - z) * 128 + (127 - x);
            layer[outputIndex] = buffer[inputIndex];
          }
        }

        boolean updateKeys = updateViewerKeys || scale != 1;
        if (updateKeys) {
          LongArrayList usedChunks = new LongArrayList();
          int alignedStartX = ((startX + 64) >> 7) << 7;
          int alignedStartZ = ((startZ + 64) >> 7) << 7;
          int alignedEndX = ((endX + 64) >> 7) << 7;
          int alignedEndZ = ((endZ + 64) >> 7) << 7;
          for (int x = alignedStartX; x <= alignedEndX; x += 128) {
            for (int z = alignedStartZ; z <= alignedEndZ; z += 128) {
              usedChunks.add(WorldMapCache.getKey(world, x, z));
            }
          }
          cacheableRenderer.getWorldMapCache().setViewerChunks(holder.getUniqueId(), usedChunks);
        }
      }
    } else {
      primaryRenderer.render(this, primaryLayer, layer);
    }
    PrimaryMapEncoder.encodePrimaryLayer(screenPosition == MinimapScreenPosition.RIGHT, playerX, playerZ, layer);
    provider.packetSender().updateLayer(holder, primaryLayer, 0, 0, 128, 128, layer);

    updateSecondaryLayers(provider);
  }

  public void updateSecondaryLayers(MinimapProvider provider) {
    for (SecondaryMinimapLayer secondary : secondaryLayers.values()) {
      if (secondary.getWorld() != null && !secondary.getWorld().equals(holder.getWorld())) {
        continue;
      }

      byte[] secondaryLayer = new byte[128 * 128];
      if (secondary.getRenderer() != null) {
        secondary.getRenderer().render(this, secondary, secondaryLayer);
      } else if (secondary.getBaseLayer().renderer() != null) {
        secondary.getBaseLayer().renderer().render(this, secondary.getBaseLayer(), secondaryLayer);
      }
      SecondaryMapEncoder.encodeSecondaryLayer(this, secondary, secondaryLayer);
      provider.packetSender().updateLayer(holder, secondary.getBaseLayer(), 0, 0, 128, 128, secondaryLayer);
    }
  }

  public void update(MinimapProvider provider) {
    update(provider, holder.getX(), holder.getZ(), false);
  }

  public void respawn(MinimapProvider provider) {
    MinimapPacketSender packetSender = provider.packetSender();
    packetSender.despawnMinimap(this);
    packetSender.spawnMinimap(this);
    update(provider, holder.getX(), holder.getZ(), true);
  }

  public void resetDeathPoint(MinimapProvider provider) {
    SecondaryMinimapLayer currentDeathPoint = secondaryLayers.remove("death_point");
    if (currentDeathPoint != null) {
      provider.packetSender().despawnLayer(holder, currentDeathPoint.getBaseLayer());
    }
  }

  public void setDeathPoint(MinimapProvider provider, Location deathLocation) {
    SecondaryMinimapLayer currentDeathPoint = secondaryLayers.get("death_point");
    if (currentDeathPoint != null) {
      provider.packetSender().despawnLayer(holder, currentDeathPoint.getBaseLayer());
    }

    MinimapIcon deathIcon = provider.iconProvider().getIcon("death");
    if (deathIcon != null) {
      MinimapLayer iconBaseLayer = provider.clientsideMinimapFactory().createMinimapLayer(holder.getWorld(), null);
      SecondaryMinimapLayer iconLayer = new MarkerMinimapLayer(iconBaseLayer, new MinimapIconRenderer(deathIcon), true,
          Config.instance().markers.deathMarker.stickToBorder, deathLocation.getWorld(), deathLocation.getBlockX(),
          deathLocation.getBlockZ(), 0.05F);
      secondaryLayers.put("death_point", iconLayer);

      if (iconLayer.getWorld().equals(holder.getWorld())) {
        provider.packetSender().spawnLayer(holder, iconBaseLayer);
      }
    }
  }

  public Location getDeathPoint() {
    SecondaryMinimapLayer currentDeathPoint = secondaryLayers.get("death_point");
    return currentDeathPoint != null ? new Location(currentDeathPoint.getWorld(), currentDeathPoint.getPositionX(), 0, currentDeathPoint.getPositionZ()) : null;
  }

  public Player holder() {
    return holder;
  }

  public MinimapScreenPosition screenPosition() {
    return screenPosition;
  }

  public void screenPosition(MinimapScreenPosition position) {
    this.screenPosition = position;
  }

  public MinimapLayer primaryLayer() {
    return primaryLayer;
  }

  public LinkedHashMap<String, SecondaryMinimapLayer> secondaryLayers() {
    return secondaryLayers;
  }
}
