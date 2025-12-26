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

package com.jnngl.vanillaminimaps.map.renderer.encoder;

import com.jnngl.vanillaminimaps.map.Minimap;
import com.jnngl.vanillaminimaps.map.MinimapScreenPosition;
import com.jnngl.vanillaminimaps.map.MinimapScale;
import com.jnngl.vanillaminimaps.map.SecondaryMinimapLayer;
import com.jnngl.vanillaminimaps.map.icon.MinimapIcon;
import com.jnngl.vanillaminimaps.map.renderer.MinimapBorder;
import com.jnngl.vanillaminimaps.map.renderer.MinimapIconRenderer;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public class SecondaryMapEncoder {

  public static void encodeSecondaryLayer(Minimap minimap, SecondaryMinimapLayer layer, byte[] data) {
    Location location = minimap.holder().getLocation();

    double trackedX = layer.getPositionX();
    double trackedZ = layer.getPositionZ();
    int scale = MinimapScale.get();
    if (layer.isTrackLocation()) {
      int centerBlockX = ((int) Math.floor(location.getX() / scale)) * scale;
      int centerBlockZ = ((int) Math.floor(location.getZ() / scale)) * scale;
      trackedZ = (centerBlockX - layer.getPositionX()) / (double) scale; // (Not a typo)
      trackedX = (centerBlockZ - layer.getPositionZ()) / (double) scale;
      if (layer.isKeepOnEdge()) {
        Vector direction = new Vector(trackedX, 0, trackedZ);
        double edgeRadius = MinimapBorder.clampRadius(resolveIcon(layer));
        if (direction.lengthSquared() > edgeRadius * edgeRadius) {
          direction.normalize();
          direction.multiply(edgeRadius);
          trackedX = direction.getX();
          trackedZ = direction.getZ();
        }
      }
      trackedX += 64;
      trackedZ += 64;
    }

    PrimaryMapEncoder.encodePrimaryLayer(minimap.screenPosition() == MinimapScreenPosition.RIGHT, location.getX(), location.getZ(), data);
    Location position = new Location(location.getWorld(), layer.getPositionX(), location.getY(), layer.getPositionZ());
    double maxDistance = 64.0 * scale;
    int mapX = (int) Math.round(trackedX);
    int mapZ = (int) Math.round(trackedZ);
    boolean tracked = !layer.isTrackLocation() || layer.isKeepOnEdge() || location.distanceSquared(position) < maxDistance * maxDistance;
    if (tracked && mapX >= 0 && mapX < 128 && mapZ >= 0 && mapZ < 128) {
      MapEncoderUtils.encodeFixedPoint(data, 1, 1, layer.getDepth());
      MapEncoderUtils.encodeFixedPoint(data, 9, 1, mapX / 128.0);
      data[128 * 2] = (byte) 4;
      MapEncoderUtils.encodeFixedPoint(data, 1, 2, mapZ / 128.0);
      data[128 * 2 + 9] = layer.isKeepOnEdge() ? (byte) 4 : (byte) 0;
    } else {
      data[128 * 2] = (byte) 0;
    }

    data[128] = (byte) 4;
  }

  private static MinimapIcon resolveIcon(SecondaryMinimapLayer layer) {
    if (layer.getRenderer() instanceof MinimapIconRenderer iconRenderer) {
      return iconRenderer.icon();
    }
    return null;
  }
}
