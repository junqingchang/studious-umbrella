
/*
CP1: Client wants server's public key and make sure its the right one
Gets server cert
Apply CA public key to server cert, get server public key
Use server public key to encrypt message of max block length 117
Server use private key to decrypt

Cp2:
Client generate random symmetric private key Ks
Encrypt message with Ks
Encrypt Ks with server public key
send both encrypted message and encrypted symmetric key to server
Server use private key to decrypt and get Ks
Server use Ks to decrypt message

 */
import java.net.*;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import java.nio.file.Paths;
import javax.crypto.*;

import java.io.*;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

public class ClientWithSecurity {
    private static final String caCert = "CA.crt";

    public static void main(String[] args) {
        String filename = "test.txt";
        //String filename = "psLec6.pdf";
        //String encryptedName = "err.txt";
        Scanner sc = new Scanner(System.in);
        boolean flag = true;
        while (flag){
            System.out.println("Enter file to be sent to server (include ext): ");
            filename = sc.next();
            try{
                File file = new File(filename);
                flag = false;
            }catch (Exception ex){
                System.out.println("Invalid file name");
            }
        }

        int numBytes = 0;

        Socket clientSocket = null;

        DataOutputStream toServer = null;
        DataInputStream fromServer = null;

        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;

        long timeStarted = System.nanoTime();

        try {
            System.out.println("Establishing connection to server...");

            // Connect to server and get the input and output streams
            clientSocket = new Socket("localhost", 4321);
            toServer = new DataOutputStream(clientSocket.getOutputStream());
            fromServer = new DataInputStream(clientSocket.getInputStream());
            System.out.println("Establishing contact, Sending Request");

            //Sending greetings and waiting for signed reply
            toServer.writeInt(3);

            toServer.flush();
            //write random number generator
            Random rn = new Random();
            String hsMessage = Integer.toString(rn.nextInt());
            toServer.writeInt(hsMessage.getBytes().length);
            toServer.write(hsMessage.getBytes());
            toServer.flush();

            int messageCode = -1;
            //wait for reply
            while (messageCode != 3) {
                messageCode = fromServer.readInt();
            }
            int messageLen = fromServer.readInt();
            byte[] message = new byte[messageLen];
            fromServer.readFully(message, 0, messageLen);

            //Nonce


            //Request for Cert sign by CA
            System.out.println("Requesting for certificate");
            toServer.writeInt(4);
            toServer.flush();
            //wait for reply
            while (messageCode != 4) {
                messageCode = fromServer.readInt();
            }
            int certLen = fromServer.readInt();
            byte[] serverCertInBytes = new byte[certLen];
            fromServer.readFully(serverCertInBytes, 0, certLen);
            //certificate received in bytes
            System.out.println("Certificate received");

            //generating X509Certs from CA's cert and Server's cert, then verify them
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream input = new ByteArrayInputStream(serverCertInBytes);
            InputStream fis = new FileInputStream(caCert);
            X509Certificate caCertificate = (X509Certificate) cf.generateCertificate(fis);
            PublicKey caKey = caCertificate.getPublicKey();
            X509Certificate serverCert = null;
            try {
                serverCert = (X509Certificate) cf.generateCertificate(input);
                System.out.println("Checking validity");
                serverCert.checkValidity();
                serverCert.verify(caKey);
            } catch (Exception ex) {
                System.out.println("Certificate not yet valid/expire");
                toServer.writeInt(2);
                toServer.close();
                fromServer.close();
                clientSocket.close();
            }
            System.out.println("Certificate verified");

            //decrypt message send by server and checking if it matches the agreed message
            System.out.println("Checking Message");
            PublicKey serverPublicKey = serverCert.getPublicKey();
            String newhsMessage = decryptMessage(message, serverPublicKey);
            if (!newhsMessage.equals(hsMessage)) {
                System.out.println("Message does not match, closing connection");
                toServer.writeInt(2);
                toServer.close();
                fromServer.close();
                clientSocket.close();
            }

            //open the file
            //File file = new File(filename);
            //byte[] unencryptedFile = Files.readAllBytes(file.toPath());

            //SecretKey symKey = generateSymmetricKey();
            //Encrypt file with server public key
            //byte[] encryptedFile = encryptFile(unencryptedFile, serverPublicKey);
            //Files.write(Paths.get(encryptedName), encryptedFile);

            //fileInputStream = new FileInputStream(encryptedName);

            fileInputStream = new FileInputStream(filename);
            BufferedInputStream bufferedFileInputStream = new BufferedInputStream(fileInputStream);
            //bufferedInputStream = new BufferedInputStream(fileInputStream);

            byte[] fromFileBuffer = new byte[117];
            /*
            // Send the filename
            toServer.writeInt(0);
            toServer.writeInt(encryptedName.getBytes().length);
            toServer.write(encryptedName.getBytes());
            toServer.flush();
            */

            // Send the filename
            System.out.println("Sending file name");
            toServer.writeInt(0);
            //toServer.writeInt(filename.getBytes().length);
            toServer.writeInt(encryptFile(filename.getBytes(), serverPublicKey).length);
            toServer.write(encryptFile(filename.getBytes(), serverPublicKey));
            toServer.flush();


            // Send the file
            System.out.println("Sending file...");
            for (boolean fileEnded = false; !fileEnded;) {
                numBytes = bufferedFileInputStream.read(fromFileBuffer);
                fileEnded = numBytes < 117;

                /*
                if(numBytes<0){
                    fileEnded = false;
                    break;
                }*/
                if (fileEnded){
                    byte[] lastByteBlock = new byte[numBytes];
                    System.arraycopy(fromFileBuffer,0, lastByteBlock,0,numBytes);
                    fromFileBuffer = lastByteBlock;
                }
                byte[] encryptedFile = encryptFile(fromFileBuffer,serverPublicKey);

                //System.out.println(Arrays.toString(encryptedFile));
                toServer.writeInt(1);
                toServer.writeInt(encryptedFile.length); //originally numbbytes
                toServer.write(encryptedFile);
                //toServer.write(fromFileBuffer);
                toServer.flush();

                /* //Send the file: Method 2
                if(bufferedFileInputStream.available() >= 117){
                    numBytes = bufferedFileInputStream.read(fromFileBuffer);

                }else{
                    numBytes = bufferedFileInputStream.available();
                    byte[] lastByteBlock = new byte[numBytes];
                    System.arraycopy(fromFileBuffer,0, lastByteBlock,0,numBytes);
                    fromFileBuffer = lastByteBlock;
                    fileEnded = true;
                }
                toServer.writeInt(1);
                toServer.writeInt(sendFileInBytes.length);
                toServer.write(sendFileInBytes);
                toServer.flush();

                // Send the file: METHOD 3
                System.out.println("Sending file");
                File sendFile = new File(filename);
                byte[] sendFileInBytes = Files.readAllBytes(sendFile.toPath());
                toServer.writeInt(1);
                toServer.writeInt(sendFileInBytes.length);
                toServer.write(sendFileInBytes);
                toServer.flush();
                */

            }
            //bufferedInputStream.close();
            fileInputStream.close();
            toServer.writeInt(2);
            toServer.close();
            fromServer.close();
            //clientSocket.close();

        } catch (Exception ex) {
            ex.printStackTrace();

        }
        long timeTaken = System.nanoTime() - timeStarted;
        System.out.println("Program took: " + timeTaken / 1000000.0 + "ms to run");
    }

    public static String decryptMessage(byte[] encrypted, PublicKey key) {
        String output = null;
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(encrypted);

            output = new String(decrypted);
        } catch (Exception ex) {
            System.out.println("Error in decrypting");
        }
        return output;
    }

    public static byte[] encryptFile(byte[] fileInBytes, PublicKey key) {
        byte[] encryptedFile = null;
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            encryptedFile = cipher.doFinal(fileInBytes);
        } catch (Exception ex) {
            System.out.println("Error in encrypting");
            ex.printStackTrace();
        }

        return encryptedFile;
    }

    /*
    public static byte[] encryptTextInFile(byte[] text, PublicKey key){
        byte[] encryptedTextInFile = null;
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            int blockSize = 117;

        } catch (Exception e) {
            e.printStackTrace();
        }

    }*/

    public static SecretKey generateAESKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance( "AES" );
        generator.init(128);
        SecretKey key = generator.generateKey();
        return key;
    }
}