/**
 *
 *  @author Bożek Michał S24864
 *
 */

package zad1;


import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class ChatClientTask extends FutureTask<String> {

    public ChatClient getClient() {
        return client;
    }

    private ChatClient client;
    public ChatClientTask(ChatClient _client,Callable<String> _code){
        super(_code);
        client = _client;
    }
    public static ChatClientTask create(ChatClient c, List<String> msgs, int wait) {
        Callable<String> code = ()->{
            String clientID = c.getClientID();
            c.login();
            Thread.sleep(wait);
            try{
                for(String message : msgs){
                    if(Thread.interrupted()) return clientID + " task interrupted";
                    c.send(message);
                    Thread.sleep(wait);
                }
                c.logout();
                Thread.sleep(wait);
            } catch (InterruptedException exc){
                return clientID + " tesk interrupted";
            }
            return clientID + " task completed";

        };
        return new ChatClientTask(c,code);
    }
}