package clients;

import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.avro.AvroRemoteException;

import network.Connection;
import proto.ClientProto;


public class Fridge extends Client {
	private static Set<String> inventory = new HashSet<String>();
	private boolean isOpen = false;
	private Connection userConnection = null;
	private ClientProto userProxy = null;
	
	public Fridge() { 
		super();
		type = "Fridge"; 
	}
			
	private void list() {
		System.out.println("Commands:");
		System.out.println("=========");
		System.out.println("id");
		System.out.println("inventory");
		//System.out.println("add [item]");
		System.out.println("exit");
		System.out.println("");
	}
	
	private void inventory(){
        for(CharSequence item : inventory) 
            System.out.println(item);
	}
	
	private void addItem(String newItem) {
		inventory.add(newItem);
	}
	
	private void removeItem(CharSequence newItem) throws AvroRemoteException {
		inventory.remove(newItem);
		if (inventory.isEmpty())
			proxy.announceEmpty(controllerConnection.getId());
	}

	protected void handleInput(String input) throws AvroRemoteException {
		String[] command = input.split(" ");
        switch (command[0]) {
        	case "list":
        		list();
        		break;
        	case "inventory": 
        		inventory();
        		break;
        	case "add":
        		if (command.length > 1) {
        			addItem(command[1]);
        		}
        		else {
            		System.out.println("Please specify an item.");
        		}
        		break;
        	case "remove":
        		if (command.length > 1) {
        			removeItem(command[1]);
        		}
        		else {
            		System.out.println("Please specify an item.");
        		}
        		break;
        	default:
	        	System.out.println("Command not recognized. Type 'list' for more information.");
        }
	}
	
	public static void main(String[] args) {
		String serverIPAddress = "";
		int serverPortnumber = 6789;
		if (args.length > 0) {
			if (!args[0].equals("local"))
				serverIPAddress = args[0];
		}
		if (args.length > 1)	
			serverPortnumber = Integer.parseInt(args[1]);
		Entry<String, Integer> controllerDetails = new AbstractMap.SimpleEntry<>(serverIPAddress, serverPortnumber);
		while (!controllerDetails.getKey().equals("None")) 
			controllerDetails = new Fridge().run(controllerDetails);
	}

	@Override
	public List<CharSequence> getInventory() throws AvroRemoteException {
		List<CharSequence> temp = new ArrayList<CharSequence>();
		temp.addAll(inventory);
		return temp;
	}
	
	@Override
	public boolean addFridgeItem(CharSequence item) throws AvroRemoteException {
		return inventory.add(item.toString());
	}

	@Override
	public boolean removeFridgeItem(CharSequence item)
			throws AvroRemoteException {
		boolean retVal = inventory.remove(item.toString());
		if (inventory.isEmpty())
			proxy.announceEmpty(controllerConnection.getId());
		return retVal;
	}
	
	@Override
	public boolean isOpen() throws AvroRemoteException {
		return isOpen;
	}
	
	@Override
	public void closeFridge() {
		isOpen = false;
	}
	
	@Override
	public Void connectFridge(CharSequence userIP, int userPortNumber) throws AvroRemoteException {
		/*
		// In different thread!!
		userConnection = new Connection(userIP.toString(), 
										  controllerConnection.getClientPortNumber(), 
										  userPortNumber);
		try {
			userProxy = userConnection.connect(ClientProto.class, type);
		} catch (IOException e) {
			return null;
		}
		while (isOpen) {
			try {
				userProxy.ping();
			} catch (AvroRemoteException | EOFException | UndeclaredThrowableException e) {
				break;
			}
			try { Thread.sleep(1000); } catch (InterruptedException e) {}
		}
		userConnection.disconnect();
		userConnection = null;
		isOpen = false;
		*/
		return null;
	}

}
