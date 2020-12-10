package udp3;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;

public class UDPClient {


    public static void main(String[] args) throws IOException {

        while (true) {
            try {

                int serverPort = 8210; // Server port
                InetSocketAddress serverAddress = new InetSocketAddress(InetAddress.getByName("localhost"), serverPort); // Server Address
                SocketAddress routerAddr = new InetSocketAddress("localhost", 8080); // router

                UDPClient udpc = new UDPClient();
                Scanner keyboard = new Scanner(System.in);
                String clientRequest;
                System.out.print("\nWrite 'quit' in order to terminate process!\n\n");
                System.out.print("Please enter client POST/GET request or ");
                System.out.print("if you want to send SMTP request, please enter \"SMTP\" word: ");
                clientRequest = keyboard.nextLine();

                if (clientRequest.contains("get") || clientRequest.contains("GET")) {
                    udpc.getRequest(routerAddr, serverAddress, clientRequest);
                } else if (clientRequest.contains("post") || clientRequest.contains("POST")) {
                    udpc.postRequest(routerAddr, serverAddress, clientRequest);
                } else if(clientRequest.equalsIgnoreCase("smtp")){
                    DatagramChannel channel = DatagramChannel.open();
                    Packet p = new Packet.Builder()
                            .setType(5)
                            .setPortNumber(serverAddress.getPort())
                            .setPeerAddress(serverAddress.getAddress())
                            .setPayload(clientRequest.getBytes())
                            .create();
                    channel.send(p.toBuffer(), routerAddr);
                    ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                    SocketAddress router = channel.receive(buf);
                    buf.flip();
                    Packet resp = Packet.fromBuffer(buf);
                    String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
                    System.out.println("Server response: " + payload);
                    if (payload.contains("Ready") || payload.contains("ready") || payload.contains("READY")) {
                        udpc.SMTPRequest(serverAddress, routerAddr);
                    } else {
                        System.out.println("550 Invalid");
                        break;
                    }
                }else if(clientRequest.equalsIgnoreCase("quit")){
                    System.out.println("Client session is terminated");
                    break;
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
    }

    private void SMTPRequest(InetSocketAddress serverAddress, SocketAddress routerAddr) {
        String clientRequest;
        int sequenceNumber = 1;

        while (true) {
            Scanner keyboard = new Scanner(System.in);
            clientRequest = keyboard.nextLine();

            try (DatagramChannel channel = DatagramChannel.open()) {
                boolean booleanValue = false;
                sequenceNumber++;
                if (clientRequest.toUpperCase().startsWith("HELO")) {
                    // Creating packet
                    Packet p = new Packet.Builder()
                            .setType(6)
                            .setSequenceNumber(sequenceNumber)
                            .setPortNumber(serverAddress.getPort())
                            .setPeerAddress(serverAddress.getAddress())
                            .setPayload(clientRequest.getBytes())
                            .create();
                    channel.send(p.toBuffer(), routerAddr);
                    // The client names itself in HELO, to which the server normally gives a 250 Server hello to client response.

                    System.out.println("CLIENT: Sending HELO request to the router address: " + routerAddr);
                    waitAndGetServerResponse(channel);

                } else if (clientRequest.toUpperCase().startsWith("MAIL FROM:")) {
                    // Creating packet
                    Packet p = new Packet.Builder()
                            .setType(7)
                            .setSequenceNumber(sequenceNumber)
                            .setPortNumber(serverAddress.getPort())
                            .setPeerAddress(serverAddress.getAddress())
                            .setPayload(clientRequest.getBytes())
                            .create();
                    channel.send(p.toBuffer(), routerAddr);

                    // To send mail, the client issues MAIL FROM and normally gets a 250 Sender OK response.
                    System.out.println("CLIENT: Sending \"MAIL FROM\" request to the router address: " + routerAddr);
                    waitAndGetServerResponse(channel);

                } else if (clientRequest.toUpperCase().startsWith("RCPT TO:")) {
                // Creating packet
                Packet p = new Packet.Builder()
                        .setType(8)
                        .setSequenceNumber(sequenceNumber)
                        .setPortNumber(serverAddress.getPort())
                        .setPeerAddress(serverAddress.getAddress())
                        .setPayload(clientRequest.getBytes())
                        .create();
                channel.send(p.toBuffer(), routerAddr);

                // Recipients are named in RCPT TO, normally obtaining 250 Recipient OK responses.
                System.out.println("CLIENT: Sending \"RCPT TO\" request to the router address: " + routerAddr);
                waitAndGetServerResponse(channel);
            }  else if (clientRequest.toUpperCase().startsWith("DATA")) {
                // Creating packet
                Packet p = new Packet.Builder()
                        .setType(9)
                        .setSequenceNumber(sequenceNumber)
                        .setPortNumber(serverAddress.getPort())
                        .setPeerAddress(serverAddress.getAddress())
                        .setPayload(clientRequest.getBytes())
                        .create();
                channel.send(p.toBuffer(), routerAddr);

                // The client sends DATA to begin message transmission; a 354 Send mail response is expected from the server.
                System.out.println("CLIENT: Sending mail request to the router address: " + routerAddr);
                waitAndGetServerResponse(channel);
            }  else if (clientRequest.toUpperCase().startsWith("QUIT")) {
                // Creating packet
                Packet p = new Packet.Builder()
                        .setType(12)
                        .setSequenceNumber(sequenceNumber)
                        .setPortNumber(serverAddress.getPort())
                        .setPeerAddress(serverAddress.getAddress())
                        .setPayload(clientRequest.getBytes())
                        .create();
                channel.send(p.toBuffer(), routerAddr);

                // The client sends QUIT and the server responds with a 221 Server closing code.
                    waitAndGetServerResponse(channel);
                    System.out.println("SMTP CLIENT session is closed ");
                break;
            }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public String waitAndGetServerResponse(DatagramChannel channel) throws IOException {
        // Try to receive a packet within timeout.
        channel.configureBlocking(false);
        Selector selector = Selector.open();
        channel.register(selector, OP_READ);
        System.out.println("Waiting for the response... ");
        selector.select(15000);

        Set<SelectionKey> keys = selector.selectedKeys();

        // If there is no response within timeout
        if (keys.isEmpty()) {
            System.out.println("No response after timeout");
        } else {

            // We just want a single response.
            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
            SocketAddress router = channel.receive(buf);
            buf.flip();
            Packet resp = Packet.fromBuffer(buf);
            String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
            System.out.println("Server response: " + payload);
            if(resp.getType() == 10){
               waitAndGetServerResponse(channel);
            }
            return payload;
        }
        return null;
    }

    public void getRequest(SocketAddress routerAddr, InetSocketAddress serverAddress, String clientRequest) throws IOException {
        // Variables
        String clientPayload;
        String method = "GET";
        String url;
        String version = "HTTP\\1.0";
        String header = null;
        String[] splitRequest;
        int sequenceNumber = 1;
        int length;

        // Preparing http client request
        splitRequest = clientRequest.split(" ");
        length = splitRequest.length;

        url = splitRequest[length - 1];

        for (int i = 0; i < length; i++) {
            if (splitRequest[i].contains("-h")) {
                header = splitRequest[i + 1];
            }
        }

        // http client payload
        clientPayload = method + " " + url + " " + version + "\\" + header + "\\\\";

        //------------------ENTIRE GET REQUEST OPERATION------------------------
        try (DatagramChannel channel = DatagramChannel.open()) {
            boolean booleanValue = false;
            sequenceNumber++;

            // Three way handshake
            do {
                String msg = "SYN";

                // Creating packet
                Packet p = new Packet.Builder()
                        .setType(1)
                        .setSequenceNumber(sequenceNumber)
                        .setPortNumber(serverAddress.getPort())
                        .setPeerAddress(serverAddress.getAddress())
                        .setPayload(msg.getBytes())
                        .create();
                channel.send(p.toBuffer(), routerAddr);
                System.out.println("CLIENT: Sending SYN request to the router address: " + routerAddr);

                // Try to receive a packet within timeout.
                channel.configureBlocking(false);
                Selector selector = Selector.open();
                channel.register(selector, OP_READ);
                System.out.println("Waiting for the response... ");
                selector.select(15000);

                Set<SelectionKey> keys = selector.selectedKeys();

                // If there is no response within timeout
                if (keys.isEmpty()) {
                    System.out.println("No response after timeout");
                } else {

                    // We just want a single response.
                    ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                    SocketAddress router = channel.receive(buf);
                    buf.flip();
                    Packet resp = Packet.fromBuffer(buf);
                    String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);

                    System.out.println("CLIENT: Received SYN-ACK from server");
                    System.out.println();
                    System.out.println("CLIENT: Sending ACK and connected to server!");

                    if (resp.getType() == 2 && resp.getSequenceNumber() == sequenceNumber) {
                        booleanValue = true;
                    }
                    keys.clear();
                }

            } while (!booleanValue);
            //-------------------End of three way handshake-------------------


            // ------------------Sending Get Request--------------------------
            boolean booleanValue2 = false;

            do {
                sequenceNumber++;

                String message = AES.encrypt(clientPayload, "Burlacu");

                Packet p = new Packet.Builder()
                        .setType(0)
                        .setSequenceNumber(1L)
                        .setPortNumber(serverAddress.getPort())
                        .setPeerAddress(serverAddress.getAddress())
                        .setPayload(message.getBytes())
                        .create();
                channel.send(p.toBuffer(), routerAddr);

                System.out.println("CLIENT: Sending this message: " + clientPayload + " to the router address" + routerAddr);

                // Try to receive a packet within timeout.
                channel.configureBlocking(false);
                Selector selector = Selector.open();
                channel.register(selector, OP_READ);
                System.out.println("Waiting for the response....");
                System.out.println(" ");
                selector.select(15000);

                Set<SelectionKey> keys = selector.selectedKeys();

                if (keys.isEmpty()) {
                    System.out.println("No response after timeout");
                } else {
                    // We just want a single response.
                    ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                    SocketAddress router = channel.receive(buf);
                    buf.flip();
                    Packet resp = Packet.fromBuffer(buf);
                    String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);

                    System.out.println("Encrypted payload from server: " + payload);
                    System.out.println();
                    String decryptedPayload = AES.decrypt(payload, "Burlacu");
                    System.out.println("CLIENT: Payload: " + decryptedPayload);
                    System.out.println();

                    booleanValue2 = true;

                    keys.clear();
                }

            } while (!booleanValue2);


            // --------------Closing connection-------------------------

            boolean booleanValue3 = false;

            do {
                String msg = "FIN";
                sequenceNumber++;

                Packet p = new Packet.Builder()
                        .setType(3)
                        .setSequenceNumber(sequenceNumber)
                        .setPortNumber(serverAddress.getPort())
                        .setPeerAddress(serverAddress.getAddress())
                        .setPayload(msg.getBytes())
                        .create();
                channel.send(p.toBuffer(), routerAddr);
                System.out.println("CLIENT: Sending FIN request to the router address: " + routerAddr);

                // Try to receive a packet within timeout.
                channel.configureBlocking(false);
                Selector selector = Selector.open();
                channel.register(selector, OP_READ);
                System.out.println("Waiting for the response... ");
                selector.select(15000);

                Set<SelectionKey> keys = selector.selectedKeys();

                if (keys.isEmpty()) {
                    System.out.println("No response after timeout");
                } else {

                    // We just want a single response.
                    ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                    SocketAddress router = channel.receive(buf);
                    buf.flip();
                    Packet resp = Packet.fromBuffer(buf);
                    String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);

                    System.out.println("CLIENT: Received FIN-ACK from server and connected to server!");
                    System.out.println();

                    if (resp.getType() == 4 && resp.getSequenceNumber() == sequenceNumber) {
                        booleanValue3 = true;
                    }

                    keys.clear();
                }

            } while (!booleanValue3);
            //-------------END OF CLOSING CONNECTION-------------------

            System.out.println("END OF CLIENT");

        } // End Of Get Request
    }

    // POST REQUEST
    public void postRequest(SocketAddress routerAddr, InetSocketAddress serverAddress, String clientRequest) throws IOException {
        // Variables
        String clientPayload;
        String entityBody = null;
        String method = "POST";
        String url;
        String version = "HTTP\\1.0";
        String header = null;
        String[] splitRequest;
        int sequenceNumber = 1;
        int length;

        splitRequest = clientRequest.split(" ");
        length = splitRequest.length;

        url = splitRequest[length - 1];

        for (int i = 0; i < length; i++) {
            if (splitRequest[i].contains("-h")) {
                header = splitRequest[i + 1];
            }

            if (splitRequest[i].contains("-d")) {
                entityBody = splitRequest[i + 1];
            }
        }

        clientPayload = method + " " + url + " " + version + "\\" + header + "\\\\" + entityBody;

        // Sending to Server
        try (DatagramChannel channel = DatagramChannel.open()) {
            boolean booleanValue = false;
            sequenceNumber++;

            // Three way handshake
            do {
                String msg = "SYN";

                Packet p = new Packet.Builder()
                        .setType(1)
                        .setSequenceNumber(sequenceNumber)
                        .setPortNumber(serverAddress.getPort())
                        .setPeerAddress(serverAddress.getAddress())
                        .setPayload(msg.getBytes())
                        .create();
                channel.send(p.toBuffer(), routerAddr);
                System.out.println("CLIENT: Sending SYN request to the router address: " + routerAddr);

                // Try to receive a packet within timeout.
                channel.configureBlocking(false);
                Selector selector = Selector.open();
                channel.register(selector, OP_READ);
                System.out.println("Waiting for the response... ");
                selector.select(15000);

                Set<SelectionKey> keys = selector.selectedKeys();

                if (keys.isEmpty()) {
                    System.out.println("No response after timeout");
                } else {

                    // We just want a single response.
                    ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                    SocketAddress router = channel.receive(buf);
                    buf.flip();
                    Packet resp = Packet.fromBuffer(buf);
                    String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);

                    System.out.println("CLIENT: Received SYN-ACK from server");
                    System.out.println();
                    System.out.println("CLIENT: Sending ACK and connected to server!");

                    if (resp.getType() == 2 && resp.getSequenceNumber() == sequenceNumber) {
                        booleanValue = true;
                    }

                    keys.clear();
                }

            } while (!booleanValue);  // End of three way handshake


            // ------------------Sending Post Request----------------------------
            boolean booleanValue2 = false;
            sequenceNumber++;
            String message = AES.encrypt(clientPayload, "Burlacu");
            do {
                Packet p = new Packet.Builder()
                        .setType(0)
                        .setSequenceNumber(sequenceNumber)
                        .setPortNumber(serverAddress.getPort())
                        .setPeerAddress(serverAddress.getAddress())
                        .setPayload(message.getBytes())
                        .create();
                channel.send(p.toBuffer(), routerAddr);

                System.out.println("CLIENT: Sending this message: " + clientPayload + " to the router address" + routerAddr);

                // Try to receive a packet within timeout.
                channel.configureBlocking(false);
                Selector selector = Selector.open();
                channel.register(selector, OP_READ);
                System.out.println("Waiting for the response....");
                System.out.println(" ");
                selector.select(15000);

                Set<SelectionKey> keys = selector.selectedKeys();

                if (keys.isEmpty()) {
                    System.out.println("No response after timeout");
                } else {
                    // We just want a single response.
                    ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                    SocketAddress router = channel.receive(buf);
                    buf.flip();
                    Packet resp = Packet.fromBuffer(buf);
                    String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
                    System.out.println("Encrypted payload from server: " + payload);
                    System.out.println();
                    String decryptedPayload = AES.decrypt(payload, "Burlacu");
                    System.out.println("CLIENT: Payload: " + decryptedPayload);
                    System.out.println();

                    booleanValue2 = true;

                    keys.clear();
                }

            } while (!booleanValue2);
            // ------------------END of Sending Post Request-------------------


            // ---------------------Closing connection-------------------------
            boolean booleanValue3 = false;
            sequenceNumber++;

            do {
                String msg = "FIN";

                Packet p = new Packet.Builder()
                        .setType(3)
                        .setSequenceNumber(sequenceNumber)
                        .setPortNumber(serverAddress.getPort())
                        .setPeerAddress(serverAddress.getAddress())
                        .setPayload(msg.getBytes())
                        .create();
                channel.send(p.toBuffer(), routerAddr);
                System.out.println("CLIENT: Sending FIN request to the router address: " + routerAddr);

                // Try to receive a packet within timeout.
                channel.configureBlocking(false);
                Selector selector = Selector.open();
                channel.register(selector, OP_READ);
                System.out.println("Waiting for the response... ");
                selector.select(15000);

                Set<SelectionKey> keys = selector.selectedKeys();

                if (keys.isEmpty()) {
                    System.out.println("No response after timeout");
                } else {

                    ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                    SocketAddress router = channel.receive(buf);
                    buf.flip();
                    Packet resp = Packet.fromBuffer(buf);
                    String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);

                    System.out.println("CLIENT: Received FIN-ACK from server and connected to server!");
                    System.out.println();

                    if (resp.getType() == 4 && resp.getSequenceNumber() == sequenceNumber) {
                        booleanValue3 = true;
                    }

                    keys.clear();
                }

            } while (!booleanValue3);
            // ---------------------END Of Closing connection-----------------------

            System.out.println("END OF CLIENT");

        }

    } // End of post request

}