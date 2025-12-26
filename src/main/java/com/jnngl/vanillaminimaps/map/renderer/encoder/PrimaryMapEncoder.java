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
import com.jnngl.vanillaminimaps.config.Config;
import com.jnngl.vanillaminimaps.map.MinimapScale;
import org.bukkit.Location;

public class PrimaryMapEncoder {

  public static void encodePrimaryLayer(Minimap minimap, byte[] data) {
    Location location = minimap.holder().getLocation();
    encodePrimaryLayer(minimap.screenPosition() == MinimapScreenPosition.RIGHT, location.getX(), location.getZ(), data);
  }

  public static void encodePrimaryLayer(boolean right, double playerX, double playerZ, byte[] data) {
    int scale = MinimapScale.get();
    double scaledX = playerX / scale;
    double scaledZ = playerZ / scale;
    double fractionalX = scaledX - Math.floor(scaledX);
    double fractionalZ = scaledZ - Math.floor(scaledZ);
    MapEncoderUtils.markCorners(data);
    MapEncoderUtils.encodeFixedPoint(data, 1, 0, fractionalX);
    MapEncoderUtils.encodeFixedPoint(data, 9, 0, fractionalZ);
    data[17] = right ? (byte) 4 : (byte) 0;
    data[18] = Config.instance().minimapShape == Config.MinimapShape.SQUARE ? (byte) 4 : (byte) 0;
    data[128] = (byte) 0;
  }
}
