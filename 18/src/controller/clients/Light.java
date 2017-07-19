package controller.clients;

import java.io.IOException;

import org.apache.avro.AvroRemoteException;

import proto.ClientProto;

public class Light extends Client {
	
	private boolean previousLightState = false;
	
	public Light(CharSequence IPaddress, int portNumber) throws IOException {
		super(IPaddress, portNumber);
		this.proxy = this.connection.connect(ClientProto.class, "");
	}
	
	public boolean getState() throws AvroRemoteException {
		return proxy.getState(); 
	}
	
	public boolean getPreviousLightState() {
		return previousLightState; 
	}	
	
	public void setPreviousLightState(boolean state) {
		previousLightState = state;
	}
	
	public void switchState() throws AvroRemoteException {
		proxy.switchState();
	}
	
	public void switchOff() throws AvroRemoteException {
		previousLightState = getState();
		proxy.changeState(false);
	}
	
	public void restore()  throws AvroRemoteException {
		proxy.changeState(previousLightState);
	}
}
