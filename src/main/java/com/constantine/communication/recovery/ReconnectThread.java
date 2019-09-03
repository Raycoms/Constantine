package com.constantine.communication.recovery;

import com.constantine.communication.NettySenderHandler;
import com.constantine.communication.handlers.SignedSizedMessageEncoder;
import com.constantine.communication.handlers.SizedMessageDecoder;
import com.constantine.communication.handlers.SizedMessageEncoder;
import com.constantine.server.ServerData;
import com.constantine.utils.Log;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

/**
 * Thread to reconnect to the sender.
 */
public class ReconnectThread extends Thread
{
    /**
     * The sender handler.
     */
    private final NettySenderHandler handler;

    /**
     * The bootstrap instance to connect to.
     */
    private final Bootstrap b;

    /**
     * Constructor for the thread.
     * @param b the bootstrap instance.
     * @param handler the sender handler.
     */
    public ReconnectThread(final NettySenderHandler handler, final Bootstrap b)
    {
        this.handler = handler;
        this.b = b;
    }

    @Override
    public void run()
    {
        super.run();

        int attempts = 0;
        //todo we probably want this timeout value to be configurable
        while (!handler.isActive() && attempts < 100)
        {
            if (!handler.isReconnecting())
            {
                handler.setReconnecting(true);
                Log.getLogger().warn("Trying to reconnect");

                final ServerData data = handler.getServerData();
                b.connect(data.getIp(), data.getPort());
                attempts++;
            }

            try
            {
                //todo we would also want to decide on this.
                Thread.sleep(250);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            if (!handler.isActive())
            {
                handler.setReconnecting(false);
            }
        }
    }
}