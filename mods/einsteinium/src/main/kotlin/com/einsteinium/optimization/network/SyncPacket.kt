package com.einsteinium.optimization.network

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.Packet
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

class SyncPacket(
    val snapshots: List<EntitySyncSnapshot>,
    val packetId: Long = System.currentTimeMillis()
) {
    companion object {
        const val MAX_ENTITIES_PER_PACKET = 16
        const val MINIMAL_SYNC_INTERVAL_TICKS = 2
    }

    fun size(): Int = snapshots.size

    fun isEmpty(): Boolean = snapshots.isEmpty()

    fun write(buf: PacketByteBuf) {
        buf.writeVarLong(packetId)
        buf.writeVarInt(snapshots.size)
        
        for (snapshot in snapshots) {
            buf.writeVarInt(snapshot.entityId)
            
            val maskByte = buildMaskByte(snapshot.mask)
            buf.writeByte(maskByte)
            
            buf.writeVarInt(snapshot.syncTier.ordinal)
            buf.writeVarLong(snapshot.version)
            
            if (snapshot.mask.hasPosition) {
                buf.writeDouble(snapshot.pos.x)
                buf.writeDouble(snapshot.pos.y)
                buf.writeDouble(snapshot.pos.z)
            }
            
            if (snapshot.mask.hasRotation) {
                buf.writeFloat(snapshot.pitch)
                buf.writeFloat(snapshot.yaw)
            }
            
            if (snapshot.mask.hasVelocity) {
                buf.writeDouble(snapshot.velocity.x)
                buf.writeDouble(snapshot.velocity.y)
                buf.writeDouble(snapshot.velocity.z)
            }
            
            if (snapshot.mask.hasOnGround) {
                buf.writeBoolean(snapshot.onGround)
            }
            
            if (snapshot.mask.hasDataTracker) {
                buf.writeVarInt(snapshot.dirtyEntries.size)
                for (entry in snapshot.dirtyEntries) {
                    buf.writeVarInt(entry.id)
                    entry.serializer.write(buf, entry.value)
                }
            }
        }
    }

    private fun buildMaskByte(mask: SyncMask): Byte {
        var result = 0
        if (mask.hasPosition) result = result or 0x01
        if (mask.hasRotation) result = result or 0x02
        if (mask.hasVelocity) result = result or 0x04
        if (mask.hasOnGround) result = result or 0x08
        if (mask.hasDataTracker) result = result or 0x10
        if (mask.hasMetadata) result = result or 0x20
        return result.toByte()
    }

    fun read(buf: PacketByteBuf): SyncPacket {
        val packetId = buf.readVarLong()
        val count = buf.readVarInt()
        val snapshots = mutableListOf<EntitySyncSnapshot>()
        
        for (i in 0 until count) {
            val entityId = buf.readVarInt()
            val maskByte = buf.readByte().toInt()
            val syncTier = SyncTier.values()[buf.readVarInt()]
            val version = buf.readVarLong()
            
            val mask = parseMaskByte(maskByte)
            
            var posX = 0.0
            var posY = 0.0
            var posZ = 0.0
            if (mask.hasPosition) {
                posX = buf.readDouble()
                posY = buf.readDouble()
                posZ = buf.readDouble()
            }
            
            var pitch = 0f
            var yaw = 0f
            if (mask.hasRotation) {
                pitch = buf.readFloat()
                yaw = buf.readFloat()
            }
            
            var velX = 0.0
            var velY = 0.0
            var velZ = 0.0
            if (mask.hasVelocity) {
                velX = buf.readDouble()
                velY = buf.readDouble()
                velZ = buf.readDouble()
            }
            
            var onGround = false
            if (mask.hasOnGround) {
                onGround = buf.readBoolean()
            }
            
            val dirtyEntries = mutableListOf<net.minecraft.entity.data.DataTracker.Entry<*>>()
            if (mask.hasDataTracker) {
                val entryCount = buf.readVarInt()
                for (j in 0 until entryCount) {
                    val id = buf.readVarInt()
                    val entry = net.minecraft.entity.data.DataTracker.Entry(id)
                    entry.read(buf)
                    dirtyEntries.add(entry)
                }
            }
            
            snapshots.add(EntitySyncSnapshot(
                entityId,
                net.minecraft.util.math.Vec3d(posX, posY, posZ),
                pitch,
                yaw,
                net.minecraft.util.math.Vec3d(velX, velY, velZ),
                onGround,
                dirtyEntries,
                mask,
                syncTier,
                version
            ))
        }
        
        return SyncPacket(snapshots, packetId)
    }

    private fun parseMaskByte(maskByte: Int): SyncMask {
        val mask = SyncMask()
        if (maskByte and 0x01 != 0) mask.hasPosition = true
        if (maskByte and 0x02 != 0) mask.hasRotation = true
        if (maskByte and 0x04 != 0) mask.hasVelocity = true
        if (maskByte and 0x08 != 0) mask.hasOnGround = true
        if (maskByte and 0x10 != 0) mask.hasDataTracker = true
        if (maskByte and 0x20 != 0) mask.hasMetadata = true
        return mask
    }
}