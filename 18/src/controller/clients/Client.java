package controller.clients;


import java.io.EOFException;
import java.lang.reflect.UndeclaredThrowableException;

import network.Connection;

import org.apache.avro.AvroRemoteException;

import proto.ClientProto;


public class Client {
	
	protected ClientProto proxy = null;
	protected String IPAddress;
	protected int portNumber;
	protected Connection connection = null;
	
	public Client() {}
	
	public Client(CharSequence IPAddress, int portNumber) {
		this.IPAddress = IPAddress.toString();
		this.portNumber = portNumber;
		this.connection = new Connection(this.IPAddress, 6798, this.portNumber);
	}
	
	public String getIPAddress() {
		return this.IPAddress;
	}
	
	public int getPortNumber() {
		return this.portNumber;
	}
	
	public boolean isConnected() {
		return this.connection.getClient().isConnected();
	}
	
	public void disconnect() {
		this.connection.disconnect();
	}
	
	public void setNewController(CharSequence cIPAddress, int cPortNumber) throws AvroRemoteException {
		if (proxy != null)
			proxy.setNewController(cIPAddress, cPortNumber);
	}
		
	public void ping() throws AvroRemoteException, EOFException, UndeclaredThrowableException {
		proxy.ping();
	}
}
