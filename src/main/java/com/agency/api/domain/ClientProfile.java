package com.agency.api.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="client_profiles")
public class ClientProfile {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @OneToOne(optional=false) @JoinColumn(nullable=false,unique=true) private Account account;
    @Column(nullable=false) private String phone;
    @Column(nullable=false) private String address;
    protected ClientProfile() {}
    public ClientProfile(Account account,String phone,String address){this.account=account;this.phone=phone;this.address=address;}
    public UUID getId(){return id;} public Account getAccount(){return account;} public String getPhone(){return phone;} public String getAddress(){return address;}
    public void update(String phone,String address){this.phone=phone;this.address=address;}
}
