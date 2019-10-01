package com.constantine.comm.server;

import com.constantine.comm.communication.MessageHandlerRegistry;
import com.constantine.comm.communication.clientoperations.IClientOperation;
import com.constantine.comm.server.client.ClientMessageHandler;
import com.constantine.comm.server.client.ServerClientReceiver;
import com.constantine.comm.server.client.ServerClientSender;
import com.constantine.comm.server.server.ServerMessageHandler;
import com.constantine.comm.server.server.ServerReceiver;
import com.constantine.comm.server.server.ServerSender;
import com.constantine.comm.communication.messages.*;
import com.constantine.comm.communication.serveroperations.IOperation;
import com.constantine.comm.communication.serveroperations.UnicastOperation;
import com.constantine.comm.utils.KeyUtilities;
import com.constantine.comm.utils.Log;
import com.constantine.comm.views.GlobalView;
import com.constantine.comm.views.utils.ViewLoader;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.constantine.comm.utils.Constants.CONFIG_LOCATION;

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
     * Cache which holds the receives messages (Consumed by Server)
     */
    public final ConcurrentLinkedQueue<IMessageWrapper> clientInputQueue = new ConcurrentLinkedQueue<>();

    /**
     * Cache which holds the messages to send in the future (Produced by Server).
     */
    public final ConcurrentLinkedQueue<IOperation> outputQueue = new ConcurrentLinkedQueue<>();

    /**
     * Cache which holds the messages to send in the future (Produced by Server).
     */
    public final ConcurrentLinkedQueue<IClientOperation> clientOutputQueue = new ConcurrentLinkedQueue<>();

    /**
     * The global view this server uses.
     */
    public final GlobalView view;

    /**
     * The current state of our servers. Client Public Key to Integer account balance.
     */
    public final HashMap<PublicKey, Integer> state = new HashMap<>();

    /**
     * Var setting the server to be active.
     */
    public AtomicBoolean isActive = new AtomicBoolean(true);

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
        this.view = ViewLoader.loadView(CONFIG_LOCATION,  "view.json");
        for (final ServerData data: view.getServers())
        {
            data.loadPublicKey(CONFIG_LOCATION);
        }
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

        final ServerClientReceiver clientReceiver = new ServerClientReceiver(this);
        clientReceiver.start();

        final ServerSender sender = new ServerSender(view, this);
        sender.start();

        final ServerClientSender clientSender = new ServerClientSender(this);
        clientSender.start();

        if (!isInView)
        {
            outputQueue.add(new UnicastOperation(new JoinRequestMessageWrapper(this, server), view.getCoordinator()));
        }

        final ServerMessageHandler serverMessageHandler = new ServerMessageHandler(this);
        serverMessageHandler.start();

        final ClientMessageHandler clientMessageHandler = new ClientMessageHandler(this);
        clientMessageHandler.start();

        while (isActive.get())
        {
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException e)
            {
                /*
                 * Nothing to do here.
                 */
            }
        }

        Log.getLogger().warn("Detected inactive!");
        receiver.disconnect();
        clientReceiver.disconnect();
    }

    @Override
    public void handleMessage(final IMessageWrapper message)
    {
        MessageHandlerRegistry.handle(message, this);
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
    public boolean hasMessageInClientOutputQueue()
    {
        return !clientOutputQueue.isEmpty();
    }

    @Override
    public IOperation consumeMessageFromOutputQueue()
    {
        return outputQueue.poll();
    }

    @Override
    public IClientOperation consumeMessageFromClientOutputQueue()
    {
        return clientOutputQueue.poll();
    }

    @Override
    public boolean isActive()
    {
        return isActive.get();
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