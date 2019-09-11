package entity;

import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.User;

import java.util.Set;

public class TestUser implements User {

    private String name;
    private Set<String> roles;
    private String account;
    private String affiliation;
    private String mspid;
    private Enrollment enrollment = null;

    public TestUser(String name){
        this.name=name;
    }


    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public void setAffiliation(String affiliation) {
        this.affiliation = affiliation;
    }

    public void setMspid(String mspid) {
        this.mspid = mspid;
    }

    public void setEnrollment(Enrollment enrollment) {
        this.enrollment = enrollment;
    }

    public String getName() {
        return this.name;
    }

    public Set<String> getRoles() {
        return this.roles;
    }

    public String getAccount() {
        return this.account;
    }

    public String getAffiliation() {
        return this.affiliation;
    }

    public Enrollment getEnrollment() {
        return this.enrollment;
    }

    public String getMspId() {
        return this.mspid;
    }


}
