package clients;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.AbstractMap;
import java.util.Map.Entry;
import java.util.Random;

import org.apache.avro.AvroRemoteException;


public class TemperatureSensor extends Client {
	private float temperature = 0;
	private Random generator;
	
	public TemperatureSensor() { 
		super();
		initialize();
		type = "TemperatureSensor"; 
	}
	
	private void initialize(){
		generator = new Random();
	    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));     
        String input = "";
        while (input.equals("")) {
    		System.out.println("TemperatureSensor> Please provide the initial temperature.");
		    try {
	            while(!br.ready()) { Thread.sleep(200); }
	            input = br.readLine();
	    	} catch (IOException | InterruptedException e) {
	    		System.err.println("Input error.");
	    	    try { br.close(); } catch (IOException e1) {}
				System.exit(0);
			}	
		    try { temperature = Float.parseFloat(input); } catch (NumberFormatException e) { input = "";}
        }
	    try { br.close(); } catch (IOException e) {}
	}
	
	public void addTemperature() {
		temperature = temperature - ( 1 - (2 * generator.nextFloat()) );
		proxy.addTemperature(controllerConnection.getId(), temperature);
	}
	
	private void getTemperature(){
		System.out.println(temperature + " degres celsius");
	}
	
	private void list() {
		System.out.println("id");
		System.out.println("Commands:");
		System.out.println("=========");
		System.out.println("temperature");
		System.out.println("exit");
		System.out.println("");
	}
	
	protected void handleInput(String input) throws AvroRemoteException {
		String[] command = input.split(" ");
        switch (command[0]) {
        	case "list":
        		list();
        		break;
        	case "temperature": 
        		getTemperature();
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
			controllerDetails = new TemperatureSensor().run(controllerDetails);
	}
}
