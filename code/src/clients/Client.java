package clients;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.InetSocketAddress;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import network.Connection;
import network.NetworkUtils;
import network.avro.SaslSocketServer;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.specific.SpecificResponder;

import controller.Controller;
import proto.ClientProto;
import proto.ControllerProto;
import proto.FullClientRecord;
import utils.UnClosableDecorator;

public class Client implements ClientProto {
	
	protected static Connection controllerConnection = null;
	protected static ControllerProto proxy = null;
	protected static List<String> controllerCandidateTypes = new ArrayList<String>();
	protected static List<FullClientRecord> connectedClientsBackup = new ArrayList<FullClientRecord>();
	protected static Thread cliThread = null;
	protected String type = "Client";
	
	// Election variables
	private static HashMap<Integer, Boolean> participantMap = new HashMap<Integer, Boolean>();
	protected static ClientProto ringProxy = null;
	protected static boolean electionIsRunning = false;
	protected static boolean elected = false;
	private static int newControllerPortNumber = 6789;
	
	// Settings
	private int temperatureSeconds = 3; // Amount of seconds before sending a new temperature.
	private int backupSeconds = 2; // Amount of seconds before requesting a new backup.
	private int maxControllerWait = 60; // Amount of seconds to wait for a new controller.
	
	public Client() {
		// Make sure the input stream cannot be closed.
		System.setIn(new UnClosableDecorator(System.in));
		controllerCandidateTypes.clear();
		controllerCandidateTypes.add("User");
		controllerCandidateTypes.add("Fridge");
	}
	
	public Entry<String,Integer> run(Entry<String, Integer> controllerDetails) {
			// Start own server (On a separate thread).
			Server clientServer = null;
			int portNumber = NetworkUtils.getValidPortNumber(6790);
			try {
				clientServer = new SaslSocketServer(new SpecificResponder(ClientProto.class, 
						 				      							  this), 
						 				      		new InetSocketAddress(portNumber));
				clientServer.start();
			} catch (IOException e) {
				System.err.println(type + "> Could not start client server, shutting down.");
				System.exit(0);
			}
			System.out.println(type + "> Running client on port: " + portNumber);
			
			// Connect to the Controller.
			controllerConnection = new Connection(controllerDetails.getKey(), portNumber, controllerDetails.getValue());
			try {
				proxy = controllerConnection.connect(ControllerProto.class, type);
			} catch (IOException e) {
				System.out.println(type + "> Cannot establish connection with the controller!");
				clientServer.close();
				return new AbstractMap.SimpleEntry<>("None", 0);
			}
			
			// Start handling user input.
			cliThread = new Thread(new Runnable()
		    {
		      @Override
		      public void run() { cli(); }
		    });
			cliThread.start();
			
			// Ping to see if the controller is responding.
			int counter = 0;
			while (true) {
				try {
					if (!cliThread.isAlive())
						break;
					if (counter % backupSeconds == 0)
						connectedClientsBackup = proxy.requestBackup();
					if (this instanceof TemperatureSensor && counter % temperatureSeconds == 0) 
						((TemperatureSensor)this).addTemperature();
					proxy.ping();
					counter++;
				} catch (AvroRemoteException | UndeclaredThrowableException e) {
					// Controller is not responding.
					if (!electionIsRunning) {
						counter = 0;
						electionIsRunning = true;
						controllerConnection.disconnect();
						if ( controllerCandidateTypes.contains(type) )
							startElection();
					}
					if (electionIsRunning) {
						counter++;
						if (counter > maxControllerWait) 
							cliThread.interrupt();
					}
				}
				try { Thread.sleep(1000); } catch (InterruptedException e) {}
			}
			
			clientServer.close();
			controllerConnection.disconnect();
			try { clientServer.join(); cliThread.join(); } catch (InterruptedException e) {}
			if (elected) {
				elected = false;
				Controller controller = new Controller();
				controller.setBackup(connectedClientsBackup);
				Entry<String,Integer> result = controller.run(newControllerPortNumber);
				return result;
			}
		return new AbstractMap.SimpleEntry<>("None", 0);
	}
	
