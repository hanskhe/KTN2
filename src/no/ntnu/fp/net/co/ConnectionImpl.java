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
    			this.remotePort = datagram.getSrc_port();
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
    	int portNumber = (int)(Math.random()*60000 + 1024);
    	while (usedPorts.containsKey(portNumber)){
    		portNumber = (int)(Math.random()*60000 + 1024);
    	}
    	usedPorts.put(portNumber, true);
    	System.out.println("Client should now be asked to use port: " + portNumber);
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
    				conn.simplySendPacket(conn.constructInternalPacket(Flag.SYN_ACK));
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
    		datagram = receivePacket(false);
    		System.out.println("ReceivePacket(flase)");
    	}
    	catch (EOFException e){
    		System.out.println("EOFExeption ######################33333333########");
    		disconnect();
    	}
    	String result = (String) datagram.getPayload();
    	System.out.println("PackPayload :" + result);
    	System.out.println("Sender ACK for pakkenr: " + datagram.getSeq_nr() + "####################################");
    	sendAck(datagram, false);
    	return result;
    }

    private void disconnect() {
    	System.out.println("Disconnect attempted ###################################################");
		state = State.CLOSE_WAIT;
    	try {
			simplySendPacket(createFINPack(2));
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			simplySendPacket(createFINPack(3));
		} catch (ClException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
    	try {
    		while (state != State.CLOSED){
    			KtnDatagram response = receiveAck();
    			if (response == null){
    				continue;
    			}
    			else if (response.getFlag() == Flag.ACK && response.getSeq_nr() == 4){
    				state = State.CLOSED;
    				return;
    			}
    			else if (response.getFlag() == Flag.FIN && response.getSeq_nr() == 1){
    				disconnect();
    			}
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
    	KtnDatagram datagram = createFINPack(1);
    	state = State.FIN_WAIT_1;
    	while (state != State.CLOSED){
    		try {
    			simplySendPacket(datagram);
    		} catch (ClException e) {
    			e.printStackTrace();
    			continue;
    		}
    		KtnDatagram response = receiveAck();
    		if (response == null){
    			//
    		}
    		else if (response.getFlag() == Flag.ACK && response.getSeq_nr() == 2){
    			state = State.FIN_WAIT_2;
    			while (state != State.CLOSED){
    				KtnDatagram Ack_for_FIN = receiveAck();
    				if (Ack_for_FIN.getFlag() == Flag.FIN && Ack_for_FIN.getSeq_nr() == 3){
    					KtnDatagram FinalAck = createFINPack(4);
    					try {
    						simplySendPacket(FinalAck);
    					} catch (ClException e) {
    						// TODO Auto-generated catch block
    						e.printStackTrace();
    					}
    					
    					state = State.CLOSED;
    					return;
    				}
    				else{
    					continue;
    				}
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
        	return (packet.getChecksum() == packet.calculateChecksum());
        }
        return false;
    }
    
    private KtnDatagram createFINPack(int FIN_Number){
    	KtnDatagram returnDatagram = null;
    	if (FIN_Number <= 4 && FIN_Number % 2 == 1){
    		if (FIN_Number == 1){    			
    			returnDatagram = constructInternalPacket(Flag.FIN);
    			returnDatagram.setSeq_nr(1);
    		}
    		else if (FIN_Number == 3){
    			returnDatagram = constructInternalPacket(Flag.FIN);
    			returnDatagram.setSeq_nr(3);
    		}
    		nextSequenceNo--;
    	}
    	else if (FIN_Number <= 4 && FIN_Number % 2 == 0){

    		if (FIN_Number == 2){    		
    			returnDatagram = constructInternalPacket(Flag.ACK);
    			returnDatagram.setSeq_nr(2);
    		}
    		else if (FIN_Number == 4){
    			returnDatagram = constructInternalPacket(Flag.ACK);
    			returnDatagram.setSeq_nr(4);
    		}
    		nextSequenceNo--;
    	}
    	return returnDatagram;
    		
    }
}
