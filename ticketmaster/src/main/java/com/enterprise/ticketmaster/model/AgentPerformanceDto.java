package com.enterprise.ticketmaster.model;

public class AgentPerformanceDto {

    private String agent;
    private long totalAssigned;
    private long totalResolved;
    private Double avgResolutionHours;    // null = no resolved tickets with duration data yet, distinct from a genuine 0.0h
    private Double avgFirstResponseHours; // null = no qualifying first-response comment yet, distinct from a genuine 0.0h
    private double slaComplianceRate;

    public AgentPerformanceDto(String agent, long totalAssigned, long totalResolved,
                               Double avgResolutionHours, Double avgFirstResponseHours,
                               double slaComplianceRate) {
        this.agent = agent;
        this.totalAssigned = totalAssigned;
        this.totalResolved = totalResolved;
        this.avgResolutionHours = avgResolutionHours;
        this.avgFirstResponseHours = avgFirstResponseHours;
        this.slaComplianceRate = slaComplianceRate;
    }

    public String getAgent() { return agent; }
    public long getTotalAssigned() { return totalAssigned; }
    public long getTotalResolved() { return totalResolved; }
    public Double getAvgResolutionHours() { return avgResolutionHours; }
    public Double getAvgFirstResponseHours() { return avgFirstResponseHours; }
    public double getSlaComplianceRate() { return slaComplianceRate; }
}