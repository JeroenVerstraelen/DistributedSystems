package network;

import java.io.*;
import java.net.*;

import network.avro.SaslSocketTransceiver;

import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;

import proto.ControllerProto;

public class Connection {
	/* 
	 * Establishes a connection between the Client and the Controller in two possible ways.
	 * Client to Controller connections have a connection ID.
	 * Controller to Client connections have no ID.
	 */
	
	private String clientIPAddress = "";
	private String serverIPAddress = "";
	private boolean local = false;
	private int clientPortNumber;
	private int serverPortNumber;
	private Transceiver client = null;
	private Integer id = -1;
	
	private int maxConnectionRetries = 20;
	
	public Connection(String serverIPAddress, int clientPortNumber, int serverPortNumber) {
		this.clientPortNumber = clientPortNumber;
		this.serverPortNumber = serverPortNumber;
		String localAddress = "";
		try { 
			localAddress = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) { e.printStackTrace(); } 
		if (serverIPAddress.equals(""))
			serverIPAddress = localAddress;
		if (serverIPAddress == localAddress)
			local = true;
		this.serverIPAddress = serverIPAddress;
	}
	
	public <T> T connect(Class<T> protocol, String clientType) throws IOException {
		int retries = 0;
		while (retries < maxConnectionRetries) {
			try {
				// Connect to the controller.
				InetSocketAddress serverSocketAddress = new InetSocketAddress(InetAddress.getByName(serverIPAddress), this.serverPortNumber);
				client = new SaslSocketTransceiver(serverSocketAddress);
				
				T proxy = (T) SpecificRequestor.getClient(protocol, client);
				if (protocol == ControllerProto.class) {
					clientIPAddress = InetAddress.getLocalHost().getHostAddress();
					if (!local && clientIPAddress.equals(""))
						clientIPAddress = NetworkUtils.askIPAddress();
					this.id = ((ControllerProto) proxy).register(clientIPAddress, this.clientPortNumber, clientType);
					System.out.println("Client Connection ID: " + this.id);
				}
				return proxy;
			} catch (IOException e) {
				// Could not connect to the server.
				retries++;
			}
		}
		throw new IOException();
	}
	
	public void disconnect() {
		try {
			if (client != null)
					client.close();
		} catch (IOException e) {
			System.err.println("Error closing connection ...");
			e.printStackTrace();
		}
	}
	
	public int getId() {
		return this.id;
	}
	
	public Transceiver getClient() {
		return this.client;
	}
	
	public int getClientPortNumber() {
		return this.clientPortNumber;
	}
	
	public String getClientIPAddress() {
		return clientIPAddress;
	}
	
	public void setId(int id) {
		this.id = id;
	}

	public void setClientIPAddress(String clientIPAddress) {
		this.clientIPAddress = clientIPAddress;
	}
}