package com.agency.api.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity @Table(name="hire_requests")
public class HireRequest {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false) private Account client;
    @ManyToOne(optional=false) private HousekeeperProfile housekeeper;
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="request_skills",joinColumns=@JoinColumn(name="request_id")) @Column(name="skill") private Set<String> requiredSkills=new HashSet<>();
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="request_days",joinColumns=@JoinColumn(name="request_id")) @Column(name="weekday") private Set<String> requiredDays=new HashSet<>();
    @ManyToMany(fetch=FetchType.EAGER) @JoinTable(name="hire_request_tasks",joinColumns=@JoinColumn(name="request_id"),inverseJoinColumns=@JoinColumn(name="task_id")) private Set<AgencyTask> selectedTasks=new HashSet<>();
    @Column(nullable=false) private LocalDate startDate;
    @Column(length=100) private String district;
    @Column(length=100) private String neighborhood;
    @Column(length=8) private String startTime;
    @Column(length=8) private String endTime;
    @Column(columnDefinition="text") private String taskScope;
    private int monthlySalary;
    private int durationMonths;
    private int hoursPerDay;
    @Column(length=300) private String notes;
    @Column(nullable=false) private String status="PENDING";
    private String rejectionReason;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @Column(nullable=false) private Instant expiresAt=Instant.now().plus(Duration.ofDays(14));
    @Version private long version;
    protected HireRequest() {}
    public HireRequest(Account client,HousekeeperProfile housekeeper,Set<String> skills,Set<String> days,LocalDate startDate,String notes,String taskScope,int monthlySalary,int durationMonths,int hoursPerDay){this.client=client;this.housekeeper=housekeeper;requiredSkills=skills;requiredDays=days;this.startDate=startDate;this.notes=notes;this.taskScope=taskScope;this.monthlySalary=monthlySalary;this.durationMonths=durationMonths;this.hoursPerDay=hoursPerDay;}
    public void setWorkLocationAndSchedule(String district,String neighborhood,String startTime,String endTime){this.district=district;this.neighborhood=neighborhood;this.startTime=startTime;this.endTime=endTime;}
    public UUID getId(){return id;} public Account getClient(){return client;} public HousekeeperProfile getHousekeeper(){return housekeeper;} public Set<String> getRequiredSkills(){return requiredSkills;} public Set<String> getRequiredDays(){return requiredDays;} public Set<AgencyTask> getSelectedTasks(){return selectedTasks;} public void setSelectedTasks(Set<AgencyTask> tasks){selectedTasks=new HashSet<>(tasks);} public String getSelectedTaskNames(){return selectedTasks.stream().map(AgencyTask::getName).sorted().reduce((a,b)->a+", "+b).orElse("");} public LocalDate getStartDate(){return startDate;} public String getDistrict(){return district;} public String getNeighborhood(){return neighborhood;} public String getStartTime(){return startTime;} public String getEndTime(){return endTime;} public String getNotes(){return notes;} public String getTaskScope(){return taskScope;} public int getMonthlySalary(){return monthlySalary;} public int getDurationMonths(){return durationMonths;} public int getHoursPerDay(){return hoursPerDay;} public String getStatus(){return status;} public String getRejectionReason(){return rejectionReason;} public Instant getCreatedAt(){return createdAt;} public Instant getExpiresAt(){return expiresAt;} public long getVersion(){return version;} public void setMonthlySalary(int salary){this.monthlySalary=salary;} public void setStatus(String status){this.status=status;} public void setExpiration(Instant date){expiresAt=date;} public void decide(String status,String reason){this.status=status;rejectionReason=reason;} public void expireIfDue(Instant now){if(expiresAt.isBefore(now)&&Set.of("PENDING","APPROVED","EM_NEGOCIACAO").contains(status))status="EXPIRED";}
}
