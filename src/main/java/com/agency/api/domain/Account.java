package com.agency.api.domain;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="accounts", uniqueConstraints=@UniqueConstraint(columnNames="email"))
public class Account {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false) private String name;
    @Column(nullable=false) private String email;
    @Column(nullable=false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Role role;
    @Column(name="must_change_password",nullable=false,columnDefinition="boolean default false") private boolean mustChangePassword=false;
    @Column(name="profile_photo",columnDefinition="bytea") private byte[] profilePhoto;
    @Column(name="profile_photo_content_type",length=40) private String profilePhotoContentType;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    protected Account() {}
    public Account(String name,String email,String passwordHash,Role role){this.name=name;this.email=email.toLowerCase().trim();this.passwordHash=passwordHash;this.role=role;}
    public Account(String name,String email,String passwordHash,Role role,boolean mustChangePassword){this(name,email,passwordHash,role);this.mustChangePassword=mustChangePassword;}
    public void changeEmail(String email){this.email=email.toLowerCase().trim();}
    public UUID getId(){return id;} public String getName(){return name;} public String getEmail(){return email;} @JsonIgnore public String getPasswordHash(){return passwordHash;} public Role getRole(){return role;} public Instant getCreatedAt(){return createdAt;} public boolean isMustChangePassword(){return mustChangePassword;} @JsonIgnore public byte[] getProfilePhoto(){return profilePhoto;} public String getProfilePhotoContentType(){return profilePhotoContentType;} public void setProfilePhoto(byte[] photo,String contentType){profilePhoto=photo;profilePhotoContentType=contentType;} public void changePassword(String passwordHash){this.passwordHash=passwordHash;mustChangePassword=false;} public void setTemporaryPassword(String passwordHash){this.passwordHash=passwordHash;mustChangePassword=true;} public void markAsHousekeeper(){if(role==Role.CANDIDATE)role=Role.HOUSEKEEPER;}
}
