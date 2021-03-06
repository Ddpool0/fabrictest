package config;

import entity.TestOrg;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.hyperledger.fabric.sdk.helper.Config;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.Security;
import java.util.*;

import static java.lang.String.format;

//配置类，使用单例模式。
public class TestUtils {

    //实例变量
    private static TestUtils testUtils;

    //设置连接的ip
    private static final String LOCALHOST = "192.168.197.124";

    //设置组织配置前缀名
    private static final String INTEGRATIONTESTS_ORG = "hyperledger.fabric.org.";

    //类似配置文件
    private static final Properties sdkProperties = new Properties();

    //组织信息
    private final HashMap<String, TestOrg> testOrgs = new HashMap<String, TestOrg>();

    //没有这个静态快，转换PrivateKey时会报错
    //error：java.security.NoSuchProviderException: no such provider: BC
    static {
        try {
            Security.addProvider(new BouncyCastleProvider());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //单例模式，私有构造函数，在此函数中配置组织信息。
    private TestUtils() {
        //添加配置
        sdkProperties.put("hyperledger.fabric.InvokeWaitTime", "32000");
        sdkProperties.put("hyperledger.fabric.DeployWaitTime", "120000");
        sdkProperties.put("hyperledger.fabric.ProposalWaitTime", "120000");
        sdkProperties.put("hyperledger.fabric.RunIdemixMTTest", "false");

        //配置组织org1的mspid
        sdkProperties.put(INTEGRATIONTESTS_ORG + "peerOrg1.mspid", "Org1MSP");
        //配置组织org1的域名
        sdkProperties.put(INTEGRATIONTESTS_ORG + "peerOrg1.domname", "org1.example.com");
        //配置组织org1的ca的地址
        sdkProperties.put(INTEGRATIONTESTS_ORG + "peerOrg1.ca_location", "http://" + LOCALHOST + ":7054");
        //配置组织org1的ca的name
        sdkProperties.put(INTEGRATIONTESTS_ORG + "peerOrg1.caName", "ca0");
        //配置组织org1的所有peer的地址
        sdkProperties.put(INTEGRATIONTESTS_ORG + "peerOrg1.peer_locations", "peer0.org1.example.com@grpc://" + LOCALHOST + ":7051, peer1.org1.example.com@grpc://" + LOCALHOST + ":7056");
        //配置组织org2的orderer的地址
        sdkProperties.put(INTEGRATIONTESTS_ORG + "peerOrg1.orderer_locations", "orderer.example.com@grpc://" + LOCALHOST + ":7050");

        sdkProperties.put(INTEGRATIONTESTS_ORG + "peerOrg2.mspid", "Org2MSP");
        sdkProperties.put(INTEGRATIONTESTS_ORG + "peerOrg2.domname", "org2.example.com");
        sdkProperties.put(INTEGRATIONTESTS_ORG + "peerOrg2.ca_location", "http://" + LOCALHOST + ":8054");
        sdkProperties.put(INTEGRATIONTESTS_ORG + "peerOrg2.peer_locations", "peer0.org2.example.com@grpc://" + LOCALHOST + ":8051,peer1.org2.example.com@grpc://" + LOCALHOST + ":8056");
        sdkProperties.put(INTEGRATIONTESTS_ORG + "peerOrg2.orderer_locations", "orderer.example.com@grpc://" + LOCALHOST + ":7050");

        //根据配置添加组织对象
        testOrgs.put("peerOrg1", new TestOrg("peerOrg1", "Org1MSP"));
        testOrgs.put("peerOrg2", new TestOrg("peerOrg2", "Org2MSP"));

        //根据配置设置组织对象的属性
        for (Map.Entry<String, TestOrg> org : testOrgs.entrySet()) {
            final TestOrg testOrg = org.getValue();
            final String orgName = org.getKey();

            //设置组织下所有节点的name和地址。
            String peerNames = sdkProperties.getProperty(INTEGRATIONTESTS_ORG + orgName + ".peer_locations");
            //根据“，”分开
            String[] ps = peerNames.split("[ \t]*,[ \t]*");
            for (String peer : ps) {
                //根据“@”分开
                String[] nl = peer.split("[ \t]*@[ \t]*");
                testOrg.addPeerLocation(nl[0], nl[1]);
            }

            //设置组织域名
            final String domainName = sdkProperties.getProperty(INTEGRATIONTESTS_ORG + orgName + ".domname");
            testOrg.setDomainName(domainName);

            //设置组织所有orderer的名字和域名
            String ordererNames = sdkProperties.getProperty(INTEGRATIONTESTS_ORG + orgName + ".orderer_locations");
            ps = ordererNames.split("[ \t]*,[ \t]*");
            for (String peer : ps) {
                String[] nl = peer.split("[ \t]*@[ \t]*");
                testOrg.addOrdererLocation(nl[0], nl[1]);
            }

            //设置组织CA的地址
            testOrg.setCaLocation(sdkProperties.getProperty((INTEGRATIONTESTS_ORG + org.getKey() + ".ca_location")));

            //设置组织CA的name
            testOrg.setCaName(sdkProperties.getProperty((INTEGRATIONTESTS_ORG + org.getKey() + ".caName")));
        }
    }

    //单例模式，对外提供获取实例接口，这里使用的是懒汉式，线程不安全。
    public static TestUtils getConfig() {
        if (null == testUtils) {
            testUtils = new TestUtils();
        }
        return testUtils;
    }

    //重新配置SDK提供的config，理解是初始化配置信息。
    public static void resetConfig() {
        try {
            //获取指定名称的属性
            final Field field = Config.class.getDeclaredField("config");
            //设置访问权限，原来属性为private
            field.setAccessible(true);
            //将此属性设值为null，单例模式实例为null，获取实例时重新实例化
            field.set(Config.class, null);
            //重新实例化，属性config重新赋值，相当于配置初始化。
            Config.getConfig();
        } catch (Exception e) {
            throw new RuntimeException("Cannot reset config", e);
        }
    }

    //获取配置好的组织集合
    public Collection<TestOrg> getTestOrgs() {
        return Collections.unmodifiableCollection(testOrgs.values());
    }

    //获取结尾为_sk的文件
    public static File findFileSK(File directory) {
        //刷选以“_sk”结尾的文件
        File[] matches = directory.listFiles((dir, name) -> name.endsWith("_sk"));

        if (null == matches) {
            throw new RuntimeException(format("Matches returned null does %s directory exist?", directory.getAbsoluteFile().getName()));
        }

        if (matches.length != 1) {
            throw new RuntimeException(format("Expected in %s only 1 sk file but found %d", directory.getAbsoluteFile().getName(), matches.length));
        }

        return matches[0];
    }

    //转换成PrivateKey类型
    public static PrivateKey getPrivateKeyFromBytes(byte[] date) throws IOException {

        final Reader pemReader = new StringReader(new String(date));

        final PrivateKeyInfo pemPair;

        try (PEMParser pemParser = new PEMParser(pemReader)) {
            pemPair = (PrivateKeyInfo) pemParser.readObject();
        }

        PrivateKey privateKey = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getPrivateKey(pemPair);
        return privateKey;
    }

    //根据指定名称获取org
    public TestOrg getTestOrgByName(String name) {
        return testOrgs.get(name);
    }

    //获取指定类型的配置信息
    public Properties getEndPointProperties(final String type, final String name) {
        Properties ret = new Properties();

        final String domainName = getDomainName(name);

        //获取orderer的tls的server.crt证书
        File cert = Paths.get("src\\test\\resources\\crypto-config\\", "ordererOrganizations".replace("orderer", type), domainName, type + "s", name, "tls/server.crt").toFile();

        if (!cert.exists()) {
            throw new RuntimeException(String.format("Missing cert file for: %s. Could not find at location: %s", name,
                    cert.getAbsolutePath()));
        }

        File clientCert;
        File clientKey;

        if ("orderer".equals(type)) {
            clientCert = Paths.get("src\\test\\resources\\crypto-config\\", "ordererOrganizations\\example.com\\users\\Admin@example.com\\tls\\client.crt").toFile();
            clientKey = Paths.get("src\\test\\resources\\crypto-config\\", "ordererOrganizations\\example.com\\users\\Admin@example.com\\tls\\client.key").toFile();
        } else {
            clientCert = Paths.get("src\\test\\resources\\crypto-config\\", "peerOrganizations", domainName, "users\\User1@" + domainName, "tls\\client.crt").toFile();
            clientKey = Paths.get("src\\test\\resources\\crypto-config\\", "peerOrganizations", domainName, "users\\User1@" + domainName, "tls\\client.key").toFile();
        }

        if (!clientCert.exists()) {
            throw new RuntimeException(String.format("Missing  client cert file for: %s. Could not find at location: %s", name,
                    clientCert.getAbsolutePath()));
        }

        if (!clientKey.exists()) {
            throw new RuntimeException(String.format("Missing  client key file for: %s. Could not find at location: %s", name,
                    clientKey.getAbsolutePath()));
        }

        ret.setProperty("clientCertFile", clientCert.getAbsolutePath());
        ret.setProperty("clientKeyFile", clientKey.getAbsolutePath());

        ret.setProperty("pemFile", cert.getAbsolutePath());

        ret.setProperty("hostnameOverride", name);
        ret.setProperty("sslProvider", "openSSL");
        ret.setProperty("negotiationType", "TLS");

        return ret;
    }

    //获取指定name的Orderer配置信息
    public Properties getOrdererProperties(String name) {
        return getEndPointProperties("orderer", name);
    }

    //获取指定name的Peer配置信息
    public Properties getPeerProperties(String name){
        return getEndPointProperties("peer",name);
    }

    //例如将orderer.example.com切成example.com
    private String getDomainName(final String name) {
        int dot = name.indexOf(".");
        if (-1 == dot) {
            return null;
        } else {
            return name.substring(dot + 1);
        }
    }
}
