/*
 * Created on Oct 27, 2004
 */
package no.ntnu.fp.net.co;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;


import no.ntnu.fp.net.admin.Log;
import no.ntnu.fp.net.cl.ClException;
import no.ntnu.fp.net.cl.ClSocket;
import no.ntnu.fp.net.cl.KtnDatagram;
import no.ntnu.fp.net.cl.KtnDatagram.Flag;

/**
 * Implementation of the Connection-interface. <br>
 * <br>
 * This class implements the behaviour in the methods specified in the interface
 * {@link Connection} over the unreliable, connectionless network realised in
 * {@link ClSocket}. The base class, {@link AbstractConnection} implements some
 * of the functionality, leaving message passing and error handling to this
 * implementation.
 * 
 * @author Sebj¯rn Birkeland and Stein Jakob Nordb¯
 * @see no.ntnu.fp.net.co.Connection
 * @see no.ntnu.fp.net.cl.ClSocket
 */
public class ConnectionImpl extends AbstractConnection {

    /** Keeps track of the used ports for each server port. */
    private static Map<Integer, Boolean> usedPorts = Collections.synchronizedMap(new HashMap<Integer, Boolean>());

    /**
     * Initialise initial sequence number and setup state machine.
     * 
     * @param myPort
     *            - the local port to associate with this connection
     */
    public ConnectionImpl(int myPort) {
    	super();
    	this.myAddress = getIPv4Address();
    	this.myPort = myPort;
    	usedPorts.put(myPort,true);
    	
        
    }

