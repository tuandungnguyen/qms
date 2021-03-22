package com.ntd.qms.data;

public class OrderAndRoomItem {
    String order;
    int direction;
    int room;


    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public int getRoom() {
        return room;
    }

    public void setRoom(int room) {
        this.room = room;
    }

    public OrderAndRoomItem(String order, int direction, int room) {
        this.order = order;
        this.direction = direction;
        this.room = room;
    }
}
