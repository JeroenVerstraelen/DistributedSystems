package controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import network.NetworkUtils;
import network.avro.SaslSocketServer;
import network.avro.SaslSocketTransceiver;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.ipc.specific.SpecificResponder;

import proto.BasicClientRecord;
import proto.ControllerProto;
import proto.FullClientRecord;
import utils.UnClosableDecorator;
import controller.clients.Client;
import controller.clients.Fridge;
import controller.clients.Light;
import controller.clients.TemperatureSensor;
import controller.clients.User;

public class Controller implements ControllerProto {
	private int currentId = -1;
	private ConcurrentHashMap<Integer, Client> connectedClients = new ConcurrentHashMap<Integer, Client>();
	private static List<FullClientRecord> backupClients = new ArrayList<FullClientRecord>();
	private static Thread cliThread = null;
	private static Entry<String,Integer> prevControllerDetails = new AbstractMap.SimpleEntry<>("None", 0);

	public static void main (String[] args){
		int portNumber = 6789;
		if (args.length >= 2) {
			// Connect with the new controller.
			String serverIPAddress = args[0];
			int serverPortNumber = 0;
			try { 
				serverPortNumber = Integer.parseInt (args[1]); 
			} catch (NumberFormatException e) { 
				System.out.println("Invalid port number.");
				System.exit(0);
			}
			try {
				InetSocketAddress serverSocketAddress = new InetSocketAddress(InetAddress.getByName(serverIPAddress), serverPortNumber);
				Transceiver client = new SaslSocketTransceiver(serverSocketAddress);
				ControllerProto proxy = (ControllerProto) SpecificRequestor.getClient(ControllerProto.class, client);
				backupClients = new ArrayList<FullClientRecord>(proxy.requestBackup());
				String IPAdress = NetworkUtils.askIPAddress();
				portNumber = NetworkUtils.getValidPortNumber(portNumber);
				proxy.reestablishController(IPAdress, portNumber);
				client.close();
			} catch (IOException e) {
				System.out.println("Could not connect to controller.");
				System.exit(0);
			}
		}
		// Make sure the input stream cannot be closed.
		System.setIn(new UnClosableDecorator(System.in));
		
		Controller controller = new Controller();
		controller.run(portNumber);
	}
	
	public Entry<String,Integer> run(int startingPortNumber) {
		prevControllerDetails = new AbstractMap.SimpleEntry<>("None", 0);
		int portNumber = NetworkUtils.getValidPortNumber(startingPortNumber);
		Server server = null;
		try {
			server = new SaslSocketServer(new SpecificResponder(ControllerProto.class, 
					 				      						new Controller()), 
					 				      new InetSocketAddress(portNumber));
		} catch (IOException e){
			System.err.println("Could not start controller server, shutting down.");
			System.exit(0);
		}
		server.start();
		System.out.println("Controller> Running controller on port: " + portNumber);
		
		// Pinger is used to check if clients have stopped responding.
		Pinger pinger = new Pinger(connectedClients);
		pinger.start();
		
		// Start handling user input.
		cliThread = new Thread(new Runnable()
	    {
	      @Override
	      public void run() { cli(); }
	    });
		cliThread.start();
		
		while(cliThread.isAlive()) 
			try { Thread.sleep(1000); } catch (InterruptedException e) {}
		
		// Stop pinging.
    	pinger.setRunning(false);
    	try { pinger.join(); server.close(); server.join();} catch (InterruptedException e) {}
		// Disconnect all client connections before shutting down.
		for (int key : connectedClients.keySet())
			connectedClients.get(key).disconnect();
		backupClients = new ArrayList<FullClientRecord>();
		
		return prevControllerDetails;
	}
	
