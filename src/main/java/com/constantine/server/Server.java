package com.constantine.server;

import com.constantine.communication.ServerReceiver;
import com.constantine.communication.ServerSender;
import com.constantine.communication.messages.IMessageWrapper;
import com.constantine.communication.messages.JoinRequestMessageWrapper;
import com.constantine.communication.messages.RegisterMessageWrapper;
import com.constantine.communication.messages.TextMessageWrapper;
import com.constantine.communication.operations.BroadcastOperation;
import com.constantine.communication.operations.ConnectOperation;
import com.constantine.communication.operations.IOperation;
import com.constantine.communication.operations.UnicastOperation;
import com.constantine.utils.KeyUtilities;
import com.constantine.utils.Log;
import com.constantine.views.GlobalView;
import com.constantine.views.utils.ViewLoader;

import java.security.PrivateKey;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.constantine.utils.Constants.CONFIG_LOCATION;

/**
 * ServerReceiver representation in View.
 */
public class Server extends Thread implements IServer
{
    /**
     * The server data.
     */
    private final ServerData server;

    /**
     * The private key which belongs to this server replica.
     */
    private PrivateKey privateKey;

    /**
     * Cache which holds the receives messages (Consumed by Server)
     */
    public final ConcurrentLinkedQueue<IMessageWrapper> inputQueue = new ConcurrentLinkedQueue<>();

    /**
     * Cache which holds the messages to send in the future (Produced by Server).
     */
    public final ConcurrentLinkedQueue<IOperation> outputQueue = new ConcurrentLinkedQueue<>();

    /**
     * The global view this server uses.
     */
    private final GlobalView view;

    /**
     * Create a server object.
     * @param id the server id.
     * @param ip the server ip.
     * @param port the server port.
     */
    public Server(final int id, final String ip, final int port)
    {
        this.server = new ServerData(id, ip, port);
        KeyUtilities.generateOrLoadKey(server, CONFIG_LOCATION);
        this.privateKey = KeyUtilities.loadPrivateKeyFromFile(CONFIG_LOCATION, this.server);
        this.view = ViewLoader.loadView(CONFIG_LOCATION + "view.json");
    }

    @Override
    public void run()
    {
        Log.getLogger().warn("Starting Server Thread for Server: " + server.getId());
        boolean isInView = true;
        if (view.getServer(server.getId()) == null)
        {
            view.addServer(server);
            isInView = false;
        }

        // This is an extra thread to start this async.
        final ServerReceiver receiver = new ServerReceiver(this);
        receiver.start();

        final ServerSender sender = new ServerSender(view, this);
        sender.start();

        if (!isInView)
        {
            outputQueue.add(new UnicastOperation(new JoinRequestMessageWrapper(server, server.getId()), view.getCoordinator()));
        }

        //todo remove, this is only test code
        if (true)
        {
            int nextId = server.getId() + 1;
            if (nextId >= 4)
            {
                nextId = 0;
            }
            outputQueue.add(new UnicastOperation(new TextMessageWrapper("go", server.getId()), nextId));
        }

        int counter = 0;
        while (true)
        {

            //todo also remove
            if (++counter%40==0)
            {
                outputQueue.add(new BroadcastOperation(new TextMessageWrapper("Heartbeat", server.getId())));
            }

            if (inputQueue.isEmpty())
            {
                try
                {
                    Thread.sleep(100);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                continue;
            }
            handleMessage(inputQueue.poll());
        }
    }

    @Override
    public void handleMessage(final IMessageWrapper message)
    {
        if (message instanceof JoinRequestMessageWrapper)
        {
            outputQueue.add(new BroadcastOperation(new RegisterMessageWrapper((JoinRequestMessageWrapper) message)));
        }
        else if (message instanceof RegisterMessageWrapper)
        {
            view.addServer(((RegisterMessageWrapper) message).getServerData());
            outputQueue.add(new ConnectOperation(((RegisterMessageWrapper) message).getServerData()));
        }
        Log.getLogger().warn(server.getId() + ": Received!");
    }

    @Override
    public ServerData getServerData()
    {
        return server;
    }

    @Override
    public byte[] signMessage(final byte[] message)
    {
        return KeyUtilities.signMessage(message, this.privateKey);
    }

    @Override
    public boolean hasMessageInOutputQueue()
    {
        return !outputQueue.isEmpty();
    }

    @Override
    public IOperation consumeMessageFromOutputQueue()
    {
        return outputQueue.poll();
    }

    /**
     * Start an instance of a server
     * @param args the arguments of the server (id, ip, host)
     */
    public static void main(final String[] args)
    {
        if (args.length < 3)
        {
            Log.getLogger().warn("Invalid arguments, at least 3 necessary!");
            return;
        }

        final int id = Integer.parseInt(args[0]);
        final String ip = args[1];
        final int port = Integer.parseInt(args[2]);

        final Server server = new Server(id, ip, port);
        server.start();
    }
}
