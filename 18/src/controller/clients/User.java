package controller.clients;

import java.io.IOException;

import proto.ClientProto;


public class User extends Client {
	
	private boolean inHouse = true;
	
	public User(CharSequence IPaddress, int portNumber) throws IOException {
		super(IPaddress, portNumber);
		this.proxy = this.connection.connect(ClientProto.class, "");
	}

	public boolean isInHouse() {
		return inHouse;
	}

	public boolean setInHouse(boolean inHouse) {
		if (this.inHouse == inHouse)
			return false;
		this.inHouse = inHouse;
		return true;
	}
	
	public void announceEmpty(int fridgeId) {
		proxy.announceEmpty(fridgeId);
	}
	
	public void announceEnter(int userId, boolean enter) {
		if (inHouse)
			proxy.announceEnter(userId, enter);
	}
}
