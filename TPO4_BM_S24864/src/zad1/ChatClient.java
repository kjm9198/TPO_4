/**
 *
 *  @author Bożek Michał S24864
 *
 */


package zad1;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ChatClient {
    private String id;
    private String host;
    private int port;
    private SocketChannel clientSocketChannel;
    //
    private Charset name = Charset.forName("UTF-8");
    private Lock lock = new ReentrantLock();
    private StringBuffer buffer;
    private Thread thread;
    private ByteBuffer buf = ByteBuffer.allocate(40_000);
    private final int CONNECTION_TIMEOUT_TRIES = 20;
    private final int CONNECTION_TIMEOUT_RETRY_FREQUENCY = 25;

    //
    public String getClientID() {
        return id;
    }

    public String getChatView() {
        return buffer.toString();
    }

    public ChatClient(String _host, int _port, String _clientID) {
        this.id = _clientID;
        host = _host;
        port = _port;
        buffer = new StringBuffer("=== " + _clientID + " chat view\n");

    }


    public void login() {
        try {
            openChannelHandler();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        send("login " + id);
        startReadThread();
    }

    private void openChannelHandler() throws Exception {
        clientSocketChannel = SocketChannel.open();
        clientSocketChannel.configureBlocking(false);
        clientSocketChannel.connect(new InetSocketAddress(host, port));
        int n = CONNECTION_TIMEOUT_TRIES;
        while (!clientSocketChannel.finishConnect()) {
            Thread.sleep(CONNECTION_TIMEOUT_RETRY_FREQUENCY);
            n--;
            if (n == 0) {
                addToView("*** error while connecting");
                throw new Exception("Connection timeout");
            }
        }
    }

    public void logout() {
        // Check if socket channel exists
        if (clientSocketChannel == null)
            return;
        //
        send("bye");
        abortConnection();
    }

    private void abortConnection() {
        lock.lock();
        try {
            thread.interrupt();
        } finally {
            lock.unlock();
        }
    }

    private void addToView(String s) {
        buffer.append(s);
    }

    private void startReadThread() {
        thread = new Thread(() -> {
            boolean reading = true;
            while (reading) {
                lock.lock();
                try {
                    //Check if channel is not closed
                    if (!clientSocketChannel.isOpen()) break;
                    //
                    //Clear buffer
                    buf.clear();
                    //

                    if (Thread.interrupted() && !clientSocketChannel.isOpen()) return;
                    int n = clientSocketChannel.read(buf);
                    //Check if connection is closed
                    if(n == -1)
                        break;

                    int i = CONNECTION_TIMEOUT_TRIES;
                    while (n == 0) {
                        Thread.sleep(CONNECTION_TIMEOUT_RETRY_FREQUENCY);
                        if (Thread.interrupted() && !clientSocketChannel.isOpen()) return;
                        n = clientSocketChannel.read(buf);
                        i--;
                        if (i < 0) {
                            addToView("*** no response. Connection timeout");
                            break;
                        }
                    }
                    buf.flip();
                    CharBuffer responseCharBuffer = name.decode(buf);
                    String response = responseCharBuffer.toString();
                    addToView(response);
                } catch (InterruptedException interruptedException) {
                    return;
                } catch (Exception e) {
                    closeConnectionHandler();
                    System.out.println(id + " exception " + e);
                    e.printStackTrace();
                    break;
                } finally {
                    lock.unlock();
                }
            }
        });
        thread.start();
    }
    private void closeConnectionHandler(){
        try {
            if (clientSocketChannel != null && clientSocketChannel.isOpen()) {
                clientSocketChannel.close();
                clientSocketChannel.socket().close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void send(String req) {
        ByteBuffer buf = name.encode(CharBuffer.wrap(req + "@"));
        try {
            clientSocketChannel.write(buf);
        } catch (Exception e) {
            addToView("*** send error: " + e);
            e.printStackTrace();
        }
    }

}
