package controller.clients;

import java.io.IOException;
import java.util.List;

import org.apache.avro.AvroRemoteException;

import proto.ClientProto;

public class Fridge extends Client{
	
	public Fridge(CharSequence IPaddress, int portNumber, int controllerPortNumber) throws IOException{
		super(IPaddress, portNumber, controllerPortNumber);
		this.proxy = this.connection.connect(ClientProto.class, "");
	}
	
	public List<CharSequence> getInventory() throws AvroRemoteException{
		return proxy.getInventory();
	}	
	
	public int getConnectedId() throws AvroRemoteException {
		return proxy.getCurrentUser();
	}
}
