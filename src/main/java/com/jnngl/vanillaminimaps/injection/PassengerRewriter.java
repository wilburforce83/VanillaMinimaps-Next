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

package com.jnngl.vanillaminimaps.injection;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;

import java.lang.reflect.Field;

public class PassengerRewriter extends ChannelOutboundHandlerAdapter {

  private final Int2ObjectMap<IntList> passengers = new Int2ObjectOpenHashMap<>();
  private static final Field PASSENGERS_FIELD;

  static {
    try {
      Field field = ClientboundSetPassengersPacket.class.getDeclaredField("passengers");
      field.setAccessible(true);
      PASSENGERS_FIELD = field;
    } catch (NoSuchFieldException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    if (msg instanceof ClientboundSetPassengersPacket packet) {
      int vehicle = packet.getVehicle();
      IntList passengers = this.passengers.get(vehicle);
      if (passengers != null) {
        synchronized (passengers) {
          int[] merged = mergePassengers(packet.getPassengers(), passengers);
          if (merged != packet.getPassengers()) {
            setPassengers(packet, merged);
          }
        }
      }
    }

    ctx.write(msg, promise);
  }

  public Int2ObjectMap<IntList> passengers() {
    return this.passengers;
  }

  public void addPassenger(int vehicle, int entity) {
    IntList list = passengers.computeIfAbsent(vehicle, k -> new IntArrayList());
    synchronized (list) {
      if (!list.contains(entity)) {
        list.add(entity);
      }
    }
  }

  public void removePassenger(int vehicle, int entity){
    IntList passengers = this.passengers.get(vehicle);
    if (passengers == null) {
      return;
    }

    synchronized (passengers) {
      passengers.rem(entity);
      if (passengers.isEmpty()) {
        this.passengers.remove(vehicle);
      }
    }
  }

  private static int[] mergePassengers(int[] existing, IntList extra) {
    if (extra.isEmpty()) {
      return existing;
    }
    IntArrayList merged = new IntArrayList(existing.length + extra.size());
    for (int passenger : existing) {
      if (!merged.contains(passenger)) {
        merged.add(passenger);
      }
    }
    for (int passenger : extra) {
      if (!merged.contains(passenger)) {
        merged.add(passenger);
      }
    }
    return merged.size() == existing.length ? existing : merged.toIntArray();
  }

  private static void setPassengers(ClientboundSetPassengersPacket packet, int[] passengers) {
    try {
      PASSENGERS_FIELD.set(packet, passengers);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Unable to update passengers packet", e);
    }
  }
}
