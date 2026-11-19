package com.agency.api.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="contract_ratings", uniqueConstraints=@UniqueConstraint(name="uk_contract_rating_author",columnNames={"contract_id","author_id"}), indexes=@Index(name="idx_contract_rating_contract",columnList="contract_id,created_at"))
public class ContractRating {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="contract_id",nullable=false) private EmploymentContract contract;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="author_id",nullable=false) private Account author;
    @Column(nullable=false) private int score;
    @Column(length=1000) private String comment;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    protected ContractRating(){}
    public ContractRating(EmploymentContract contract,Account author,int score,String comment){this.contract=contract;this.author=author;this.score=score;this.comment=comment;}
    public UUID getId(){return id;} public EmploymentContract getContract(){return contract;} public Account getAuthor(){return author;} public int getScore(){return score;} public String getComment(){return comment;} public Instant getCreatedAt(){return createdAt;}
}
