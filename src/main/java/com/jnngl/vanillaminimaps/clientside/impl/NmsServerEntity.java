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

package com.jnngl.vanillaminimaps.clientside.impl;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.function.Predicate;

final class NmsServerEntity {

  private static final ServerEntity.Synchronizer NOOP_SYNCHRONIZER = new ServerEntity.Synchronizer() {
    @Override
    public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) {
    }

    @Override
    public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) {
    }

    @Override
    public void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet,
                                              Predicate<ServerPlayer> filter) {
    }
  };

  private NmsServerEntity() {
  }

  static ServerEntity create(ServerLevel level, Entity entity) {
    return new ServerEntity(level, entity, 0, false, NOOP_SYNCHRONIZER, Set.of());
  }
}
