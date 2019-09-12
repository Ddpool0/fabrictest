package main;

import config.TestUtils;
import entity.TestEnrollment;
import entity.TestOrg;
import entity.TestUser;
import org.apache.commons.io.IOUtils;
import org.hyperledger.fabric.protos.peer.Chaincode;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.ChaincodeEndorsementPolicyParseException;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
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

        //安装链码，实例化链码，执行链码
        runChannnel(client, fooChannel, true, testOrg, 0);
    }

    /**
     * 安装链码，实例化链码，执行链码
     *
     * @param client
     * @param channel
     * @param installChaincode
     * @param testOrg
     * @param delta
     */
    public void runChannnel(HFClient client, Channel channel, boolean installChaincode, TestOrg testOrg, int delta) throws org.hyperledger.fabric.sdk.exception.InvalidArgumentException, ProposalException, IOException, ChaincodeEndorsementPolicyParseException {

        //Chaincode事件捕获类
        class ChaincodeEventCapture {
            final String handle;
            final BlockEvent blockEvent;
            final ChaincodeEvent chaincodeEvent;

            public ChaincodeEventCapture(String handle, BlockEvent blockEvent, ChaincodeEvent chaincodeEvent) {
                this.handle = handle;
                this.blockEvent = blockEvent;
                this.chaincodeEvent = chaincodeEvent;
            }
        }

        Vector<ChaincodeEventCapture> chaincodeEvents = new Vector<>();

        //获取channel的name
        final String channelName = channel.getName();
        boolean isFooChain = "foo".equals(channelName);
        System.out.println("Running channel " + channelName);

        //获取channel的Orderer节点实例集合
        Collection<Orderer> orderers = channel.getOrderers();
        //链码声明
        final ChaincodeID chaincodeID;
        //响应集合
        Collection<ProposalResponse> responses;
        //成功响应集合
        Collection<ProposalResponse> successful = new LinkedList<>();
        //失败响应集合
        Collection<ProposalResponse> failed = new LinkedList<>();

        //不明，事件监听注册
        String chaincodeEventListenerHandle = channel.registerChaincodeEventListener(Pattern.compile(".*"),
                Pattern.compile(Pattern.quote("event")),
                (handle, blockEvent, chaincodeEvent) -> chaincodeEvents.add(new ChaincodeEventCapture(handle, blockEvent, chaincodeEvent))
        );

        //不明
        if (!isFooChain) {
            channel.unregisterChaincodeEventListener(chaincodeEventListenerHandle);
            chaincodeEventListenerHandle = null;
        }

        //设置链码的name、version、path
        ChaincodeID.Builder chaincodeIDBuilder = ChaincodeID.newBuilder().setName("example_cc_go").setVersion("1").setPath("github.com/example_cc");

        //实例化chaincodeID
        chaincodeID = chaincodeIDBuilder.build();

        //安装链码
        if (installChaincode) {

            //设置客户端用户角色
            client.setUserContext(testOrg.getPeerAdmin());

            System.out.println("Creating install proposal");
            //安装链码提议请求
            InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
            installProposalRequest.setChaincodeID(chaincodeID);

            installProposalRequest.setChaincodeSourceLocation(Paths.get("src\\test\\resources", "\\chaincode\\sample1").toFile());
            //不明，设置couchdb的meta信息
            installProposalRequest.setChaincodeMetaInfLocation(new File("src\\test\\resources\\meta-infs\\end2endit"));

            installProposalRequest.setChaincodeVersion("1");
            installProposalRequest.setChaincodeLanguage(TransactionRequest.Type.GO_LANG);

            System.out.println("Sending install proposal");

            int numInstallProposal = 0;
            Collection<Peer> peers = channel.getPeers();
            numInstallProposal = numInstallProposal + peers.size();
            //发送安装请求,安装到channel上的所有peer
            responses = client.sendInstallProposal(installProposalRequest, peers);

            for (ProposalResponse response : responses) {
                if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    System.out.println("Successful install proposal response Txid: " + response.getTransactionID() + " from peer " + response.getPeer().getName());
                    successful.add(response);
                } else {
                    failed.add(response);
                }
            }

            System.out.println("Received " + numInstallProposal + " install proposal responses. Successful+verified: " + successful.size() + " . Failed: " + failed.size());
        }

        System.out.println("Instantiate chaincode");
        //实例化链码提议请求
        InstantiateProposalRequest instantiateProposalReques = client.newInstantiationProposalRequest();
        //提议等待时间
        instantiateProposalReques.setProposalWaitTime(120000);
        instantiateProposalReques.setChaincodeID(chaincodeID);
        instantiateProposalReques.setChaincodeLanguage(TransactionRequest.Type.GO_LANG);
        instantiateProposalReques.setFcn("init");
        instantiateProposalReques.setArgs(new String[]{"a", "500", "b", "" + (200 + delta)});

        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
        tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
        instantiateProposalReques.setTransientMap(tm);

        //背书策略
        ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
        //配置背书策略
        chaincodeEndorsementPolicy.fromYamlFile(new File("src\\test\\resources\\chaincodeendorsementpolicy.yaml"));
        instantiateProposalReques.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);

        System.out.println("Sending instantiateProposalRequest to all peers with arguments: a and b set to 100 and " + (200 + delta) + " respectively");
        successful.clear();
        failed.clear();

        responses = channel.sendInstantiationProposal(instantiateProposalReques, channel.getPeers());

        for (ProposalResponse response : responses) {
            if (response.isVerified()&&response.getStatus()==ProposalResponse.Status.SUCCESS){
                successful.add(response);
                System.out.println("Succesful instantiate proposal response Txid: "+response.getTransactionID()+" from peer "+response.getPeer().getName());
            }else {
                failed.add(response);
            }
        }

        System.out.println("Received " + responses.size() + " instantiate proposal responses. Successful+verified: " + successful.size() + " . Failed: " + failed.size());
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

            System.out.println("Peer " + peerName + " joined channel " + name);
        }

        //将剩下的orderer加入到channel中
        for (Orderer orderer : orderers) {
            newChannel.addOrderer(orderer);
        }

        return newChannel.initialize();
    }

    /**
     * 设置用户，并注册与登记。
     *
     * @param testOrgs
     * @throws Exception
     */
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
