package entity;

import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import java.util.*;

public class TestOrg {
    //组织名
    final String name;
    //组织的mspid
    final String mspid;
    //域名
    private String domainName;
    //组织的管理员
    private TestUser admin;
    //节点的管理员
    private TestUser peerAdmin;
    //组织的CA
    HFCAClient hfcaClient;
    //组织CA的name
    private String caName;
    //ca的地址
    private String caLocation;
    //ca的相关配置
    private Properties caProperties = null;
    //组织下的用户
    Map<String, User> userMap = new HashMap<String, User>();
    //组织下的peer的地址
    Map<String, String> peerLocations = new HashMap<String, String>();
    //组织下的orderer的地址
    Map<String, String> ordererLocations = new HashMap<String, String>();


    public TestOrg(String name, String mspid) {
        this.name = name;
        this.mspid = mspid;
    }

    public String getName() {
        return name;
    }

    public String getMspid() {
        return mspid;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public TestUser getAdmin() {
        return admin;
    }

    public void setAdmin(TestUser admin) {
        this.admin = admin;
    }

    public TestUser getPeerAdmin() {
        return peerAdmin;
    }

    public void setPeerAdmin(TestUser peerAdmin) {
        this.peerAdmin = peerAdmin;
    }

    public HFCAClient getHfcaClient() {
        return hfcaClient;
    }

    public void setHfcaClient(HFCAClient hfcaClient) {
        this.hfcaClient = hfcaClient;
    }

    public String getCaName() {
        return caName;
    }

    public void setCaName(String caName) {
        this.caName = caName;
    }

    public String getCaLocation() {
        return caLocation;
    }

    public void setCaLocation(String caLocation) {
        this.caLocation = caLocation;
    }

    public Properties getCaProperties() {
        return caProperties;
    }

    public void setCaProperties(Properties caProperties) {
        this.caProperties = caProperties;
    }

    //获取所有的orderer的地址
    public Collection<String> getOrdererLocations(){
        return Collections.unmodifiableCollection(ordererLocations.values());
    }

    //获取所有orderer的name
    public Set<String> getOrdererNames(){
        return Collections.unmodifiableSet(ordererLocations.keySet());
    }

    //获取指定name的orderer的地址
    public String getOrdererLocation(String name){
        return ordererLocations.get(name);
    }

    //存储orderer的name和location
    public void addOrdererLocation(String name,String location){
        ordererLocations.put(name,location);
    }

    //获取所有的peer的地址
//    public Collection<String> getPeerLocations(){
//        return Collections.unmodifiableCollection(peerLocations.values());
//    }

    //获取所有Peer的name
    public Set<String> getPeerNames(){
        return Collections.unmodifiableSet(peerLocations.keySet());
    }

    //获取指定name的peer的地址
    public String getPeerLocation(String name){
        return peerLocations.get(name);
    }

    //存储peer的name和location
    public void addPeerLocation(String name,String location){
        peerLocations.put(name,location);
    }

    //添加用户
    public void addUser(TestUser user){
        userMap.put(user.getName(),user);
    }

    //获取指定name的用户信息。
    public User getUser(String name){
        return userMap.get(name);
    }

    @Override
    public String toString() {
        return "TestOrg{" +
                "name='" + name + '\'' +
                ", mspid='" + mspid + '\'' +
                ", domainName='" + domainName + '\'' +
                ", admin=" + admin +
                ", peerAdmin=" + peerAdmin +
                ", hfcaClient=" + hfcaClient +
                ", caName='" + caName + '\'' +
                ", caLocation='" + caLocation + '\'' +
                ", caProperties=" + caProperties +
                ", userMap=" + userMap +
                ", peerLocations=" + peerLocations +
                ", ordererLocations=" + ordererLocations +
                '}';
    }
}
