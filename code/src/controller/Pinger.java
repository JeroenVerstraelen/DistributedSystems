package controller;

import java.io.EOFException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.avro.AvroRemoteException;

import controller.clients.Client;

public class Pinger extends Thread {
	
	private boolean isRunning = true;
	private static Controller controller;
	
	public Pinger(Controller con) {
		Pinger.controller = con;
	}

	@Override
	public void run() {
		while (isRunning) {
			controller.time += 1;
			if (controller.time % 2 == 0) {
				for (int key : controller.getConnectedClients().keySet()) {
					try {
							controller.getConnectedClients().get(key).ping();
					} catch (AvroRemoteException | EOFException | UndeclaredThrowableException e) {
						// Client is no longer responding.
						System.out.println("Client (" + key + ") disconnected");
						controller.getConnectedClients().remove(key);
					}
				}
			}
			try { sleep(1000); } catch (InterruptedException e) {}
		}
	}
	
	public void setRunning(boolean state) {
		this.isRunning = state;
	}
	
}