	private static void cli() {
        System.out.print("Controller> ");
	    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));     
        String input = "";
	    while (true) {
		    try {
	            while(!br.ready()) { Thread.sleep(200); }
	            input = br.readLine();
            } catch (InterruptedException e) {
				try { br.close(); } catch (IOException ignore) {}
				return;
        	} catch (IOException e) { 
        		return;
			}	        
		    // Handle the defaults.
	        if (input.equals("exit"))
	            break;
            print("Type 'exit' to stop the server.");
        }		
		try { br.close(); } catch (IOException e) {}
	}
	
	public void setBackup(List<FullClientRecord> backup) {
		backupClients = new ArrayList<FullClientRecord>(backup);
	}
	
	public ConcurrentHashMap<Integer, Client> getConnectedClients() {
		return connectedClients;
	}
	
	@Override
	public synchronized int register(CharSequence IPaddress, int portNumber, CharSequence type) throws AvroRemoteException {
		currentId++;
		print("Client connected: "+ IPaddress
		+ "::" + portNumber + " (ID: "+ (currentId) + ") on thread " 
		+ Thread.currentThread().getId());
		Client connected_client = null;
		FullClientRecord backup = null;
		for (FullClientRecord record : backupClients) {
			if (IPaddress.toString().equals(record.getIPaddress().toString()) &&
					 portNumber == record.getPortNumber()) {
				backup = record;
				break;
			}
		}
		if (backup != null) 
			backupClients.remove(backup);
		try {
			switch (type.toString()) {
				case "Fridge":
					connected_client = new Fridge(IPaddress, portNumber);
					break;
				case "Light":
					Light light = new Light(IPaddress, portNumber);
					if (backup != null) {
						boolean currentState = light.getState();
						light.setPreviousLightState(backup.getPreviousLight());
						if (isHouseEmpty(null) && currentState)
							light.switchOff();
					}
					connected_client = light;
					break;
				case "TemperatureSensor":
					TemperatureSensor tempSensor = new TemperatureSensor(IPaddress, portNumber);
					if (backup != null)
						tempSensor.setTemperatureHistory(backup.getTemperatureArray());
					connected_client = tempSensor;
					break;
				case "User":
					User user = new User(IPaddress, portNumber);
					boolean enter = true;
					if (backup != null) {
						enter = backup.getInHouse();
						user.setInHouse(enter);
					}
					boolean houseEmpty = isHouseEmpty(user);
					if (enter && houseEmpty) {
						// Restore all the lights to their previous state.
						for (int key : connectedClients.keySet()) {
							Client tempClient = connectedClients.get(key);
							if (tempClient instanceof Light)
								((Light) tempClient).restore();
						}
					}
					// Announce it to all other users.
					for (int key : connectedClients.keySet()) {
						Client tempClient = connectedClients.get(key);
						if (tempClient instanceof User && tempClient != user)
							((User) tempClient).announceEnter(currentId, enter);
					}
					connected_client = user;
					break;
				default: 
					return -1;
			}
		} catch (IOException e) { return -1; }
		connectedClients.put(currentId, connected_client);
		return currentId;
	}
	
	@Override
	public void ping() {}
	
	@Override
	public List<FullClientRecord> requestBackup() throws AvroRemoteException {
		List<FullClientRecord> result =  new ArrayList<FullClientRecord>();
		for (int key : connectedClients.keySet()) {
			Client client = connectedClients.get(key);
			String className = client.getClass().getName();
			String type = className.substring(className.lastIndexOf('.')+1); 
			boolean inHouse = false;
			boolean previousLightState = false;
			List<Float> temperatureHistory = new ArrayList<Float>();
			switch(type) {
				case "User":
					inHouse = ((User) client).isInHouse();
					break;
				case "TemperatureSensor":
					temperatureHistory = ((TemperatureSensor) client).getTemperatureHistory();
					break;
				case "Light":
					previousLightState = ((Light) client).getPreviousLightState();
					break;
			}
		    result.add(new FullClientRecord(key, client.getIPAddress(), 
		    		   client.getPortNumber(), type, inHouse, previousLightState, 
		    		   temperatureHistory));
		}
		for (FullClientRecord record : backupClients) {
			result.add(record);
		}
		return result;
	}

	@Override
	public boolean userEnterExit(int id, boolean enter) throws AvroRemoteException {
		if (!connectedClients.containsKey(id))
			return false;
		Client client = connectedClients.get(id);
		if (client == null || !(client instanceof User))
			return false;
		if (! ((User)client).setInHouse(enter) )
			return false;
		boolean houseEmpty = isHouseEmpty(client);
		if (enter) {
			if (houseEmpty) {
				// House was previously empty.
				// Restore all the lights to their previous state.
				for (int key : connectedClients.keySet()) {
					Client tempClient = connectedClients.get(key);
					if (tempClient instanceof Light) {
						((Light) tempClient).restore();
					}
				}
			}
			print("User with id " + id + " has entered the house.");
		}
		else {
			if (houseEmpty) {
				// User was the only one in the house and now leaves.
				// Save the light states.
				for (int key : connectedClients.keySet()) {
					// Switch all the lights off.
					Client tempClient = connectedClients.get(key);
					if (tempClient instanceof Light)
						((Light) tempClient).switchOff();
				}
			}
			print("User with id " + id + " has left the house.");
		}
		// Announce it to all other users.
		for (int key : connectedClients.keySet()) {
			Client tempClient = connectedClients.get(key);
			if (tempClient instanceof User && tempClient != client)
				((User) tempClient).announceEnter(id, enter);
		}
		return true;
	}
	
	private boolean isHouseEmpty(Client client) {
		for (int key : connectedClients.keySet()) {
			Client tempClient = connectedClients.get(key);
			if (tempClient != client && tempClient instanceof User && 
				((User) tempClient).isInHouse()) {
					return false;
			}
		}
		return true;
	}

	@Override
	public List<BasicClientRecord> listClients() {
		List<BasicClientRecord> result =  new ArrayList<BasicClientRecord>();
		for (int key : connectedClients.keySet()) {
			Client client = connectedClients.get(key);
			String className = client.getClass().getName();
			String type = className.substring(className.lastIndexOf('.')+1); 
		    result.add(new BasicClientRecord(key, client.getIPAddress(), 
		    		   client.getPortNumber(), type));
		}
		return result;
	}

	@Override
	public Map<CharSequence, Boolean> getLightStates()
			throws AvroRemoteException {
		Map<CharSequence, Boolean> lightStates = new HashMap<CharSequence, Boolean>();
		for (int key : connectedClients.keySet()) {
			Client client = connectedClients.get(key);
			if (client instanceof Light) {
				Light light = (Light) client;
				lightStates.put(Integer.toString(key), light.getState());
			}
		}
		return lightStates;
	}

	@Override
	public boolean switchLight(int id) throws AvroRemoteException {
		if (!connectedClients.containsKey(id))
			return false;
		Client client = connectedClients.get(id);
		if (client == null || !(client instanceof Light))
			return false;
		Light light = (Light) client;
		light.switchState();
		return true;
	}

	@Override
	public List<CharSequence> getFridgeInventory(int fridgeId)
			throws AvroRemoteException {
		if (!connectedClients.containsKey(fridgeId))
			return new ArrayList<CharSequence>();
		Client client = connectedClients.get(fridgeId);
		if (client == null || !(client instanceof Fridge))
			return new ArrayList<CharSequence>();
		return ((Fridge) client).getInventory();
	}

	@Override
	public float getTemperatureHouse() throws AvroRemoteException {
		float totalTemperature = 0;
		int numberOfTemperatureSensors = 0;
		for (int key : connectedClients.keySet()) {
			Client client = connectedClients.get(key);
			if (client instanceof TemperatureSensor) {
				totalTemperature += ((TemperatureSensor) client).getTemperature();
				numberOfTemperatureSensors++;
			}
		}
		if (numberOfTemperatureSensors == 0) 
			return 0;
		return totalTemperature/numberOfTemperatureSensors;
	}
	
	@Override
	public List<Float> getTemperatureHistory() throws AvroRemoteException {
		int maxSize = 0;
		List< List<Float> > history = new ArrayList<List<Float>>();
		for (int key : connectedClients.keySet()) {
			Client client = connectedClients.get(key);
			if (client instanceof TemperatureSensor) {
				List<Float> tempHistory = ((TemperatureSensor) client).getTemperatureHistory();
				if (tempHistory.size() > maxSize)
					maxSize = tempHistory.size();
				history.add(tempHistory);
			}
		}
		
		List<Float> average_history = new ArrayList<Float>();
		for (int j = 0; j < maxSize; j++) {
			int numberOfTemperatureSensors = 0;
			float temp_sum = 0;
			for (int i = 0; i < history.size(); i++) {
				if (history.get(i).size()-1 >= j) {
					numberOfTemperatureSensors++;
					temp_sum += history.get(i).get(j);
				}
			}
			average_history.add(temp_sum / numberOfTemperatureSensors);
		}
		return average_history;
	}

	@Override
	public void addTemperature(int id, float randomValue){
		if (connectedClients.get(id) instanceof TemperatureSensor)
			((TemperatureSensor) connectedClients.get(id)).addTemperature(randomValue);
	}

	@Override
	public BasicClientRecord openFridge(int id) throws AvroRemoteException {
		if (!connectedClients.containsKey(id))
			return new BasicClientRecord(-1, "", 0, "");
		Client client = connectedClients.get(id);
		if (client == null || !(client instanceof Fridge))
			return new BasicClientRecord(-1, "", 0, "");
		if (((Fridge) client).isOpen() )
			return new BasicClientRecord(-2, "", 0, "");
		return new BasicClientRecord(id, client.getIPAddress(), client.getPortNumber(), 
									 client.getClass().getName());
	}

	@Override
	public void announceEmpty(int fridgeId) {
		for (int key : connectedClients.keySet()) {
			Client client = connectedClients.get(key);
			if (client instanceof User)
				((User) client).announceEmpty(fridgeId); 
		}
	}

	@Override
	public void reestablishController(CharSequence IPAddress, int portNumber) {
		try {
			for (int key : connectedClients.keySet())
				connectedClients.get(key).setNewController(IPAddress, portNumber);
		} catch (AvroRemoteException ignore) {}
		prevControllerDetails = new AbstractMap.SimpleEntry<>(IPAddress.toString(), portNumber);
		cliThread.interrupt();
	}
	
	public static void print(String message) {
		System.out.print("\n" + message + "\n" + "Controller> ");
	}

}