    private String getIPv4Address() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        }
        catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }

    /**
     * Establish a connection to a remote location.
     * 
     * @param remoteAddress
     *            - the remote IP-address to connect to
     * @param remotePort
     *            - the remote portnumber to connect to
     * @throws IOException
     *             If there's an I/O error.
     * @throws java.net.SocketTimeoutException
     *             If timeout expires before connection is completed.
     * @see Connection#connect(InetAddress, int)
     */
    public void connect(InetAddress remoteAddress, int remotePort) throws IOException,
    SocketTimeoutException {
    	this.remoteAddress = remoteAddress.getHostAddress();
    	this.remotePort = remotePort;

    	while(state != State.ESTABLISHED){
    		try {
    			simplySendPacket((constructInternalPacket(Flag.SYN)));
    			state = State.SYN_SENT;
    		} catch (ClException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		KtnDatagram datagram = receiveAck();
    		if (datagram == null){
    			continue;
    		}
    		else if (datagram.getFlag() == Flag.SYN_ACK){
    			state = State.SYN_RCVD;
    			remotePort = datagram.getSrc_port();
    			sendAck(datagram, false);
    			state = State.ESTABLISHED;
    			break;
    		}
    		else {
    			state = State.CLOSED;
    			continue;
    		}
    	}
    	
    }


    /**
     * Listen for, and accept, incoming connections.
     * 
     * @return A new ConnectionImpl-object representing the new connection.
     * @see Connection#accept()
     */
    public Connection accept() throws IOException, SocketTimeoutException {
    	int portNumber = (int)Math.random()*60000 + 1024;
    	while (usedPorts.containsKey(portNumber)){
    		portNumber = (int)Math.random()*60000 + 1024;
    	}
    	usedPorts.put(portNumber, true);
    	ConnectionImpl conn = new ConnectionImpl(portNumber);
    	while (state != State.ESTABLISHED) {
    		state = State.LISTEN;
    		KtnDatagram received = null;
    		while (!isValid(received)){
    			received = receivePacket(true);
    		}
    		this.remoteAddress = received.getSrc_addr();
    		this.remotePort = received.getSrc_port();
    		
    		conn.remoteAddress = received.getSrc_addr();
    		conn.remotePort = received.getSrc_port();

    		for (int i = 0; i<2; i++){
    			try {
    				conn.simplySendPacket(constructInternalPacket(Flag.SYN_ACK));
    			} catch (ClException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
    			KtnDatagram datagram= conn.receiveAck();
    			if (datagram == null){
    				continue;
    			}        	
    			else{
    				System.out.println("Connection Established ##########################################");
    				conn.state = State.ESTABLISHED;
    				return conn;
    			}
    		}
    	}
    	usedPorts.remove(portNumber);
    	System.out.println("Returning NO connection ############################################");
    	return null;

    }

    /**
     * Send a message from the application.
     * 
     * @param msg
     *            - the String to be sent.
     * @throws ConnectException
     *             If no connection exists.
     * @throws IOException
     *             If no ACK was received.
     * @see AbstractConnection#sendDataPacketWithRetransmit(KtnDatagram)
     * @see no.ntnu.fp.net.co.Connection#send(String)
     */
    public void send(String msg) throws ConnectException, IOException {
    	KtnDatagram datagram = constructDataPacket(msg);
    	boolean sendDone = false;
    	while (!sendDone){
    		KtnDatagram answer = sendDataPacketWithRetransmit(datagram);
    		if(answer == null){
    			continue;
    		}
    		else if (answer.getFlag() == Flag.ACK){
    			sendDone = true;
    			return;
    		}
    		else{
    			System.out.println("Yeah, we are fucked");
    		}
    	}

    }

    /**
     * Wait for incoming data.
     * 
     * @return The received data's payload as a String.
     * @see Connection#receive()
     * @see AbstractConnection#receivePacket(boolean)
     * @see AbstractConnection#sendAck(KtnDatagram, boolean)
     */
    public String receive() throws ConnectException, IOException {
    	System.out.println("RECEIVING MOTHERFUCKERS #####################################");
    	KtnDatagram datagram = null;
    	try{
    		System.out.println("ReceivePacket(flase)");
    		datagram = receivePacket(false);
    	}
    	catch (EOFException e){
    		disconnect();
    	}
    	String result = (String) datagram.getPayload();
    	System.out.println("Sender ACK for pakkenr: " + datagram.getSeq_nr() + "####################################");
    	sendAck(datagram, false);
    	return result;
    }

    private void disconnect() {
    	System.out.println("Disconnect attempted ###################################################");
		state = State.CLOSE_WAIT;
    	try {
			simplySendPacket(constructInternalPacket(Flag.ACK));
			simplySendPacket(constructInternalPacket(Flag.FIN));
		} catch (ClException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			KtnDatagram response = receiveAck();
			if (response.getFlag() == Flag.ACK){
				state = State.CLOSED;
				return;
			}
			else if (response.getFlag() == Flag.FIN){
				disconnect();
			}
		} catch (EOFException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
     * Close the connection.
     * 
     * @see Connection#close()
     */
    public void close() throws IOException {
    	KtnDatagram datagram = constructInternalPacket(Flag.FIN);
    	state = State.FIN_WAIT_1;
    	while (state != State.CLOSED){
    		try {
    			simplySendPacket(datagram);
    		} catch (ClException e) {
    			e.printStackTrace();
    			continue;
    		}
    		KtnDatagram response = receiveAck();
    		if (response.getFlag() == Flag.ACK){
    			state = State.FIN_WAIT_2;
    			KtnDatagram Ack_for_FIN = receiveAck();
    			if (Ack_for_FIN.getFlag() == Flag.FIN){
    				KtnDatagram FinalAck = constructInternalPacket(Flag.ACK);
    				sendDataPacketWithRetransmit(FinalAck);
    				state = State.CLOSED;
    				return;
    			}
    			else{
    				throw new IOException();
    			}
    		}
    		else{

    		}
    	}
    }

    /**
     * Test a packet for transmission errors. This function should only called
     * with data or ACK packets in the ESTABLISHED state.
     * 
     * @param packet
     *            Packet to test.
     * @return true if packet is free of errors, false otherwise.
     */
    protected boolean isValid(KtnDatagram packet) {
        if(packet != null){
        	return true;
        }
        return false;
    }
}
