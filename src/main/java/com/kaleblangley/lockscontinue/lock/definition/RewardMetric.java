package com.kaleblangley.lockscontinue.lock.definition;

/** Server-owned value used to grade one completed lock attempt. */
public enum RewardMetric {
    MISTAKES_USED("mistakes_used"),
    TIME_TICKS("time_ticks");

    private final String id;

    RewardMetric(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static RewardMetric fromId(String id) {
        for (RewardMetric metric : values()) {
            if (metric.id.equals(id)) {
                return metric;
            }
        }
        throw new IllegalArgumentException("unknown reward metric " + id);
    }
}
