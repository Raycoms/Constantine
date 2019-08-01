package com.constantine.communication.operations;

import com.constantine.communication.ISender;
import com.constantine.server.ServerData;

/**
 * The Connect operation to connect to a new server.
 */
public class DisconnectOperation implements IOperation
{
    /**
     * The server to connect to.
     */
    private final ServerData server;

    /**
     * Create an instance of the connect operation.
     * @param server the server to connect to.
     */
    public DisconnectOperation(final ServerData server)
    {
        this.server = server;
    }

    @Override
    public void executeOP(final ISender sender)
    {
        sender.disconnectFromServer(this.server);
    }
}