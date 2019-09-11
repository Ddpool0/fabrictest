package entity;

import org.hyperledger.fabric.sdk.Enrollment;

import java.security.PrivateKey;

//实现Enrollment接口，自定义公私钥。
public class TestEnrollment implements Enrollment {

    private final PrivateKey privateKey;
    private final String certificate;

    public TestEnrollment(PrivateKey privateKey,String certificate){
        this.certificate=certificate;
        this.privateKey=privateKey;
    }

    @Override
    public PrivateKey getKey() {
        return this.privateKey;
    }

    @Override
    public String getCert() {
        return this.certificate;
    }
}
