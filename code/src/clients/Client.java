package clients;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.InetSocketAddress;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
	protected static boolean someoneElected = false;
	private static int newControllerPortNumber = 6789;
	
	// Settings
	private int backupSeconds = 2; // Amount of seconds before requesting a new backup.
	private int maxControllerWait = 360; // Amount of seconds to wait for a new controller.
	
	public Client() {
		// Make sure the input stream cannot be closed.
		System.setIn(new UnClosableDecorator(System.in));
		controllerCandidateTypes.clear();
		controllerCandidateTypes.add("User");
		controllerCandidateTypes.add("Fridge");
	}
	
	public Entry<String,Integer> run(Entry<String, Integer> controllerDetails) {
			// Start server (separate thread).========
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
	    	//System.out.println("Attempting connection with controller.");
			try {
				proxy = controllerConnection.connect(ControllerProto.class, type);
			} catch (IOException e) {
				System.out.println(type + "> Cannot establish connection with the controller");
				clientServer.close();
				return new AbstractMap.SimpleEntry<>("None", 0);
			}
	    	//System.out.println("Connection established.");

			
			// Start handling user input.
			cliThread = new Thread(new Runnable()
		    {
		      @Override
		      public void run() { 
		    	  cli(); 
		      }
		    });
			cliThread.start();
			
			// Temperature sensors have an internal clock
			Thread temperatureClock = null;
			if (this instanceof TemperatureSensor) { 
				temperatureClock = new Thread(new Runnable()
				{
			      @Override
			      public void run() { 
			    	  runClock();
			      }
			    });
				temperatureClock.start();
			}
			
			// Ping to see if the controller is responding.
			int counter = 0;
			while (true) {
				try {
					if (!cliThread.isAlive())
						// User killed interface, end main program loop.
						break;
					if (counter % backupSeconds == 0)
						connectedClientsBackup = proxy.requestBackup();
					proxy.ping();
					counter++;
				} catch (AvroRemoteException | UndeclaredThrowableException e) {
					// Controller is not responding to ping request.
					if (!electionIsRunning) {
						counter = 0;
						electionIsRunning = true;
						controllerConnection.disconnect();
						if ( controllerCandidateTypes.contains(type) )
							startElection();
					}
					/*
					else {
						counter++;
						if (counter > maxControllerWait) {
							System.out.println("Max controller wait time exceeded, shutting down.");
							cliThread.interrupt();
						}
					}
					*/
				}
				// Ping every second
				try { Thread.sleep(1000); } catch (InterruptedException e) {}
			}
			
			// Cleanup after main loop ends.
			clientServer.close();
			controllerConnection.disconnect();
			try { 
				clientServer.join(); 
				cliThread.join(); 
				if (temperatureClock != null) {
					temperatureClock.interrupt(); 
					temperatureClock.join();
				}
			} catch (InterruptedException e) {}
			
			// If elected, become new controller.
			if (elected) {
				elected = false;
				Controller controller = new Controller();
				// Remove this client from the backup
				int toRemove = -1;
				for (int i = 0; i < connectedClientsBackup.size(); i++) {
					if (connectedClientsBackup.get(i).getId() == controllerConnection.getId()) {
						toRemove = i;
						break;
					}
				}
				if (toRemove != -1)
					connectedClientsBackup.remove(toRemove);
				controller.setBackup(connectedClientsBackup);
				Entry<String,Integer> result = controller.run(newControllerPortNumber);
				return result;
			}
		return new AbstractMap.SimpleEntry<>("None", 0);
	}
	
	protected void runClock() {}
	
	protected void cli() {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));     
        String input = "";
        
	    while (true) {
	    	System.out.print(type + "> ");
	    	// Read the next line.
		    try {
	            while(!br.ready()) { Thread.sleep(200); }
	            input = br.readLine();
            } catch (InterruptedException e) {
				try { br.close(); } catch (IOException ignore) {}
				return;
        	} catch (IOException e) { 
        		return;
			}	        
		    // Handle the input from the user.
	        if (input.equals("exit"))
	            break;
	        if (input.equals("id")) {
	            System.out.println("Client ID: " + controllerConnection.getId());
	            continue;
	        }
	        try {
	        	handleInput(input);
			} catch(AvroRemoteException e) {
				/*
				// Controller is not responding to user initiated request.
				if (!electionIsRunning) {
					electionIsRunning = true;
					controllerConnection.disconnect();
					if ( controllerCandidateTypes.contains(type) )
						startElection();
				}
				// Wait until election is done, wait again if woken up before end of election.
				while (electionIsRunning) {
					try { 
						synchronized(cliThread) { cliThread.wait(); } 
					} catch (InterruptedException e1) {		
						try { br.close(); } catch (IOException ignore) {}
					}
				}
				if (!elected)
					// Handle the left over input that caused exception.
					try {
						handleInput(input); 
					} catch(AvroRemoteException ignore) {
					} catch (InterruptedException e1) {		
						try { br.close(); } catch (IOException ignore) {}
					}
				*/
			} catch (InterruptedException e1) {		
				try { br.close(); } catch (IOException ignore) {}
			}
	        input = "";
        }		
		try { br.close(); } catch (IOException e) {}
	}
	
	protected void handleInput(String input) throws AvroRemoteException, InterruptedException{}
	
	protected void startElection() {
        System.out.println("STARTING ELECTION");
		if(!connectRing())
			return;
		int id = controllerConnection.getId();
		Client.participantMap.put(id, true);
		ringProxy.election(id, id); 
	}
	
	private boolean connectRing() {
		// Remove non controllerCandidateTypes
		List<FullClientRecord> electClients = new ArrayList<FullClientRecord>();
		for (FullClientRecord client : connectedClientsBackup) {
			if (controllerCandidateTypes.contains( client.getType().toString() ))
				electClients.add(client);
		}
		// Sort list on Id
		Collections.sort(electClients, new Comparator<FullClientRecord>() {
		    public int compare(FullClientRecord one, FullClientRecord other) {
		        return one.getId().compareTo(other.getId());
		    }
		}); 
		while(true) {
			int clientCount = electClients.size();
			if (clientCount < 2) {
				// Automatically become a controller.
				elected = true;
				newControllerPortNumber = NetworkUtils.getValidPortNumber(6750);
				elected(controllerConnection.getId(), controllerConnection.getClientIPAddress(),
						newControllerPortNumber); 
				ringProxy = null;
				return false;
			}
			// Find the next id in the ring to connect to.
			FullClientRecord nextClient = null;
			Integer currentId = controllerConnection.getId();
			for(int i=0; i<clientCount; i++) {
				int checkId = electClients.get(i).getId();
			    if (currentId == checkId) {
			    	int nextId = i+1;
			    	if (nextId >= clientCount) 
			    		nextId = 0;
			    	nextClient = electClients.get(nextId);
			        break;
			    }
			}
			try {
				// Connect to next ID
				String ownIPAddress = controllerConnection.getClientIPAddress();
				controllerConnection = new Connection(nextClient.getIPaddress().toString(), 
													  controllerConnection.getClientPortNumber(), 
													  nextClient.getPortNumber());
				controllerConnection.setId(currentId);
				controllerConnection.setClientIPAddress(ownIPAddress);
				ringProxy = controllerConnection.connect(ClientProto.class, "");
				ringProxy.settleConnection(); // Makes sure one way requests behave properly. 
				// Notify while loop in election() that ringProxy can be used.
	            System.out.println("Connected with " + nextClient.getId());
			    synchronized(ringProxy) { ringProxy.notifyAll(); }
	            System.out.println("Done synchronizing ringProxy");
			    break;
			} catch (IOException e) {
				System.err.println("Could not connect to next Client after Controller failure");
				System.err.println("Attempting next client.");
				electClients.remove(nextClient);
			}
		}
        System.out.println("Fuly left");
		return true;
	}

	@Override
	public void election(int i, int id) {
		System.out.println("========" );
		System.out.println("election( " +  i + ", " + id  + " )" );
		// Make sure connection with next id is set up.
		if (!electionIsRunning) {
			electionIsRunning = true;
			if (!connectRing()) {
				System.out.println("Ring not connected." );
				return;
			}
		}
		else {
			System.out.println("Waiting for ringProxy." );
			// Wait until ringProxy is initialized in startElection.
			while (ringProxy == null) {
		            try {
		                Thread.currentThread().wait();
		            } catch (InterruptedException | IllegalMonitorStateException e) {}
			}
			System.out.println("Done waiting for ringProxy." );
		}
		// Election algorithm
		int ownId = controllerConnection.getId();
		if (id > ownId) {
			// electionIsRunning = false;
			ringProxy.election(i, id);
			System.out.println("sent election( " +  i + ", " + id  + " )" );
			participantMap.put(ownId, true);
		}
		if (id <= ownId && i != ownId) {
			if ((!participantMap.containsKey(ownId)) || (!participantMap.get(ownId))) {
				ringProxy.election(ownId, ownId);
				System.out.println("sent election( " +  ownId + ", " + ownId  + " )" );
				participantMap.put(ownId, true);
			}
		}
		if (i == ownId) {
			elected = true;
			System.out.println("Electing self");
			newControllerPortNumber = NetworkUtils.getValidPortNumber(6750);
			System.out.println("sent elected( " +  ownId + ", ownIP, portNum )" );
			ringProxy.elected(ownId, controllerConnection.getClientIPAddress(), newControllerPortNumber);
			// Reset election variables.
			ringProxy = null;
		}
		System.out.println("========" );
	}

	@Override
	public void elected(int i, CharSequence IPAddress, int portNumber) {
		System.out.println("elected( " +  i + ", " + IPAddress  + ", " + portNumber + " )" );
		if (someoneElected)
			return;
		someoneElected = true;
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
			    synchronized(cliThread) { cliThread.notifyAll(); }
				// Continue handling user input.
				electionIsRunning = false;
				someoneElected = false;
			}
		} else {
			if (electionIsRunning) {
				// Make sure all non electable clients know there is a new Controller.
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
				participantMap.clear();
				System.out.println("Elected ownid = i, cliThread interruptedd");
				cliThread.interrupt();
				electionIsRunning = false;
				someoneElected = false;
			}
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

	@Override
	public void closeFridge() {
	}

	@Override
	public int getCurrentUser() throws AvroRemoteException {
		return -1;
	}

	@Override
	public Void setCurrentUser(int id) throws AvroRemoteException {
		return null;
	}

}
