import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.*;
import java.security.spec.*;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONObject;

/**
 *
 * @author metamug.com
 */
public class Main {

    private static final String SPEC = "secp256r1";
    private static final String ALGO = "SHA256withECDSA";

    private JSONObject sender() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, UnsupportedEncodingException, SignatureException, InvalidKeySpecException, NoSuchProviderException, InvalidParameterSpecException {

        ECGenParameterSpec ecSpec = new ECGenParameterSpec(SPEC);
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(ecSpec, new SecureRandom());
        KeyPair keypair = g.generateKeyPair();

        PublicKey publicKey = keypair.getPublic();
        PrivateKey privateKey = keypair.getPrivate();

        String pub =  Base64.getEncoder().encodeToString(publicKey.getEncoded());
        String priv = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        System.out.println("private: " + priv);
        System.out.println("public: " + pub);

        String plaintext = "CT60_DEMOTENANTTEST01";

        //...... sign
        Signature ecdsaSign = Signature.getInstance(ALGO);
        ecdsaSign.initSign(privateKey);
        ecdsaSign.update(plaintext.getBytes("UTF-8"));
        byte[] signature = ecdsaSign.sign();

        String sig = Base64.getEncoder().encodeToString(signature);
        System.out.printf("signature: %s",sig);

        JSONObject obj = new JSONObject();
        obj.put("publicKey", pub);
        obj.put("signature", sig);
        obj.put("message", plaintext);
        obj.put("algorithm", ALGO);

        return obj;
    }

    private boolean receiver(JSONObject obj) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException,
            UnsupportedEncodingException, SignatureException {

        Signature ecdsaVerify = Signature.getInstance(obj.getString("algorithm"));
        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(obj.getString("publicKey")));

        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);


        ecdsaVerify.initVerify(publicKey);
        ecdsaVerify.update(obj.getString("message").getBytes("UTF-8"));
        boolean result = ecdsaVerify.verify(Base64.getDecoder().decode(obj.getString("signature")));

        return result;
    }

    public static void subscribe(){
        MqttClient mqttClient = null;
        try {
            mqttClient = new MqttClient("tcp://onpremgibors.westus2.cloudapp.azure.com:1883", "clientId");

            MqttConnectOptions connOpts = new MqttConnectOptions();

            connOpts.setCleanSession(true);
            connOpts.setPassword("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImdpYm9ycy1rZXkifQ.eyJhdWQiOlsicmFiYml0bXEiLCJyYWJiaXRfY2xpZW50Il0sInNjb3BlIjpbInJhYmJpdG1xLnJlYWQ6Ki8qLyoiLCJyYWJiaXRtcS50YWc6YWRtaW5pc3RyYXRvciIsInJhYmJpdG1xLmNvbmZpZ3VyZToqLyovKiIsInJhYmJpdG1xLndyaXRlOiovKi8qIl0sImV4cCI6MTYwMjIwMjA1NX0.qw08HGsj-XX5rV26oBVD-K6FtXEBsuEPSl_enuhadNU".toCharArray());
            connOpts.setKeepAliveInterval(60 * 5); // connOpts.setKeepAliveInterval(60 * 5);
            mqttClient.connect(connOpts);

            mqttClient.setCallback(new MqttCallback() {

                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String time = new Timestamp(System.currentTimeMillis()).toString();
                    System.out.println("\nReceived a Message!" +
                            "\n\tTime:    " + time +
                            "\n\tTopic:   " + topic +
                            "\n\tMessage: " + new String(message.getPayload()) +
                            "\n\tQoS:     " + message.getQos() + "\n");
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
                    System.out.println("Connection to Solace broker lost!");

                }

                public void connectionLost(Throwable cause) {
                    System.out.println("Connection to Solace broker lost!" + cause.getMessage());
//                    latch.countDown();
                }
            });
            mqttClient.subscribe("devices/321eaaf4-1c98-4bc5-8fbb-e099df7cacd5/messages/devicebound/#", 1);
            System.out.println("subscribed");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sentDataIothub(){

        MqttClient mqttClient = null;
        try {
            mqttClient = new MqttClient("tcp://caidc-qaiothub.azure-devices.net:1883", "b4eab5a4-06b5-4bd6-9279-86cfa0a216f2");

            MqttConnectOptions connOpts = new MqttConnectOptions();

            connOpts.setCleanSession(true);
            connOpts.setPassword("".toCharArray());
            connOpts.setKeepAliveInterval(60 * 5); // connOpts.setKeepAliveInterval(60 * 5);
            mqttClient.connect(connOpts);

            mqttClient.setCallback(new MqttCallback() {

                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String time = new Timestamp(System.currentTimeMillis()).toString();
                    System.out.println("\nReceived a Message!" +
                            "\n\tTime:    " + time +
                            "\n\tTopic:   " + topic +
                            "\n\tMessage: " + new String(message.getPayload()) +
                            "\n\tQoS:     " + message.getQos() + "\n");
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
                    System.out.println("sent successfully!");

                }

                public void connectionLost(Throwable cause) {
                    System.out.println("Connection to Solace broker lost!" + cause.getMessage());
//                    latch.countDown();
                }
            });
            MqttMessage message = new MqttMessage("hello".getBytes());
            mqttClient.publish("devices/{device_id}/messages/events/", message);
            System.out.println("published ");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static byte[] compress(String data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length());
        GZIPOutputStream gzip = new GZIPOutputStream(bos);
        gzip.write(data.getBytes());
        gzip.close();
        byte[] compressed = bos.toByteArray();
        bos.close();
        return compressed;
    }

    public static void main(String[] args) throws NoSuchProviderException, InvalidParameterSpecException {
        subscribe();
//        try {
//            Main digiSig = new Main();
//            JSONObject obj = digiSig.sender();
//            boolean result = digiSig.receiver(obj);
//            System.out.println("");
//            System.out.println(result);
//        } catch (NoSuchAlgorithmException ex) {
//            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (InvalidAlgorithmParameterException ex) {
//            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (InvalidKeyException ex) {
//            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (UnsupportedEncodingException ex) {
//            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (SignatureException ex) {
//            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (InvalidKeySpecException ex) {
//            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
//        }
    }

}