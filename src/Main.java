import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.*;
import java.security.spec.*;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
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

        String plaintext = "CT60_CMDTESTGIB01";

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

    public static void subscribeIotHub(){
        MqttClient mqttClient = null;
        try {
            mqttClient = new MqttClient("ssl://caidc-qaiothub.azure-devices.net:8883", "7b1586db-9a59-4bf3-942d-a1a850ddce73");

            MqttConnectOptions connOpts = new MqttConnectOptions(); // 83ebb1e7-f5d3-4c8d-8b4a-4344179bbd91 //mydevice: 7b1586db-9a59-4bf3-942d-a1a850ddce73
            connOpts.setUserName("caidc-qaiothub.azure-devices.net/7b1586db-9a59-4bf3-942d-a1a850ddce73/?api-version=2018-06-30");
            connOpts.setCleanSession(true);
            connOpts.setPassword("SharedAccessSignature sr=caidc-qaiothub.azure-devices.net%2Fdevices%2F&sig=9dAgbJG7z1sUIlZKfPPDj55dcngaaCWfy%2FCDF%2BqBy9M%3D&se=1604678166&skn=device".toCharArray());
            connOpts.setKeepAliveInterval(60 * 5);
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
                    System.out.println("Delivered event !");

                }

                public void connectionLost(Throwable cause) {
                    System.out.println("Connection to Solace broker lost!" + cause.getMessage());
                }
            });
            mqttClient.subscribe("devices/7b1586db-9a59-4bf3-942d-a1a850ddce73/messages/devicebound/#", 1);
            System.out.println("subscribed");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendNewConnectionEventIotHub(byte[] message){
        MqttClient mqttClient = null;
        try {
            mqttClient = new MqttClient("ssl://caidc-qaiothub.azure-devices.net:8883", "7b1586db-9a59-4bf3-942d-a1a850ddce73");

            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setUserName("caidc-qaiothub.azure-devices.net/7b1586db-9a59-4bf3-942d-a1a850ddce73/?api-version=2018-06-30");
            connOpts.setCleanSession(true);
            connOpts.setPassword("SharedAccessSignature sr=caidc-qaiothub.azure-devices.net%2Fdevices%2F&sig=9dAgbJG7z1sUIlZKfPPDj55dcngaaCWfy%2FCDF%2BqBy9M%3D&se=1604678166&skn=device".toCharArray());
            connOpts.setKeepAliveInterval(60 * 5);

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
                    System.out.println("Delivered newconnection event !");

                }

                public void connectionLost(Throwable cause) {
                    System.out.println("Connection to Solace broker lost!" + cause.getMessage());
                }
            });
            MqttMessage mqttMessage = new MqttMessage(message);
            mqttMessage.setQos(1);
            mqttMessage.setRetained(false);
            mqttClient.publish("devices/7b1586db-9a59-4bf3-942d-a1a850ddce73/messages/events/", mqttMessage);
            System.out.println("published");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void subscribeOnPrem(){
        MqttClient mqttClient = null;

        try { //wss://onpremgibors.westus2.cloudapp.azure.com:15679/ws
            //tcp://onpremgibors.westus2.cloudapp.azure.com:1883
            mqttClient = new MqttClient("tcp://tempoisunil45fta.westeurope.cloudapp.azure.com:1883", "clientId");

            MqttConnectOptions connOpts = new MqttConnectOptions();

            connOpts.setCleanSession(true);
            connOpts.setPassword("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IjEyMy1rZXkifQ.eyJhdWQiOlsicmFiYml0bXEiLCJyYWJiaXRfY2xpZW50Il0sInNjb3BlIjpbInJhYmJpdG1xLnJlYWQ6Ki8qIiwicmFiYml0bXEudGFnOmFkbWluaXN0cmF0b3IiLCJyYWJiaXRtcS5jb25maWd1cmU6Ki8qLyoiLCJyYWJiaXRtcS53cml0ZToqLyovKiJdLCJleHAiOjE2MDQ1NDY1Mzl9._8gs9jKRHX2QJhlKt14LC9hBfhdbEmnh5v84bTBn2KI".toCharArray());
            connOpts.setKeepAliveInterval(600 * 5); // connOpts.setKeepAliveInterval(60 * 5);
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
                }
            });
            mqttClient.subscribe("devices/628344b7-396c-4041-b95f-1d2a8284aff7/messages/devicebound", 1);
            System.out.println("subscribed");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static byte[] getNewConnection(String serialNumber) throws IOException {
        String event = "{\"Events\":[{\"BodyProperties\":[{\"Key\":\"batteryserialnumber\",\"Value\":\"PEPA162000502333\"},{\"Key\":\"BluetoothAddress\",\"Value\":\"00:10:20:CD:02:C2\"},{\"Key\":\"configurationnumber\",\"Value\":\"CT60-L1N-ASC210E\"},{\"Key\":\"securitypatchlevel\",\"Value\":\"2019-05-01\"},{\"Key\":\"CurrentWirelessConnection\",\"Value\":\"None\"},{\"Key\":\"WiFiMacAddress\",\"Value\":\"00:10:20:cd:02:c1\"},{\"Key\":\"timestamp\",\"Value\":\"2020-05-25T08:24:05.570+00:00\"},{\"Key\":\"ipaddress\",\"Value\":\"192.168.0.4\"},{\"Key\":\"deviceuniqueid\",\"Value\":\"CT60CMDTESTGIB01\"},{\"Key\":\"Imei\",\"Value\":\"Unavailable\"},{\"Key\":\"Meid\",\"Value\":\"Unavailable\"},{\"Key\":\"family\",\"Value\":\"Dubai\"},{\"Key\":\"BatteryLevel\",\"Value\":\"100\"},{\"Key\":\"name\",\"Value\":\"CT60CMDTESTGIB01\"},{\"Key\":\"gatewayid\",\"Value\":\"\"},{\"Key\":\"osinfo\",\"Value\":\"Android 7.1.1\"},{\"Key\":\"type\",\"Value\":\"MobileComputer\"},{\"Key\":\"connectedagentversion\",\"Value\":\"5.10.11.0046\"},{\"Key\":\"model\",\"Value\":\"CT60\"},{\"Key\":\"firmwareversion\",\"Value\":\"84.00.19-(0169)\"}],\"CreatedTime\":\"2020-05-25T13:59:18.838+05:30\",\"CreatorType\":\"EBICC R1\",\"EventType\":\"newconnectionevent\",\"GeneratorType\":\"CT60_CMDTESTGIB01\",\"Id\":\"1a3b34d1-f2c8-40a5-9261-496633569c21\",\"TargetId\":\"16:79:50:B2:6F:86\"}]}\n";
        System.out.println(event);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(event.length());
        GZIPOutputStream gzip = new GZIPOutputStream(bos);
        gzip.write(event.getBytes());
        gzip.close();
        byte[] compressed = bos.toByteArray();
        bos.close();
        return compressed;
    }

    public static void generateKeys() throws NoSuchProviderException, InvalidParameterSpecException {
        try {
            Main digiSig = new Main();
            JSONObject obj = digiSig.sender();
            boolean result = digiSig.receiver(obj);
            System.out.println("");
            System.out.println(result);
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidAlgorithmParameterException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidKeyException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SignatureException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidKeySpecException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void main(String[] args) throws NoSuchProviderException, InvalidParameterSpecException, IOException {
//        generateKeys();

        // Send event to iothub
        byte[] message = getNewConnection("CT60CMDTESTGIB01");
        sendNewConnectionEventIotHub(message);

        // Subscribe to topic Iothub
        subscribeIotHub();
        // subscribe onprem
//        subscribeOnPrem();
    }

}