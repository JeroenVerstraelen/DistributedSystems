package controller.clients;

import java.io.IOException;
import java.util.List;

import org.apache.avro.AvroRemoteException;

import proto.ClientProto;

public class Fridge extends Client{
	
	public Fridge(CharSequence IPaddress, int portNumber) throws IOException{
		super(IPaddress, portNumber);
		this.proxy = this.connection.connect(ClientProto.class, "");
	}
	
	public List<CharSequence> getInventory() throws AvroRemoteException{
		return proxy.getInventory();
	}	
	
	public boolean isOpen() throws AvroRemoteException {
		return proxy.isOpen();
	}
}
