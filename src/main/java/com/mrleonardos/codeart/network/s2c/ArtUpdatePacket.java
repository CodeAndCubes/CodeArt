package com.mrleonardos.codeart.network.s2c;

import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.common.ArtBridge;
import com.mrleonardos.codeart.common.ClientArtSink;
import com.mrleonardos.codecore.api.net.CodeBuffer;
import com.mrleonardos.codecore.api.net.Codec;
import com.mrleonardos.codecore.api.net.Packet;
import com.mrleonardos.codecore.api.net.PacketContext;

/**
 * Изменение реестра на лету: арт появился или снят.
 *
 * <p>
 * Отдельный пакет от манифеста, потому что пересылать весь реестр из-за одной картинки дороже, чем
 * показать её.
 */
public final class ArtUpdatePacket extends Packet {

    private boolean added;
    private String name;
    private ArtDefinition definition;

    public ArtUpdatePacket() {}

    public static ArtUpdatePacket added(ArtDefinition definition) {
        ArtUpdatePacket packet = new ArtUpdatePacket();
        packet.added = true;
        packet.definition = definition;
        packet.name = definition.name();
        return packet;
    }

    public static ArtUpdatePacket removed(String name) {
        ArtUpdatePacket packet = new ArtUpdatePacket();
        packet.added = false;
        packet.name = name;
        return packet;
    }

    public boolean added() {
        return added;
    }

    public String name() {
        return name;
    }

    public ArtDefinition definition() {
        return definition;
    }

    @Override
    public void write(CodeBuffer buffer) {
        buffer.writeBoolean(added);
        if (added) {
            definition.write(buffer);
        } else {
            Codec.writeString(buffer, name);
        }
    }

    @Override
    public void read(CodeBuffer buffer) {
        added = buffer.readBoolean();
        if (added) {
            definition = ArtDefinition.read(buffer);
            name = definition.name();
        } else {
            name = Codec.readString(buffer);
        }
    }

    @Override
    public void handle(PacketContext context) {
        ClientArtSink sink = ArtBridge.client();
        if (sink == null) {
            return;
        }
        if (added) {
            sink.artAdded(definition);
        } else {
            sink.artRemoved(name);
        }
    }
}
