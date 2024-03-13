/**
 *
 *  @author Bożek Michał S24864
 *
 */


package zad1;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.locks.ReentrantLock;
import java.nio.channels.ServerSocketChannel;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.nio.charset.Charset;
import java.util.concurrent.locks.Lock;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ChatServer implements Runnable {

    private Selector sel = null;
    private ServerSocketChannel channel = null;
    private ExecutorService service;
    private Lock lock = new ReentrantLock();
    private Future string;
    private Charset charset = Charset.forName("UTF-8");
    private StringBuffer serverLog = new StringBuffer();
    private Map<SocketChannel, String> channelsMap = new LinkedHashMap<>();

    public ChatServer(String host, int port) {
        service = Executors.newSingleThreadExecutor();
        try {
            channel = ServerSocketChannel.open();
            channel.configureBlocking(false);
            channel.socket().bind(new InetSocketAddress(host, port));
            sel = Selector.open();
            channel.register(sel, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void stopServer() {
        try {
            if (!channelsMap.isEmpty())
                logOutHandler();

            if (service != null)
                shutdownServerHandler();

            if (sel != null)

            if (channel != null)
                closeServerHandler();

        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.print("Server stopped\n");
    }

    public void startServer() {
        string = service.submit(this);
        System.out.print("Server started\n\n");
    }

    private void logOutHandler() throws IOException {
        respondHandler("Server stopped. You are logged out.");
        for (SocketChannel sc : channelsMap.keySet()) {
            if (sc.isOpen()) {
                sc.socket().close();
            }
        }
    }

    private void shutdownServerHandler() {
        lock.lock();
        try {
            service.shutdownNow();
        } finally {
            lock.unlock();
        }
    }

    private void closeServerHandler() throws IOException {
        channel.close();
        channel.socket().close();
    }


    private SocketChannel dummySocketChannel = null;

    @Override
    public void run() {
        while (true) {
            try {
                // Check if selector is closed
                if (!sel.isOpen())
                    break;

                // Look for channels
                sel.select();

                // Check if server is stopping
                if (Thread.interrupted()) break;

                Set readyChannels = sel.selectedKeys();

                Iterator readyChannelsIterator = readyChannels.iterator();
                while (readyChannelsIterator.hasNext()) {
                    // Get channel from iterator and remove it
                    SelectionKey selectedChannel = (SelectionKey) readyChannelsIterator.next();
                    readyChannelsIterator.remove();
                    //
                    // Check if received channel acceptable
                    if (selectedChannel.isAcceptable()) {
                        acceptableChannelHandler();
                        continue;
                    }
                    // Check if received channel is readable
                    if (selectedChannel.isReadable())
                        readableChannelHandler(selectedChannel);

                }
            } catch (Exception e) {
                try {
                    if (dummySocketChannel != null) {
                        closeServerHandler();
                    }
                } catch (IOException eIO) {
                    eIO.printStackTrace();
                }
                e.printStackTrace();
            }
        }
    }

    private void acceptableChannelHandler() throws IOException {
        dummySocketChannel = channel.accept();
        dummySocketChannel.configureBlocking(false);
        dummySocketChannel.register(sel, SelectionKey.OP_READ);
    }

    private void readableChannelHandler(SelectionKey currentChannel) {
        dummySocketChannel = (SocketChannel) currentChannel.channel();
        try {
            lock.lock();
            serviceRequest(dummySocketChannel);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    private void serviceRequest(SocketChannel socketChannelParam) throws IOException {
        // Check if server is running
        if (!channel.isOpen()) return;
        if (socketChannelParam.socket().isClosed()) return;
        //

        ByteBuffer buf = ByteBuffer.allocate(4000);
        int n = socketChannelParam.read(buf);
        // Check if buffer is empty
        if (n == 0 || n == -1) return;
        // Reset buffer
        buf.flip();
        //
        CharBuffer charBuffer = charset.decode(buf);
        String fullRequest = charBuffer.toString();
        String[] splittedRequest = fullRequest.split("@");
        for (String singleRequest : splittedRequest) {
            String startsWith = singleRequest.split("\\s+")[0];
            switch (startsWith) {
                case "login":
                    loginHandler(singleRequest, socketChannelParam);
                    break;
                case "bye":
                    quitHandler(socketChannelParam);
                    break;
                default:
                    sendMessageHandler(singleRequest, socketChannelParam);
            }

        }
    }

    private void loginHandler(String request, SocketChannel socketChannelParam) throws IOException {

        String dataToSend;
        String[] split = request.split("\\s+");
        channelsMap.put(socketChannelParam, split[1]);
        dataToSend = split[1] + " logged in\n";
        respondHandler(dataToSend);
    }

    private void quitHandler(SocketChannel socketChannelParam) throws IOException {
        String fullResponse;

        fullResponse = channelsMap.get(socketChannelParam) + " logged out\n";
        respondHandler(fullResponse);
        if (socketChannelParam.isOpen()) {
            socketChannelParam.close();
            socketChannelParam.socket().close();
        }
        channelsMap.remove(socketChannelParam);
    }

    private void sendMessageHandler(String message, SocketChannel socketChannelParam) throws IOException {
        String dataToSend;
        dataToSend = channelsMap.get(socketChannelParam) + ": " + message + "\n";
        respondHandler(dataToSend);
    }

    private void respondHandler(String resp) throws IOException {
        serverLog.append(LocalTime.now() + " " + resp);
        ByteBuffer buf = charset.encode(CharBuffer.wrap(resp));
        for (SocketChannel c : channelsMap.keySet()) {
            if (c.isOpen() && !c.socket().isClosed()) {
                c.write(buf);
                buf.rewind();
            }
        }
    }

    public String getServerLog() {
        return serverLog.toString();
    }
}
