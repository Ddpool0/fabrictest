package main;

import config.TestUtils;
import entity.TestEnrollment;
import entity.TestOrg;
import entity.TestUser;
import org.apache.commons.io.IOUtils;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.HFCAInfo;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.hyperledger.fabric_ca.sdk.exception.EnrollmentException;
import org.hyperledger.fabric_ca.sdk.exception.InfoException;
import org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static org.junit.Assert.assertNotNull;

public class TestFabric {
    //获取配置实例，单例模式
    private static final TestUtils testConfig = TestUtils.getConfig();

    //组织信息集合
    private Collection<TestOrg> testOrgs;

    //初始化配置信息，为每个配置好的组织设置HFCAClien实例
    @Before
    public void checkConfig() throws MalformedURLException, InvalidArgumentException {
        System.out.println("---------------------checkConfig begin---------------------");
        //初始化sdk提供的config信息，配置整体的fabric
        TestUtils.resetConfig();

        //获取配置好的组织信息
        testOrgs = testConfig.getTestOrgs();

        //为每一个组织设值CA实例
        for (TestOrg testOrg : testOrgs) {
            //获取配置好的CA名称
            String caName = testOrg.getCaName();
            if (caName != null && !caName.isEmpty()) {
                testOrg.setHfcaClient(HFCAClient.createNewInstance(caName, testOrg.getCaLocation(), testOrg.getCaProperties()));
            } else {
                testOrg.setHfcaClient(HFCAClient.createNewInstance(testOrg.getCaLocation(), testOrg.getCaProperties()));
            }
        }
        System.out.println("---------------------checkConfig end---------------------");
    }

    @Test
    public void setup() throws Exception {

        //设置用户，并注册与登记。
        enrollUsers(testOrgs);
        //创建channel，peer加入，执行chaincode
        runFabricTest();
    }

    public void runFabricTest() throws Exception {
        //实例化fabric的Client
        HFClient client = HFClient.createNewInstance();

        //设置加密工具
        client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

        //获取指定name组织
        TestOrg testOrg = testConfig.getTestOrgByName("peerOrg1");

        //创建channel
        Channel fooChannel = constructChannel("foo", client, testOrg);
    }

    /**
     * 创建channel
     *
     * @param name    cahnnel名字
     * @param client  HFClient
     * @param testOrg org
     * @return Channel实例
     */
    public Channel constructChannel(String name, HFClient client, TestOrg testOrg) throws Exception {

        TestUser peerAdmin = testOrg.getPeerAdmin();
        client.setUserContext(peerAdmin);

        Collection<Orderer> orderers = new LinkedList<>();

        for (String orderName : testOrg.getOrdererNames()) {
            //获取fabric的orderer配置信息
            Properties ordererProperties = testConfig.getOrdererProperties(orderName);

            //设置grpc的keepAlive，避免timeout
            ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[]{5L, TimeUnit.MINUTES});
            ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[]{8L, TimeUnit.SECONDS});
            ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveWithoutCalls", new Object[]{true});

            //调用clien的newOrderer接口，表示一个fabric的orderer。
            orderers.add(client.newOrderer(orderName, testOrg.getOrdererLocation(orderName), ordererProperties));
        }

        //选择第一个orderer创建channel
        Orderer anOrderer = orderers.iterator().next();
        orderers.remove(anOrderer);

        //通道配置文件路径
        String path = "src\\test\\resources\\" + name + ".tx";
        ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(path));

        //配置信息需要peerAdmin签名,生成Channel
        Channel newChannel = client.newChannel(name, anOrderer, channelConfiguration, client.getChannelConfigurationSignature(channelConfiguration, peerAdmin));

        System.out.println("Create channel " + name);

        //将peer加入到channel中
        for (String peerName : testOrg.getPeerNames()) {
            //peer的连接地址
            String peerLocation = testOrg.getPeerLocation(peerName);
            //peer的配置信息
            Properties peerProperties = testConfig.getPeerProperties(peerName);
            if (null == peerProperties) {
                peerProperties = new Properties();
            }

            peerProperties.put("grpc.NettyChannelBuilderOption.maxInboundMessageSize", 9000000);

            //实例化Peer
            Peer peer = client.newPeer(peerName, peerLocation, peerProperties);

            //join实例化的peer到channel中，设置peer拥有的角色
            newChannel.joinPeer(peer, Channel.PeerOptions.createPeerOptions().setPeerRoles(EnumSet.of(Peer.PeerRole.ENDORSING_PEER, Peer.PeerRole.LEDGER_QUERY, Peer.PeerRole.CHAINCODE_QUERY, Peer.PeerRole.EVENT_SOURCE)));

            System.out.println("Peer " + peerName + "joined channel " + name);
        }

        //将剩下的orderer加入到channel中
        for (Orderer orderer : orderers){
            newChannel.addOrderer(orderer);
        }

        return newChannel.initialize();
    }

    //设置用户，并注册与登记。
    public void enrollUsers(Collection<TestOrg> testOrgs) throws Exception {
        System.out.println("---------------------Enrolling Users begin---------------------");
        //为每一个组织下的用户注册并登记。
        for (TestOrg testOrg : testOrgs) {

            HFCAClient ca = testOrg.getHfcaClient();

            final String orgName = testOrg.getName();
            final String mspid = testOrg.getMspid();
            //设置加密工具
            ca.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

            //CA的信息,测试CA是否连接上
            HFCAInfo info = ca.info();
            assertNotNull(info);

            //为admin生成公私钥对
            TestUser admin = new TestUser("admin");
            admin.setEnrollment(ca.enroll(admin.getName(), "adminpw"));
            admin.setMspid(mspid);
            testOrg.setAdmin(admin);

            //为user1生成公私钥对
            TestUser user = new TestUser("user1");
            RegistrationRequest rr = new RegistrationRequest(user.getName(), "org1.department1");
            //由上面声明的admin去执行register请求
            String user1Secret = ca.register(rr, admin);
            user.setEnrollment(ca.enroll(user.getName(), user1Secret));
            user.setMspid(mspid);
            testOrg.addUser(user);

            //创建peerAdmin
            TestUser peerOrgAdmin = new TestUser("peerOrg1Admin");
            peerOrgAdmin.setMspid(testOrg.getMspid());

            //获取fabric生成的证书
            File certificateFile = Paths.get("src\\test\\resources\\crypto-config\\peerOrganizations\\", testOrg.getDomainName(), format("\\users\\Admin@%s\\msp\\signcerts\\Admin@%s-cert.pem", testOrg.getDomainName(), testOrg.getDomainName())).toFile();
            if (!certificateFile.exists()) {
                System.out.println(testOrg.getDomainName() + "'s" + "certificateFile not find");
                return;
            }

            String certificate = new String(IOUtils.toByteArray(new FileInputStream(certificateFile)), "UTF-8");

            //获取fabric生成的私钥
            File baseSK = Paths.get("src\\test\\resources\\crypto-config\\peerOrganizations\\", testOrg.getDomainName(), format("\\users\\Admin@%s\\msp\\keystore", testOrg.getDomainName())).toFile();
            File privateKeyFile = TestUtils.findFileSK(baseSK);
            //转换成PrivateKey类型
            PrivateKey privateKey = TestUtils.getPrivateKeyFromBytes(IOUtils.toByteArray(new FileInputStream(privateKeyFile)));

            //设置公私钥
            peerOrgAdmin.setEnrollment(new TestEnrollment(privateKey, certificate));
            testOrg.setPeerAdmin(peerOrgAdmin);
        }
        System.out.println("---------------------Enrolling Users end---------------------");
    }

}
