package main;

import config.TestUtils;
import entity.TestEnrollment;
import entity.TestOrg;
import entity.TestUser;
import org.apache.commons.io.IOUtils;
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
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.Collection;

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
            File baseSK=Paths.get("src\\test\\resources\\crypto-config\\peerOrganizations\\",testOrg.getDomainName(),format("\\users\\Admin@%s\\msp\\keystore",testOrg.getDomainName())).toFile();
            File privateKeyFile=TestUtils.findFileSK(baseSK);
            //转换成PrivateKey类型
            PrivateKey privateKey=TestUtils.getPrivateKeyFromBytes(IOUtils.toByteArray(new FileInputStream(privateKeyFile)));

            //设置公私钥
            peerOrgAdmin.setEnrollment(new TestEnrollment(privateKey,certificate));
            testOrg.setPeerAdmin(peerOrgAdmin);
        }
    }


}
