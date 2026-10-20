package com.crowdcheck;

/** Plain data classes (records) for users, locations, crowd updates and alerts. */
public final class Models {
    private Models() {}

    public record User(int userId, String userName, String email, String contactInfo, String userRole) {}

    public record Location(int locationId, String locationName, String locationType, int capacity,
                           int currentCrowdCount, String crowdLevel, String lastUpdated, int percent) {}

    public record CrowdUpdate(int updateId, int locationId, String locationName, int crowdCount, int capacity,
                              int percent, String crowdLevel, String updatedBy, String updateTime) {}

    public record Alert(int alertId, int locationId, String locationName, String alertMessage,
                        String alertTime, String crowdLevel, String status) {}
}
