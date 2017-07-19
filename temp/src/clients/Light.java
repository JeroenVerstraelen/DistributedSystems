package clients;


import java.util.AbstractMap;
import java.util.Map.Entry;

import org.apache.avro.AvroRemoteException;


public class Light extends Client {
	private boolean state = false;
	
	public Light() { 
		super();
		type = "Light"; 
	}
	
	private void list() {
		System.out.println("Commands:");
		System.out.println("=========");
		System.out.println("id");
		System.out.println("state");
		System.out.println("exit");
		System.out.println("");
	}
	
	private void state() {
		if (state) System.out.println("ON");
		if (!state) System.out.println("OFF");
	}
	
	protected void handleInput(String input) throws AvroRemoteException {
		String[] command = input.split(" ");
        switch (command[0]) {
        	case "list":
        		list();
        		break;
        	case "state": 
        		state();
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
			controllerDetails = new Light().run(controllerDetails);
	}

	@Override
	public boolean getState() throws AvroRemoteException {
		return state;
	}

	@Override
	public void changeState(boolean newState) {
		this.state = newState;
		String stateString = "OFF";
		if (state) stateString = "ON";
		System.out.println("Light state changed to state: " + stateString);
	}
	
	@Override
	public void switchState() {
		state = !state;
		String stateString = "OFF";
		if (state) stateString = "ON";
		System.out.println("Light state changed to state: " + stateString);		
	}
}
