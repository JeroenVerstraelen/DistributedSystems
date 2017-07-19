package clients;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import network.Connection;

import org.apache.avro.AvroRemoteException;

import proto.BasicClientRecord;
import proto.ClientProto;


public class User extends Client {
	
	private Connection fridgeConnection = null;
	private ClientProto fridgeProxy = null;
	private static boolean fridgeConnectionRunning = false;
	
	User() {
		super();
		type = "User";
	}
	
	private void list() {
		System.out.println("Commands:");
		System.out.println("=========");
		System.out.println("id");
		System.out.println("enter");
		System.out.println("leave");
		System.out.println("clients");
		System.out.println("lights");
		System.out.println("switch [lightID]");
		System.out.println("inventory [fridgeID]");
		System.out.println("openfridge [fridgeID]");
		System.out.println("temperature");
		System.out.println("temperaturehistory");
		System.out.println("exit");
		System.out.println("");
	}
	
	private String enter() throws AvroRemoteException {
		proxy.userEnterExit(controllerConnection.getId(), true);
	    return "You have entered the house.";
	}
 	
	private String leave() throws AvroRemoteException {
		proxy.userEnterExit(controllerConnection.getId(), false);
	    return "You have left the house.";
	}
	
	private void listClients() throws AvroRemoteException {
		List<BasicClientRecord> clients = proxy.listClients();
		for (int i = 0; i < clients.size(); i++) {
			BasicClientRecord client = clients.get(i);
			System.out.println("ID: " + client.getId());
			System.out.println("Type: " + client.getType());
			System.out.println("IPAddress: " + client.getIPaddress());
			System.out.println("Portnumber: " + client.getPortNumber());
			System.out.println("");
		}
	}
	
	private void listLights() throws AvroRemoteException {
		Map<CharSequence, Boolean> lights = proxy.getLightStates();
		for (Entry<CharSequence, Boolean> entry : lights.entrySet())
		    System.out.println("ID: " + entry.getKey() + " state:" + stateToString(entry.getValue()));
		System.out.println("");
	}
	
	private String stateToString(boolean state){
		if (state) return "ON";
		return "OFF";
	}
	
	private boolean switchLight(int id) throws AvroRemoteException {
		return proxy.switchLight(id);
	}
	
	private void getFridgeInventory(int id) throws AvroRemoteException {
		List<CharSequence> inventory = proxy.getFridgeInventory(id);
		for (int i = 0; i < inventory.size(); i++) 
			System.out.println(inventory.get(i));
		System.out.println("");
	}
	
	private void openFridge(int id) throws InterruptedException, AvroRemoteException {
		BasicClientRecord fridge = proxy.openFridge(id);
		switch (fridge.getId()) {
			case -1:
				System.out.println("Fridge ID is not valid.");
				return;
			case -2:
				System.out.println("The fridge has already been opened by another user.");
				return;
		}
		// Connect to the Controller.
		fridgeConnection = new Connection(fridge.getIPaddress().toString(), 
										  controllerConnection.getClientPortNumber(), 
										  fridge.getPortNumber());
		try {
			fridgeProxy = fridgeConnection.connect(ClientProto.class, type);
		} catch (IOException e) {
			System.out.println("Cannot establish connection with the Fridge.");
			return;
		}
		fridgeConnectionRunning = true;
		
		BufferedReader br = null;
		try {
		    br = new BufferedReader(new InputStreamReader(System.in));     
		    String input = "";
		    while (input.equals("")) {
		    	System.out.print(type + "-> Fridge connection> ");
			    try {
		            while(!br.ready()) { Thread.sleep(200); }
		            input = br.readLine();
		    	} catch (IOException | InterruptedException e) {
		    		try { 
			    		// Clean up.
		    			br.close();
			    		fridgeConnection.disconnect();
			    		fridgeConnection = null;
			    		fridgeConnectionRunning = false;
			    		// Make sure the entire thread cleans up.
			    		throw new InterruptedException();
		    		} catch (IOException ignore) {}
		    	}	
				String[] command = input.split(" ");
	 	        input = "";
				if (command.length == 0)
					continue;
				switch (command[0]) {
					case "inventory":
							List<CharSequence> inventory = fridgeProxy.getInventory();
							for (int i = 0; i < inventory.size(); i++) 
								System.out.println(inventory.get(i));
							System.out.println("");
						break;
					case "add":
		        		if (command.length > 1)
		        			fridgeProxy.addFridgeItem(command[1]);
						break;
					case "remove":
		        		if (command.length > 1) {
		        			if (fridgeProxy.removeFridgeItem(command[1])) {
		        				System.out.println("Item removed.");
		        			}
		        			else {
		        				System.out.println("The fridge does not have this item.");
		        			}
		        		}
						break;
					case "close":
						input = "exit";
						break;
					default:
						System.out.println("inventory | add [item] | remove [item] | close");
				}
		    }
		    // Clean up.
			try { br.close(); } catch (IOException e) {}
			fridgeConnection.disconnect();
			fridgeConnection = null;
			fridgeConnectionRunning = false;
		} catch (AvroRemoteException e) {
			// Connection to fridge lost.
			if (br != null)
				try { br.close(); } catch (IOException ignore) {}
			fridgeConnection.disconnect();
			fridgeConnection = null;
			fridgeConnectionRunning = false;
			System.out.println("The fridge no longer exists.");
		}
	}	
	