	protected void cli() {
	    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));     
        String input = "";
	    while (true) {
	    	System.out.print(type + "> ");
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
	        if (input.equals("id")) {
	            System.out.println("Client ID: " + controllerConnection.getId());
	            continue;
	        }
	        try {
	        	handleInput(input);
			} catch(AvroRemoteException e) {
				if (!electionIsRunning) {
					electionIsRunning = true;
					controllerConnection.disconnect();
					if ( controllerCandidateTypes.contains(type) )
						startElection();
				}
				while (electionIsRunning) {
					try { 
						synchronized(cliThread) { cliThread.wait(); } 
					} catch (InterruptedException e1) {		
						try { br.close(); } catch (IOException ignore) {}
					}
				}
				if (!elected)
					try {
						handleInput(input); 
					} catch(AvroRemoteException ignore) {
					} catch (InterruptedException e1) {		
						try { br.close(); } catch (IOException ignore) {}
					}
			} catch (InterruptedException e1) {		
				try { br.close(); } catch (IOException ignore) {}
			}
	        input = "";
        }		
		try { br.close(); } catch (IOException e) {}
	}
	
	protected void handleInput(String input) throws AvroRemoteException, InterruptedException{}
	
	protected void startElection() {
		if(!connectRing())
			return;
		int id = controllerConnection.getId();
		Client.participantMap.put(id, true);
		ringProxy.election(id, id); 
	}
	
	private boolean connectRing() {
		int clientCount = 0;
		for (FullClientRecord client : connectedClientsBackup) {
			if (controllerCandidateTypes.contains( client.getType().toString() ))
				clientCount++;
		}
		if (clientCount < 2) {
			// Automatically become a controller.
			elected = true;
			newControllerPortNumber = NetworkUtils.getValidPortNumber(6750);
			elected(controllerConnection.getId(), controllerConnection.getClientIPAddress(),
					newControllerPortNumber); 
			ringProxy = null;
			return false;
		}
		// Find the next ID in the ring to connect to.
		FullClientRecord nextClient = null;
		Integer id = controllerConnection.getId();
		Integer nextId = -1;
		Iterator<FullClientRecord> itr = connectedClientsBackup.iterator();
		while(itr.hasNext()) {
			FullClientRecord next = itr.next();
			if (next.getId() > id && (next.getId() < nextId || nextId == -1)) {
				if (controllerCandidateTypes.contains(next.getType().toString())) {
					nextId = next.getId();
					nextClient = next;
				}
			}
		}
		if (nextId == -1) {
			for (FullClientRecord client : connectedClientsBackup) {
				if (controllerCandidateTypes.contains( client.getType().toString() ))
					nextClient = client;
			}
		}
		// Connect to next ID
		try {
			String ownIPAddress = controllerConnection.getClientIPAddress();
			controllerConnection = new Connection(nextClient.getIPaddress().toString(), 
												  controllerConnection.getClientPortNumber(), 
												  nextClient.getPortNumber());
			controllerConnection.setId(id);
			controllerConnection.setClientIPAddress(ownIPAddress);
			ringProxy = controllerConnection.connect(ClientProto.class, "");
			ringProxy.settleConnection(); 
		    synchronized(ringProxy) { ringProxy.notifyAll(); }
		} catch (IOException e) {
			System.err.println("Could not connect to next Client after Controller failure");
			System.err.println("Shutting down.");
			System.exit(0);
		}
		return true;
	}

	@Override
	public void election(int i, int id) {
		// Reduces the amount of running elections.
		if (!electionIsRunning) {
			electionIsRunning = true;
			if (!connectRing())
				return;
		}
		// Make sure ringProxy is initialized.
		while (ringProxy == null) {
	            try {
	                Thread.currentThread().wait();
	            } catch (InterruptedException | IllegalMonitorStateException e) {}
		}
		int ownId = controllerConnection.getId();
		if (id > ownId) {
			// electionIsRunning = false;
			ringProxy.election(i, id);
			participantMap.put(ownId, true);
		}
		if (id <= ownId && i != ownId) {
			if ((!participantMap.containsKey(ownId)) || (!participantMap.get(ownId))) {
				ringProxy.election(ownId, ownId);
				participantMap.put(ownId, true);
			}
		}
		if (i == ownId) {
			elected = true;
			newControllerPortNumber = NetworkUtils.getValidPortNumber(6750);
			ringProxy.elected(ownId, controllerConnection.getClientIPAddress(), newControllerPortNumber);
			// Reset election variables.
			ringProxy = null;
		}
	}

	@Override
	public void elected(int i, CharSequence IPAddress, int portNumber) {
		int ownId = controllerConnection.getId();
		if (i != ownId) {
			if (electionIsRunning || !controllerCandidateTypes.contains(type)) {
				if (ringProxy != null)
					ringProxy.elected(i, IPAddress, portNumber);
				ringProxy = null;
				participantMap.clear();
				// Disconnect ring connection.
				controllerConnection.disconnect();		
				String ownIPAddress = controllerConnection.getClientIPAddress();
				controllerConnection = new Connection(IPAddress.toString(), controllerConnection.getClientPortNumber(), portNumber);
				controllerConnection.setClientIPAddress(ownIPAddress);
				while (true) {
					try {
						proxy = controllerConnection.connect(ControllerProto.class, type);
						break;
					} catch (IOException e) {
						// New controller is not yet online.
						try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
					}
				}
				// Continue handling user input.
				electionIsRunning = false;
			    synchronized(cliThread) { cliThread.notifyAll(); }
			}
		} else {
			// Make sure all other clients know there is a new Controller.
			Iterator<FullClientRecord> itr = connectedClientsBackup.iterator();
			while(itr.hasNext()) {
				FullClientRecord next = itr.next();
				if (!controllerCandidateTypes.contains(next.getType().toString())) {
					try {
						Connection tempConnection = new Connection(next.getIPaddress().toString(), 
							  								   	   controllerConnection.getClientPortNumber(), 
							  								   	   next.getPortNumber());
						ClientProto tempProxy = tempConnection.connect(ClientProto.class, "");
						tempProxy.settleConnection();
						tempProxy.elected(i, IPAddress, portNumber);
						tempConnection.disconnect();
					} catch (IOException | UndeclaredThrowableException e) {}
				}
			}
			electionIsRunning = false;
			participantMap.clear();
			cliThread.interrupt();
		}
	}
	
	@Override
	public void setNewController(CharSequence IPAddress, int portNumber) {
		electionIsRunning = true;
		// Disconnect ring connection.
		controllerConnection.disconnect();		
		String ownIPAddress = controllerConnection.getClientIPAddress();
		controllerConnection = new Connection(IPAddress.toString(), controllerConnection.getClientPortNumber(), portNumber);
		controllerConnection.setClientIPAddress(ownIPAddress);
		while (true) {
			try {
				proxy = controllerConnection.connect(ControllerProto.class, type);
				break;
			} catch (IOException e) {
				// New controller is not yet online.
				try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
			}
		}
		electionIsRunning = false;
	}
	
	@Override
	public void ping() {}
	
	@Override
	public Void settleConnection() throws AvroRemoteException {
		// Ensures one-way messages behave properly.
		return null;
	}

	public List<CharSequence> getInventory() throws AvroRemoteException {
		return new ArrayList<CharSequence>();
	}

	@Override
	public boolean getState() throws AvroRemoteException {
		return false;
	}

	@Override
	public void changeState(boolean state) {}
	
	@Override
	public boolean isOpen() throws AvroRemoteException {
		return false;
	}

	@Override
	public boolean addFridgeItem(CharSequence item) throws AvroRemoteException {
		return false;
	}

	@Override
	public boolean removeFridgeItem(CharSequence item)
			throws AvroRemoteException {
		return false;
	}

	@Override
	public void announceEmpty(int fridgeId) {}

	@Override
	public void announceEnter(int userId, boolean enter) {}

	@Override
	public void switchState() {}

}
