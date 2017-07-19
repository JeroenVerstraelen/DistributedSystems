package controller;

import java.io.EOFException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.avro.AvroRemoteException;

import controller.clients.Client;

public class Pinger extends Thread {
	
	private boolean isRunning = true;
	private ConcurrentHashMap<Integer, Client> connectedClients;
	
	public Pinger(ConcurrentHashMap<Integer, Client> connectedClients) {
		this.connectedClients = connectedClients;
	}

	@Override
	public void run() {
		while (isRunning) {
			for (int key : connectedClients.keySet()) {
				try {
					connectedClients.get(key).ping();
				} catch (AvroRemoteException | EOFException | UndeclaredThrowableException e) {
					// Client is no longer responding.
					connectedClients.remove(key);
				}
			}
			try { sleep(2000); } catch (InterruptedException e) {}
		}
	}
	
	public void setRunning(boolean state) {
		this.isRunning = state;
	}
	
}