	private void getTemperature() throws AvroRemoteException {
		System.out.println(proxy.getTemperatureHouse() + " degres celsius.");
	}
	
	private void getTemperatureHistory() throws AvroRemoteException {
		List<Float> history = proxy.getTemperatureHistory();
		for (float temperature : history)
			System.out.println(temperature + " degres celsius.");
		System.out.println("");
	}
	
	protected void handleInput(String input) throws AvroRemoteException, InterruptedException {
		String[] command = input.split(" ");
        switch (command[0]) {
        	case "list":
        		list();
        		break;
        	case "enter": 
        		System.out.println(enter());
        		break;
        	case "leave":
        		System.out.println(leave());
        		break;
        	case "clients": 
        		listClients();
        		break;
        	case "lights": 
        		listLights();
        		break;
        	case "switch": 
        		if (command.length > 1) {
        			try { 
        				int id = Integer.parseInt(command[1]); 
            			if (!switchLight(id)) 
            	        	System.out.println("Light ID is not valid.");
        			} catch (NumberFormatException e) { 
        	        	System.out.println("Light ID is not valid.");
        			}
        		}
        		break;
        	case "inventory": 
        		if (command.length > 1) {
        			try { 
        				int id = Integer.parseInt(command[1]); 
            			getFridgeInventory(id);
        			} catch (NumberFormatException e) { 
        	        	System.out.println("Fridge ID is not valid.");
        			}
        		}
        		break;
        	case "openfridge":
        		if (command.length > 1) {
        			try { 
        				int id = Integer.parseInt(command[1]); 
            			openFridge(id);
        			} catch (NumberFormatException e) { 
        	        	System.out.println("Fridge ID is not valid.");
        			}
        		}
        		input = "";
        		break;
        	case "temperature":
        		getTemperature();
        		break;
        	case "temperaturehistory":
        		getTemperatureHistory();
        		break;
        	default:
	        	System.out.println("Command not recognized. Type 'list' for more information.");
        }
	}
	
	public static void main(String[] args) {
		String serverIPAddress = "";
		int serverPortnumber = 6789;
		if (args.length > 0) {
			if (!args[0].equals("null"))
				serverIPAddress = args[0];
		}
		if (args.length > 1)	
			serverPortnumber = Integer.parseInt(args[1]);
		Entry<String, Integer> controllerDetails = new AbstractMap.SimpleEntry<>(serverIPAddress, serverPortnumber);
		while (!controllerDetails.getKey().equals("None")) 
			controllerDetails = new User().run(controllerDetails);
	}
	
	@Override
	public void announceEmpty(int fridgeId) {
		if (!fridgeConnectionRunning) 
			System.out.println("Fridge (" + fridgeId + ") is empty.");
	} 
	
	@Override
	public void announceEnter(int userId, boolean enter) {
		if (!fridgeConnectionRunning) { 
			if (enter) {
				System.out.println("User (" + userId + ") has entered the building.");
				return;
			}
			System.out.println("User (" + userId + ") has left the building.");
		}
	}
}
