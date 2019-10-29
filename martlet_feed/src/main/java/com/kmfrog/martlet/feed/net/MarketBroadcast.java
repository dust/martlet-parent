package com.kmfrog.martlet.feed.net;

import static org.jvirtanen.util.Applications.fatal;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import com.paritytrading.nassau.moldudp64.MoldUDP64DefaultMessageStore;
import com.paritytrading.nassau.moldudp64.MoldUDP64DownstreamPacket;
import com.paritytrading.nassau.moldudp64.MoldUDP64RequestServer;
import com.paritytrading.nassau.moldudp64.MoldUDP64Server;

public class MarketBroadcast {

    private final PMBK.Version version;
    private final PMBK.OrderBook book;

    private final MoldUDP64Server transport;

    private final MoldUDP64RequestServer requestTransport;

    private final MoldUDP64DefaultMessageStore messages;

    private final MoldUDP64DownstreamPacket packet;

    private final ByteBuffer buffer;

    private MarketBroadcast(MoldUDP64Server transport, MoldUDP64RequestServer requestTransport) {
        this.transport = transport;
        this.requestTransport = requestTransport;

        messages = new MoldUDP64DefaultMessageStore();
        version = new PMBK.Version();
        book = new PMBK.OrderBook();

        this.packet = new MoldUDP64DownstreamPacket();
        this.buffer = ByteBuffer.allocateDirect(4 * 1024);

    }
    
    static MarketBroadcast open(String session, NetworkInterface multicastInterface,
            InetSocketAddress multicastGroup,
            InetSocketAddress requestAddress) throws IOException {
        DatagramChannel channel = DatagramChannel.open(StandardProtocolFamily.INET);

        channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, multicastInterface);
        channel.connect(multicastGroup);

        MoldUDP64Server transport = new MoldUDP64Server(channel, session);

        DatagramChannel requestChannel = DatagramChannel.open();

        requestChannel.bind(requestAddress);
        requestChannel.configureBlocking(false);

        MoldUDP64RequestServer requestTransport = new MoldUDP64RequestServer(requestChannel);

        return new MarketBroadcast(transport, requestTransport);
    }

    MoldUDP64Server getTransport() {
        return transport;
    }

    MoldUDP64RequestServer getRequestTransport() {
        return requestTransport;
    }

    void serve() {
        try {
            requestTransport.serve(messages);
        } catch (IOException e) {
            fatal(e);
        }
    }

    void version() {
        version.version = PMBK.VERSION;

        send(version);
    }
    
    void snapshotOrderBook(PMBK.OrderBook orderbook) {
        
        send(orderbook);
    }
    
//    private long timestamp() {
//        return (System.currentTimeMillis() - C.EPOCH_MILLIS) * 1_000_000;
//    }

    private void send(PMBK.Message message) {
        buffer.clear();
        message.put(buffer);
        buffer.flip();

        try {
            packet.put(buffer);

            transport.send(packet);

            packet.payload().flip();

            messages.put(packet);

            packet.clear();
        } catch (IOException e) {
            fatal(e);
        }
    }

}
