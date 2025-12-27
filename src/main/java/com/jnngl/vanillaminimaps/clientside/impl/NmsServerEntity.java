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
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class NmsServerEntity {

  private static final Consumer<Packet<?>> NOOP_BROADCAST = packet -> { };
  private static final BiConsumer<Packet<?>, List<UUID>> NOOP_BROADCAST_IGNORE = (packet, ignored) -> { };
  private static final Constructor<ServerEntity> SERVER_ENTITY_CONSTRUCTOR;
  private static final Object NOOP_SYNCHRONIZER;
  private static final boolean USE_BROADCAST_CONSTRUCTOR;
  private static final boolean USE_TRACKED_PLAYERS;

  static {
    Constructor<ServerEntity> constructor = null;
    Object synchronizer = null;
    boolean useBroadcast = false;
    boolean useTrackedPlayers = false;
    Class<?> synchronizerType = null;
    int score = -1;

    for (Constructor<?> candidate : ServerEntity.class.getConstructors()) {
      Class<?>[] params = candidate.getParameterTypes();
      if (params.length < 5
          || params[0] != ServerLevel.class
          || params[1] != Entity.class
          || params[2] != int.class
          || params[3] != boolean.class) {
        continue;
      }
      if (params.length == 7
          && Consumer.class.isAssignableFrom(params[4])
          && BiConsumer.class.isAssignableFrom(params[5])
          && Set.class.isAssignableFrom(params[6])) {
        constructor = (Constructor<ServerEntity>) candidate;
        useBroadcast = true;
        useTrackedPlayers = true;
        synchronizerType = null;
        score = 3;
        break;
      }
      if (params.length == 6
          && Consumer.class.isAssignableFrom(params[4])
          && BiConsumer.class.isAssignableFrom(params[5])
          && score < 2) {
        constructor = (Constructor<ServerEntity>) candidate;
        useBroadcast = true;
        useTrackedPlayers = false;
        synchronizerType = null;
        score = 2;
        continue;
      }
      if (params.length == 6
          && Set.class.isAssignableFrom(params[5])
          && score < 2) {
        constructor = (Constructor<ServerEntity>) candidate;
        useBroadcast = false;
        useTrackedPlayers = true;
        synchronizerType = params[4];
        score = 2;
        continue;
      }
      if (params.length == 5 && score < 1) {
        constructor = (Constructor<ServerEntity>) candidate;
        useBroadcast = false;
        useTrackedPlayers = false;
        synchronizerType = params[4];
        score = 1;
      }
    }

    if (constructor == null) {
      throw new ExceptionInInitializerError("Unable to resolve ServerEntity constructor");
    }

    if (!useBroadcast) {
      if (synchronizerType == null || !synchronizerType.isInterface()) {
        throw new ExceptionInInitializerError("Unable to resolve ServerEntity synchronizer type");
      }
      synchronizer = Proxy.newProxyInstance(
          synchronizerType.getClassLoader(),
          new Class<?>[] { synchronizerType },
          (proxy, method, args) -> null
      );
    }

    SERVER_ENTITY_CONSTRUCTOR = constructor;
    NOOP_SYNCHRONIZER = synchronizer;
    USE_BROADCAST_CONSTRUCTOR = useBroadcast;
    USE_TRACKED_PLAYERS = useTrackedPlayers;
  }

  private NmsServerEntity() {
  }

  static ServerEntity create(ServerLevel level, Entity entity) {
    try {
      if (USE_BROADCAST_CONSTRUCTOR) {
        if (USE_TRACKED_PLAYERS) {
          return SERVER_ENTITY_CONSTRUCTOR.newInstance(
              level, entity, 0, false, NOOP_BROADCAST, NOOP_BROADCAST_IGNORE, new HashSet<>());
        }
        return SERVER_ENTITY_CONSTRUCTOR.newInstance(
            level, entity, 0, false, NOOP_BROADCAST, NOOP_BROADCAST_IGNORE);
      }
      if (USE_TRACKED_PLAYERS) {
        return SERVER_ENTITY_CONSTRUCTOR.newInstance(level, entity, 0, false, NOOP_SYNCHRONIZER, new HashSet<>());
      }
      return SERVER_ENTITY_CONSTRUCTOR.newInstance(level, entity, 0, false, NOOP_SYNCHRONIZER);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Unable to create ServerEntity instance", e);
    }
  }
}
