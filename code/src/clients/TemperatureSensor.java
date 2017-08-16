package clients;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.AbstractMap;
import java.util.Map.Entry;
import java.util.Random;

import org.apache.avro.AvroRemoteException;


public class TemperatureSensor extends Client {
	private int temperatureSeconds = 3; // Amount of seconds before sending a new temperature.
	final int timeOutputSeconds = 5;  // Amount of seconds before outputting time value.
	private float temperature = 0;
	private double driftValue = 0;
	private double time = 0;
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
	    try {
			System.out.println("TemperatureSensor> Please provide the initial temperature.");
            while(!br.ready()) { Thread.sleep(200); }
            temperature = Float.parseFloat(br.readLine());
			System.out.println("TemperatureSensor> Please provide the drift value.");
            while(!br.ready()) { Thread.sleep(200); }
			driftValue = Float.parseFloat(br.readLine());
    	} catch (IOException | InterruptedException | NumberFormatException e) {
    		System.err.println("Input error.");
    	    try { br.close(); } catch (IOException e1) {}
			System.exit(0);
		}	
	    try { br.close(); } catch (IOException e) {}
	}
	
	protected void runClock() {
		int counter = 0;
		double lastSent = 0;
		while (true) {
			try {
				counter++;
				if (!cliThread.isAlive())
					break;
				time += 1 + driftValue;
				if (time - lastSent > temperatureSeconds) {
					lastSent = time;
					System.out.println("Sending temperature: " + time);
					addTemperature();
				}
				if (counter % timeOutputSeconds == 0) {
					counter = 0;
					long start_time = System.currentTimeMillis();
					time = proxy.getTime();
					long end_time = System.currentTimeMillis();
					double difference = (end_time - start_time);
					time += difference / 2;
					System.out.println("Clock time: " + time);
				}
			} catch (AvroRemoteException | UndeclaredThrowableException e) {}
			try { Thread.sleep(1000); } catch (InterruptedException e) {}
		}
	}
	
	public void addTemperature() throws AvroRemoteException, UndeclaredThrowableException {
		temperature = temperature - ( 1 - (2 * generator.nextFloat()) );
		proxy.addTemperature(controllerConnection.getId(), temperature);
	}

	public int getTime() throws AvroRemoteException, UndeclaredThrowableException {
		return proxy.getTime();
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
			if (!args[0].equals("local"))
				serverIPAddress = args[0];
		}
		if (args.length > 1)	
			serverPortnumber = Integer.parseInt(args[1]);
		Entry<String, Integer> controllerDetails = new AbstractMap.SimpleEntry<>(serverIPAddress, serverPortnumber);
		while (!controllerDetails.getKey().equals("None")) 
			controllerDetails = new TemperatureSensor().run(controllerDetails);
	}
}
