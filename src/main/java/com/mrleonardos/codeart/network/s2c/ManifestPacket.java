package com.mrleonardos.codeart.network.s2c;

import java.util.ArrayList;
import java.util.List;

import com.mrleonardos.codeart.api.ArtDefinition;
import com.mrleonardos.codeart.common.ArtBridge;
import com.mrleonardos.codeart.common.ClientArtSink;
import com.mrleonardos.codeart.network.ArtPackets;
import com.mrleonardos.codecore.api.net.CodeBuffer;
import com.mrleonardos.codecore.api.net.MalformedPacketException;
import com.mrleonardos.codecore.api.net.Packet;
import com.mrleonardos.codecore.api.net.PacketContext;

/**
 * Порция манифеста артов.
 *
 * <p>
 * Первая порция говорит пересобрать реестр с нуля, последняя завершает пересборку. Число определений в
 * пакете ограничено и заводским размером порции, и проверкой при чтении: заявленный размер это число из
 * сети, и выделять память по нему без потолка нельзя.
 */
public final class ManifestPacket extends Packet {

    private boolean reset;
    private boolean last;
    private List<ArtDefinition> definitions;

    public ManifestPacket() {
        this.definitions = new ArrayList<>();
    }

    public ManifestPacket(boolean reset, boolean last, List<ArtDefinition> definitions) {
        this.reset = reset;
        this.last = last;
        this.definitions = definitions;
    }

    public boolean reset() {
        return reset;
    }

    public boolean last() {
        return last;
    }

    public List<ArtDefinition> definitions() {
        return definitions;
    }

    @Override
    public void write(CodeBuffer buffer) {
        buffer.writeBoolean(reset);
        buffer.writeBoolean(last);
        buffer.writeInt(definitions.size());
        for (ArtDefinition definition : definitions) {
            definition.write(buffer);
        }
    }

    @Override
    public void read(CodeBuffer buffer) {
        reset = buffer.readBoolean();
        last = buffer.readBoolean();
        int count = buffer.readInt();
        if (count < 0 || count > ArtPackets.MANIFEST_BATCH) {
            throw new MalformedPacketException("Manifest batch size out of bounds: " + count);
        }
        definitions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            definitions.add(ArtDefinition.read(buffer));
        }
    }

    @Override
    public void handle(PacketContext context) {
        ClientArtSink sink = ArtBridge.client();
        if (sink != null) {
            sink.manifest(reset, last, definitions);
        }
    }
}
