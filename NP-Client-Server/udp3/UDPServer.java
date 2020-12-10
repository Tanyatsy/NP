package udp3;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.Random;
import java.util.Scanner;

import static java.nio.charset.StandardCharsets.UTF_8;

public class UDPServer {

    // HTTP request
    static String method;
    static String url;
    static String requestVersion;
    static String requestHeader;
    static String requestEntityBody;

    // HTTP response
    static String responseVersion = "HTTP\\1.0";
    static String statusCode;
    static String phrase;
    static String responseHeader;
    static String responseEntityBody;
    static String serverResponse;

    // Get File method
    public static void getFileResponse(String directory) {

        File fileDirectory = new File(directory);
        File[] listFiles = fileDirectory.listFiles();

        for (File file : listFiles) {
            if (file.isFile()) {
                responseEntityBody += "\n" + file.getName() + "\n";
            }
        }

        statusCode = "200";
        phrase = "OK";
        responseHeader = requestHeader;
    }

    // ---------------------------SUPPORTING METHODS-----------------------------------

    // Get method
    public static void getResponse(String directory, String url) {

        System.out.println("Checking if file exists...");
        String filePath = directory +"\\"+ url;
        File file = new File(filePath);

        // Check if file exists in the directory
        if (file.exists()) {
            try {
                System.out.println("Trying to read from files...");
                readFile(filePath);

            } catch (Exception e) {
                e.getMessage();
            }
        } else {
            statusCode = "404";
            phrase = "Not Found";
        }
    }

