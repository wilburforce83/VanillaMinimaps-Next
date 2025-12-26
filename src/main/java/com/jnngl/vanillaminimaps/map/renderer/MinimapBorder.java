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

package com.jnngl.vanillaminimaps.map.renderer;

import com.jnngl.vanillaminimaps.config.Config;
import com.jnngl.vanillaminimaps.map.icon.MinimapIcon;

public final class MinimapBorder {

  private static final double MAP_HALF_SIZE = 64.0;
  // Keep these in sync with minimap/fragment_main.glsl.
  private static final double CIRCLE_BORDER_OUTER = 0.93;
  private static final double SQUARE_BORDER_OUTER = 0.97;

  private MinimapBorder() {
  }

  public static double outerRadius() {
    if (Config.instance().minimapShape == Config.MinimapShape.SQUARE) {
      return SQUARE_BORDER_OUTER * MAP_HALF_SIZE;
    }
    return Math.sqrt(CIRCLE_BORDER_OUTER) * MAP_HALF_SIZE;
  }

  public static double clampRadius(MinimapIcon icon) {
    int iconHalf = 0;
    if (icon != null) {
      iconHalf = Math.max(icon.width(), icon.height()) / 2;
    }
    double radius = outerRadius() - iconHalf;
    return Math.max(0.0, radius);
  }
}
