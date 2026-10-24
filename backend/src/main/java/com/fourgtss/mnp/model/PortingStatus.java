package com.fourgtss.mnp.model;

/** Lifecycle of a porting request; see docs/diagrams/state-diagram.png. */
public enum PortingStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELED
}
