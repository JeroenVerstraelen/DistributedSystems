package avro.hello.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.ExecutionException;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.CallFuture;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;

import avro.hello.proto.Hello;

public class HelloClient {
	public static void main(String[] args) {
		try {
			Transceiver client = new SaslSocketTransceiver(new InetSocketAddress(6789));
			Hello.Callback proxy = SpecificRequestor.getClient(Hello.Callback.class, client);
			CallFuture<CharSequence> future = new CallFuture<CharSequence>();
		    // Test sync call:
		    System.out.println("1. " + new Date() + ": Saying Hello (sync)...");
		    CharSequence syncResult = proxy.sayHello("Bob"); // This should block for 5 seconds
		    System.out.println("2. " + new Date() + ": Chat.hello() returned \"" + syncResult + "\"");

		    // Test async call:
		    final CallFuture<CharSequence> future1 = new CallFuture<CharSequence>();
		    System.out.println("\n3. " + new Date() + ": Saying Hello (async)...");
		    proxy.sayHello("Bob", future1); // This should not block.
		    System.out.println("4. " + new Date() + ": Chat.hello(Callback<CharSequence>) returned");
		    CharSequence asyncResult = future1.get(); // This should block for 5 seconds
		    System.out.println("5. " + new Date() + ": Callback<CharSequence>.get() returned \"" + asyncResult + "\"");
		    
		    // Ping test
		    System.out.println(new Date() + " : Pinging");
		    proxy.ping("Bob");
		    System.out.println(new Date() + " : Done pinging");

			client.close();
		} catch (IOException | InterruptedException | ExecutionException e2) {
			System.err.println("Error connecting to server ...");
			e2.printStackTrace(System.err);
			System.exit(1);
		}
	}
}
