package com.ae2subnet.api;

import net.minecraft.util.StringRepresentable;

public enum SubnetDisplayStatus implements StringRepresentable {
    OFFLINE("offline"),
    IDLE("idle"),
    BUSY("busy");

    private final String name;

    SubnetDisplayStatus(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}