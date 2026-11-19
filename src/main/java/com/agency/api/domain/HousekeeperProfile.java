package com.agency.api.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "housekeeper_profiles")
public class HousekeeperProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @OneToOne(optional = false)
    @JoinColumn(nullable = false, unique = true)
    private Account account;
    @Column(nullable = false)
    private LocalDate birthDate;
    @Column(nullable = false)
    private String phone;
    @Column(nullable = false)
    private String city;
    @Column(length = 100)
    private String district;
    @Column(nullable = false)
    private String neighborhood;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "housekeeper_skills", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "skill")
    private Set<String> skills = new HashSet<>();
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "housekeeper_availability", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "weekday")
    private Set<String> availability = new HashSet<>();
    @Column(nullable = false)
    private int expectedSalary;
    @Column(nullable = false)
    private int yearsOfExperience;
    @Column(nullable = false)
    private String status = "PENDING";
    private String rejectionReason;

    protected HousekeeperProfile() {
    }

    public HousekeeperProfile(Account a, LocalDate birth, String phone, String city, String neighborhood, Set<String> skills, Set<String> days, int salary, int years) {
        this(a, birth, phone, city, null, neighborhood, skills, days, salary, years);
    }

    public HousekeeperProfile(Account a, LocalDate birth, String phone, String city, String district, String neighborhood, Set<String> skills, Set<String> days, int salary, int years) {
        account = a;
        birthDate = birth;
        this.phone = phone;
        this.city = city;
        this.district = district;
        this.neighborhood = neighborhood;
        this.skills = skills;
        availability = days;
        expectedSalary = salary;
        yearsOfExperience = years;
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public String getName() {
        return account.getName();
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getPhone() {
        return phone;
    }

    public String getCity() {
        return getProvince();
    }

    public String getProvince() {
        return switch (city == null ? "" : city.trim().toLowerCase(Locale.ROOT)) {
            case "maputo" -> "Cidade de Maputo";
            case "matola" -> "Província de Maputo";
            case "beira" -> "Sofala";
            case "xai-xai" -> "Gaza";
            default -> city;
        };
    }

    public String getDistrict() {
        if (district != null && !district.isBlank()) return district;
        return switch (city == null ? "" : city.trim().toLowerCase(Locale.ROOT)) {
            case "maputo" -> switch (neighborhood == null ? "" : neighborhood.trim().toLowerCase(Locale.ROOT)) {
                case "maxaquene", "maxakene" -> "KaMaxaqueni";
                case "costa do sol" -> "KaMavota";
                default -> "KaMpfumu";
            };
            case "matola" -> "Matola";
            case "beira" -> "Cidade Da Beira";
            case "nampula" -> "Cidade De Nampula";
            case "xai-xai" -> "Cidade De Xai-Xai";
            case "inhambane" -> "Cidade De Inhambane";
            default -> "Distrito não informado";
        };
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public Set<String> getSkills() {
        return skills;
    }

    public Set<String> getAvailability() {
        return availability;
    }

    public int getExpectedSalary() {
        return expectedSalary;
    }

    public int getYearsOfExperience() {
        return yearsOfExperience;
    }

    public String getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void decide(String status, String reason) {
        this.status = status;
        rejectionReason = reason;
    }

    public void updateProfile(LocalDate birthDate,String phone,String city,String district,String neighborhood,Set<String> skills,Set<String> days,int expectedSalary,int years){this.birthDate=birthDate;this.phone=phone;this.city=city;this.district=district;this.neighborhood=neighborhood;this.skills=new HashSet<>(skills);this.availability=new HashSet<>(days);this.expectedSalary=expectedSalary;this.yearsOfExperience=years;this.status="PENDING";this.rejectionReason=null;}
}
