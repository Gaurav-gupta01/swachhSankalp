package CleanVision.domain;

import java.util.List;

import lombok.Getter;

@Getter
public enum ReportStatus {

    SUBMITTED(1, "Submitted"),
    AI_ANALYZED(2, "AI Analyzed"),
    VERIFIED(3, "Verified"),
    ASSIGNED(4, "Assigned"),
    IN_PROGRESS(5, "In Progress"),
    COLLECTED(6, "Collected"),
    RESOLVED(7, "Resolved"),
    CLOSED(8, "Closed");

    private final int order;
    private final String label;

    ReportStatus(int order, String label) {
        this.order = order;
        this.label = label;
    }

    public static List<ReportStatus> workflow() {
        return List.of(values());
    }

    public ReportStatus next() {
        ReportStatus[] all = values();
        return this.order >= all.length ? this : all[this.order];
    }
}
