package network;

import java.io.FilterInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;


public class NetworkUtils {
	
	public static int getValidPortNumber(int portNumber) {
		while (portNumber < 7000){
	        try {
	        	ServerSocket temp = new ServerSocket(portNumber);
	            temp.close();
	            break;
	        } catch (IOException ex) {
	            portNumber++;
	        }
	    }
		return portNumber;
	}
	
	public static String askIPAddress() throws UnknownHostException {
		// Print the different IP addresses.
        Enumeration<NetworkInterface> nets;
		try {
			nets = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e) {
			return InetAddress.getLocalHost().getHostAddress();
		}
		List<NetworkInterface> netInts = Collections.list(nets);
        System.out.println("===============================================================");
        System.out.println("");
        for (int i = 0; i < netInts.size(); i++) {
        	NetworkInterface netint = netInts.get(i);
            System.out.printf("(" + i + ") Display name: %s\n", netint.getDisplayName());
        	System.out.printf("	Name: %s\n", netint.getName());
            List<InetAddress> inetList = Collections.list(netint.getInetAddresses());
            for (int j = 0; j < inetList.size(); j++)
            	System.out.printf("	(" + j + ") InetAddress: %s\n", inetList.get(j));
            System.out.printf("\n");
        }
        // Allow the user to choose IP address.
        Scanner scanner = new Scanner(new FilterInputStream(System.in) {
            @Override
            public void close() throws IOException {}
        });     
        System.out.println("===============================================================");
        System.out.println("Please choose the index of the IP address type you want to use.");
        while (true) {
            System.out.print("ChooseIPAddress> ");
            String nextLine = scanner.nextLine();
            int input = !nextLine.equals("") ? Integer.parseInt(nextLine) : -1;
            if (input >= 0 && input < netInts.size()) {
            	NetworkInterface netint = netInts.get(input);
            	System.out.println("Please choose the InetAddress index you want to use");
                System.out.print("ChooseIPAddress> ");
                List<InetAddress> inetList = Collections.list(netint.getInetAddresses());
                int inetAddressIndex = Integer.parseInt(scanner.nextLine());
                if (inetAddressIndex >= 0 && inetAddressIndex < inetList.size()) {
                    scanner.close();
                    System.out.println("===============================================================");
                	return inetList.get(inetAddressIndex).getHostAddress();
                }
            }
            System.out.println("That index is invalid, please try again");
        }	
	}

}