    // Post method
    public static void postResponse(String directory, String url, String requestEntityBody) {
        String filePath = directory +"\\"+ url;
        File file = new File(filePath);

        if (file.exists()) {

            PrintWriter pw = null;

            try {
                pw = new PrintWriter(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            pw.print(requestEntityBody);
            pw.close();

            statusCode = "200";
            phrase = "OK";
            responseHeader = requestHeader + "\\" + "Content-length: " + requestEntityBody.length() + "\\" + "Content-Type: text\\html";
            responseEntityBody = requestEntityBody;
        } else {

            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

            PrintWriter pw = null;

            try {
                pw = new PrintWriter(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            pw.print(requestEntityBody);
            pw.close();

            statusCode = "201";
            phrase = "Created";
            responseHeader = requestHeader + "\\" + "Content-length: " + requestEntityBody.length() + "\\" + "Content-Type: text\\html";
            responseEntityBody = requestEntityBody;
        }

    }

    // Reads the selected file
    public static void readFile(String filePath) {
        System.out.println("Reading from the file...");

        Scanner keyboard = null;
        String text = "";

        try {
            keyboard = new Scanner(new File(filePath));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        while (keyboard.hasNext()) {
            text += keyboard.nextLine() + "\n";
        }

        statusCode = "200";
        phrase = "OK";
        responseEntityBody = text;
        responseHeader = requestHeader + "\\" + "Content-Length:" + responseEntityBody.length() + "\\" + "Content-Type: text\\html";
    }


    public static void reset() {
        // HTTP request
        method = null;
        url = null;
        requestVersion = null;
        requestHeader = null;
        requestEntityBody = null;

        // HTTP response
        responseVersion = "HTTP\\1.0";
        statusCode = null;
        phrase = null;
        responseHeader = null;
        responseEntityBody = null;
        serverResponse = null;
    }

    public void listenAndServe( int port, String directory) throws IOException, InterruptedException {
        boolean isQuit = true;
        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(new InetSocketAddress(port));

            System.out.println("" + channel.getLocalAddress());

            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

        while (isQuit) {
            buf.clear();
                SocketAddress router = channel.receive(buf);

                // Parse a packet from the received raw data.
                buf.flip();
                Packet packet = Packet.fromBuffer(buf);
                buf.flip();

                // DATA REQUEST
            switch (packet.getType()) {
                case 0: {
                    reset();

                    String[] splitClientPayload;
                    String[] splitClientPayload2;

                    String payload = new String(packet.getPayload(), UTF_8);
                    String decryptedPayload = AES.decrypt(payload, "Burlacu");
                    System.out.println("Encrypted payload from client: " + payload);
                    System.out.println(" ");
                    System.out.println("Decrypted payload from client: " + decryptedPayload);
                    splitClientPayload = decryptedPayload.split(" ");
                    splitClientPayload2 = splitClientPayload[2].split("\\\\");

                    method = splitClientPayload[0];
                    url = splitClientPayload[1];
                    requestHeader = splitClientPayload2[2];

                    if (method.equals("get") || method.equals("GET")) {
                        if (url.equals("/") & url.length() == 1) {
                            getFileResponse(directory);
                        } else {
                            getResponse(directory, url);
                        }
                    } else {
                        if (splitClientPayload2.length > 3) {
                            requestEntityBody = splitClientPayload2[4];
                        }
                        postResponse(directory, url, requestEntityBody);
                    }

                    serverResponse = responseVersion + " " + statusCode + " " + phrase + "\\" + responseHeader + "\\" + "\\" + responseEntityBody;

                    System.out.println("SERVER: Sending this message to client: " + serverResponse);
                    String encryptedServerResp = AES.encrypt(serverResponse, "Burlacu");
                    Packet resp = packet.toBuilder()
                            .setPayload(encryptedServerResp.getBytes())
                            .create();
                    channel.send(resp.toBuffer(), router);
                    break;
                }
                 // CONNECTION REQUEST
                case 1: {
                    System.out.println("SERVER: Sending back syn ack");
                    Packet resp = packet.toBuilder()
                            .setType(2)
                            .create();
                    channel.send(resp.toBuffer(), router);
                    break;
                }

                // CLOSING REQUEST
                case 3: {
                    System.out.println("SERVER: Sending back FYN-ACK");
                    Packet resp = packet.toBuilder()
                            .setType(4)
                            .create();
                    channel.send(resp.toBuffer(), router);
                    break;
                }
                // SMTP READY RESPONSE
                case 5: {
                    String serverResp = "220 Server ready";
                    Packet resp = packet.toBuilder()
                            .setPayload(serverResp.getBytes())
                            .create();
                    channel.send(resp.toBuffer(), router);
                    break;
                }
                // SMTP HELO RESPONSE
                case 6: {
                    String serverResp = "250 Server hello to client";
                    Packet resp = packet.toBuilder()
                            .setPayload(serverResp.getBytes())
                            .create();
                    channel.send(resp.toBuffer(), router);
                    break;
                }
                case 7: {
                    // SMTP SENDER OK
                    reset();
                    String payload = new String(packet.getPayload(), UTF_8);
                    if (payload.startsWith("MAIL FROM:")) {
                        SMTPSender.from = payload.substring(payload.indexOf(":") + 1).trim();
                    }
                    String serverResp;
                    if(payload.contains("@") && payload.contains(".")) {
                         serverResp = "250 Sender OK";
                    }else {
                        serverResp = "501 Sender is not valid";
                    }
                    Packet resp = packet.toBuilder()
                            .setPayload(serverResp.getBytes())
                            .create();
                    channel.send(resp.toBuffer(), router);
                    break;
                }
                case 8: {
                    // SMTP RECIPIENT OK
                    reset();
                    String payload = new String(packet.getPayload(), UTF_8);
                    if (payload.startsWith("RCPT TO:")) {
                        SMTPSender.to = payload.substring(payload.indexOf(":") + 1).trim();
                    }
                    String serverResp;
                    if(payload.contains("@") && payload.contains(".")) {
                        serverResp = "250 Recipient OK";
                    }else {
                        serverResp = "501 Recipient is not valid";
                    }
                    Packet resp = packet.toBuilder()
                            .setPayload(serverResp.getBytes())
                            .create();
                    channel.send(resp.toBuffer(), router);
                    break;
                }
                case 9: {
                    // SMTP SEND EMAIL
                    reset();
                    String payload = new String(packet.getPayload(), UTF_8);
                    if (payload.startsWith("DATA")) {
                        SMTPSender.sendEmail();
                    }
                    long generatedLong = new Random().nextLong();
                    String serverResp = "354 Send mail with ID: " + String.valueOf(generatedLong);
                    Packet resp = packet.toBuilder()
                            .setPayload(serverResp.getBytes())
                            .setType(10)
                            .create();
                    channel.send(resp.toBuffer(), router);
                    Thread.sleep(500);
                    String serverResponseAfterSend = "250 Message accepted";
                    Packet resp1 = packet.toBuilder()
                            .setType(11)
                            .setPayload(serverResponseAfterSend.getBytes())
                            .create();
                    channel.send(resp1.toBuffer(), router);
                    break;
                }
                case 12: {
                    // SMTP CLOSE SERVER
                    reset();
                    String serverResp = "221 Server closing";
                    Packet resp = packet.toBuilder()
                            .setPayload(serverResp.getBytes())
                            .create();
                    channel.send(resp.toBuffer(), router);
                    isQuit = false;
                    break;
                }
            }
            }
        }
    }
}